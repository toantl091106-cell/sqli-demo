#!/usr/bin/env python3
"""Exercise the repaired SQLi demo against a disposable Docker test database.

Run only after the test Compose stack is running:
    python verification/verify_demo.py --allow-demo-reset

The sole accepted origin is http://127.0.0.1:18080. This script intentionally
resets sample data several times and refuses to send any requests without the
explicit reset flag. It never connects to the user's regular localhost:8080.
Only Python's standard library is required.
"""

import argparse
import html
import http.cookiejar
import json
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request


TEST_ORIGIN = "http://127.0.0.1:18080"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Seeing the original redirect matters for access-control assertions.
        return None


class Client:
    def __init__(self, origin):
        self.origin = origin
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            NoRedirect(), urllib.request.HTTPCookieProcessor(self.cookies)
        )

    def request(self, method, path, payload=None):
        if not path.startswith("/") or path.startswith("//"):
            raise ValueError("Only application-relative paths are allowed")
        headers = {"User-Agent": "SQLiDemo-Isolated-Regression/1.0"}
        body = None
        if payload is not None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(
            self.origin + path, data=body, headers=headers, method=method
        )
        try:
            response = self.opener.open(req, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return (
                response.code,
                dict(response.headers.items()),
                response.read().decode("utf-8", errors="replace"),
            )

    def json(self, method, path, payload=None):
        status, headers, body = self.request(method, path, payload)
        try:
            parsed = json.loads(body)
        except json.JSONDecodeError as error:
            raise AssertionError(
                f"{method} {path}: expected JSON, got HTTP {status}: {body[:300]}"
            ) from error
        return status, headers, parsed

    def execute(self, scenario, secure, input1="", input2="", input3=""):
        return self.json(
            "POST",
            "/api/execute",
            {
                "scenario": str(scenario),
                "secure": "true" if secure else "false",
                "input1": input1,
                "input2": input2,
                "input3": input3,
            },
        )[2]

    def session_id(self):
        return next((cookie.value for cookie in self.cookies
                     if cookie.name == "SQLI_DEMO_DOCKER"), None)


class Verification:
    def __init__(self, origin):
        self.origin = origin
        self.client = Client(origin)
        self.assertions = 0
        self.failures = []

    def check(self, condition, description):
        if not condition:
            raise AssertionError(description)
        self.assertions += 1

    def expect(self, response, status, outcome, affected=None):
        self.check(response.get("status") == status,
                   f"expected status {status!r}; response={response}")
        self.check(response.get("outcome") == outcome,
                   f"expected outcome {outcome!r}; response={response}")
        if affected is not None:
            self.check(response.get("affectedRows") == affected,
                       f"expected affectedRows={affected}; response={response}")

    def reset(self):
        code, _, response = self.client.json("POST", "/api/reset", {})
        self.check(code == 200 and response.get("status") == "success",
                   f"isolated sample reset failed: HTTP {code}, {response}")
        data = self.data()
        self.check(len(data["users"]) >= 2 and len(data["posts"]) >= 2,
                   "reset did not provide the expected sample data")
        return data

    def data(self):
        code, _, data = self.client.json("GET", "/api/data")
        self.check(code == 200 and isinstance(data.get("users"), list)
                   and isinstance(data.get("posts"), list),
                   f"live database snapshot is invalid: HTTP {code}, {data}")
        self.public(data["users"], "public database snapshot")
        return data

    def public(self, value, description):
        def contains_password(item):
            if isinstance(item, dict):
                return any(str(key).lower() == "password"
                           or contains_password(child)
                           for key, child in item.items())
            if isinstance(item, list):
                return any(contains_password(child) for child in item)
            return False
        self.check(not contains_password(value),
                   f"{description} exposes a password field")

    def session(self, client, authenticated, username=None):
        code, _, response = client.json("GET", "/api/session")
        self.check(code == 200 and response.get("authenticated") is authenticated,
                   f"session should have authenticated={authenticated}: {response}")
        self.public(response, "session response")
        if username is not None:
            self.check(response.get("user", {}).get("username") == username,
                       f"wrong session identity: {response}")
        elif not authenticated:
            self.check(not response.get("user"),
                       f"unauthenticated session retained a user: {response}")
        return response

    def protected(self, client, authenticated, admin=False):
        for path in ("/api/account", "/api/admin/users"):
            code, _, response = client.json("GET", path)
            expected = 200 if authenticated and (path == "/api/account" or admin) \
                else 403 if authenticated else 401
            self.check(code == expected,
                       f"{path}: expected HTTP {expected}, got {code}: {response}")
            self.public(response, path)
        for path in ("/account", "/admin"):
            code, headers, _ = client.request("GET", path)
            if not authenticated:
                self.check(code in (301, 302, 303, 307, 308, 401),
                           f"{path} served an unauthenticated protected page: HTTP {code}")
                if code != 401:
                    location = headers.get("Location", headers.get("location", ""))
                    self.check(bool(location) and "/admin" not in location
                               and "/account" not in location,
                               f"{path} has an invalid authentication redirect: {location}")
            else:
                expected = 200 if path == "/account" or admin else 403
                self.check(code == expected,
                           f"{path}: expected HTTP {expected}, got {code}")

    @staticmethod
    def by_id(rows, row_id):
        return next((row for row in rows if int(row["id"]) == row_id), None)

    @staticmethod
    def by_username(rows, username):
        return next((row for row in rows if row["username"] == username), None)

    def session_lifecycle_and_html(self):
        self.reset()
        actor = Client(self.origin)
        response = actor.execute(1, True, "admin", "admin123")
        self.expect(response, "success", "authenticated")
        first_id = actor.session_id()
        self.check(bool(first_id), "successful login did not create a session cookie")
        response = actor.execute(1, True, "admin", "admin123")
        self.expect(response, "success", "authenticated")
        self.check(bool(actor.session_id()) and actor.session_id() != first_id,
                   "successful re-login reused the old session identifier")
        self.session(actor, True, "admin")

        # Reset through another browser/client must revoke the original session.
        other_browser = Client(self.origin)
        code, _, response = other_browser.json("POST", "/api/reset", {})
        self.check(code == 200 and response.get("status") == "success",
                   f"separate-client reset failed: HTTP {code}, {response}")
        self.session(actor, False)
        self.protected(actor, False)

        response = actor.execute(1, True, "user1", "pass123")
        self.expect(response, "success", "authenticated")
        code, _, body = actor.request("GET", "/admin")
        self.check(code == 403, f"ordinary user's admin page returned HTTP {code}")
        self.check("<table" not in body.lower() and "john_doe" not in body
                   and "admin@gmail.com" not in body,
                   "forbidden admin HTML contains the user list")

        # A harmless tag-shaped text value must remain text in the HTML table.
        # It is inserted through the ordinary parameterized endpoint.
        text_value = "<b>regression_plain_text</b>"
        response = self.client.execute(3, True, text_value, "test-password", "html@example.test")
        self.expect(response, "success", "inserted", 1)
        stored = self.by_username(self.data()["users"], text_value)
        self.check(stored is not None and stored["role"] == "user",
                   "prepared insert did not preserve the literal text value")
        response = actor.execute(1, True, "admin", "admin123")
        self.expect(response, "success", "authenticated")
        code, _, body = actor.request("GET", "/admin")
        self.check(code == 200 and html.escape(text_value) in body,
                   "admin HTML did not render the escaped text value")
        self.check(text_value not in body,
                   "admin HTML rendered stored tag-shaped text as active markup")
        self.reset()

    def authentication(self):
        self.reset()
        actor = Client(self.origin)
        self.session(actor, False)
        self.protected(actor, False)
        for secure in (False, True):
            response = actor.execute(1, secure, "admin", "admin123")
            self.expect(response, "success", "authenticated")
            self.check(response.get("authenticated") is True
                       and response.get("bypassDetected") is False,
                       f"ordinary valid login was mislabeled as a bypass: {response}")
            self.check(response.get("user", {}).get("username") == "admin",
                       f"login omitted the public identity: {response}")
            self.public(response, "login response")
            self.session(actor, True, "admin")
            self.protected(actor, True, admin=True)

            # A failed second attempt must invalidate the earlier admin session.
            response = actor.execute(1, secure, "admin", "incorrect-password")
            self.expect(response, "rejected", "authentication_failed")
            self.check(response.get("authenticated") is False,
                       f"failed login claims authentication: {response}")
            self.session(actor, False)
            self.protected(actor, False)

        response = actor.execute(1, True, "admin' #", "wrong-password")
        self.expect(response, "rejected", "authentication_failed")
        self.session(actor, False)
        self.protected(actor, False)

        response = actor.execute(1, False, "admin' #", "wrong-password")
        self.expect(response, "success", "authenticated")
        self.check(response.get("authenticated") is True
                   and response.get("bypassDetected") is True,
                   f"vulnerable bypass was not established from the actual result: {response}")
        self.session(actor, True, "admin")
        self.protected(actor, True, admin=True)
        code, _, response = actor.json("POST", "/api/logout", {})
        self.check(code == 200, f"logout failed: HTTP {code}, {response}")
        self.session(actor, False)
        self.protected(actor, False)

        # Explicitly demonstrate server-side role enforcement for a real user.
        response = actor.execute(1, True, "user1", "pass123")
        self.expect(response, "success", "authenticated")
        self.session(actor, True, "user1")
        self.protected(actor, True, admin=False)
        actor.json("POST", "/api/logout", {})
        self.session(actor, False)

    def search(self):
        baseline = self.reset()
        for secure in (False, True):
            response = self.client.execute(2, secure, "Spring")
            self.expect(response, "success", "results_found")
            rows = response.get("rows")
            self.check(isinstance(rows, list) and len(rows) > 0
                       and response.get("rowCount") == len(rows),
                       f"ordinary search result/count is incorrect: {response}")
            message = "".join(c for c in unicodedata.normalize("NFD", response.get("message", ""))
                              if unicodedata.category(c) != "Mn").lower()
            self.check(not any(term in message for term in
                               ("danh cap", "tan cong thanh cong", "ngan chan", "exfiltrat",
                                "attack succeeded", "attack successful", "blocked", "prevented")),
                       f"ordinary search falsely claims an attack/block: {response}")
            response = self.client.execute(2, secure, "no_such_title_749326")
            self.expect(response, "success", "no_results")
            self.check(response.get("rows") == [] and response.get("rowCount") == 0,
                       f"empty search fabricated results: {response}")

        payload = "no_such_title_749326%' UNION SELECT id, username, password FROM users #"
        response = self.client.execute(2, False, payload)
        self.expect(response, "success", "results_found")
        self.check(any(row.get("title") == "admin" and row.get("content") == "admin123"
                       for row in response.get("rows", [])),
                   f"vulnerable UNION did not demonstrate actual sample credential exposure: {response}")
        response = self.client.execute(2, True, payload)
        self.expect(response, "success", "no_results")
        self.check(response.get("rows") == [] and response.get("rowCount") == 0,
                   f"parameterized UNION input returned injected rows: {response}")
        self.check(self.data() == baseline, "read-only search changed stored data")

    def credential_collation(self):
        self.reset()
        actor = Client(self.origin)
        for secure in (False, True):
            response = actor.execute(1, secure, "ADMIN", "admin123")
            self.expect(response, "success", "authenticated")
            self.check(response.get("bypassDetected") is False,
                       "ordinary case-insensitive database match was labeled a bypass")
            self.session(actor, True, "admin")
            code, headers, _ = actor.request("GET", "/account")
            self.check(code == 200 and headers.get("Cache-Control") == "no-store",
                       "protected page must disable caching")
            actor.json("POST", "/api/logout", {})

    def insert(self):
        self.reset()
        for secure in (False, True):
            name = "regression_normal_" + str(secure).lower()
            response = self.client.execute(3, secure, name, "test-password", "normal@example.test")
            self.expect(response, "success", "inserted", 1)
            user = self.by_username(self.data()["users"], name)
            self.check(user is not None and user["email"] == "normal@example.test"
                       and user["role"] == "user", f"normal insertion is incorrect: {user}")

        payload = "payload@example.test', 'admin') #"
        response = self.client.execute(3, False, "regression_insert_vulnerable", "test-password", payload)
        self.expect(response, "success", "inserted", 1)
        user = self.by_username(self.data()["users"], "regression_insert_vulnerable")
        self.check(user is not None and user["email"] == "payload@example.test"
                   and user["role"] == "admin", f"vulnerable INSERT did not change the role: {user}")
        response = self.client.execute(3, True, "regression_insert_secure", "test-password", payload)
        self.expect(response, "success", "inserted", 1)
        user = self.by_username(self.data()["users"], "regression_insert_secure")
        self.check(user is not None and user["email"] == payload and user["role"] == "user",
                   f"secure INSERT failed to preserve literal input and fixed role: {user}")
        self.reset()

    def update(self):
        self.reset()
        for secure in (False, True):
            before = self.by_id(self.data()["users"], 2)
            email = "normal-" + str(secure).lower() + "@example.test"
            response = self.client.execute(4, secure, email)
            self.expect(response, "success", "updated", 1)
            after = self.by_id(self.data()["users"], 2)
            self.check(after["email"] == email and after["role"] == before["role"],
                       f"ordinary email update unexpectedly changed the role: {after}")
            unchanged = self.data()
            response = self.client.execute(4, secure, email)
            self.expect(response, "success", "no_change", 0)
            self.check(self.data() == unchanged,
                       "repeating the current email changed stored data")

        payload = "promoted@example.test', role='admin' WHERE id=2 #"
        response = self.client.execute(4, True, payload)
        self.expect(response, "success", "updated", 1)
        after = self.by_id(self.data()["users"], 2)
        self.check(after["email"] == payload and after["role"] == "user",
                   f"secure UPDATE interpreted SQL in the email: {after}")
        response = self.client.execute(4, False, payload)
        self.expect(response, "success", "updated", 1)
        after = self.by_id(self.data()["users"], 2)
        self.check(after["email"] == "promoted@example.test" and after["role"] == "admin",
                   f"vulnerable UPDATE did not demonstrate privilege change: {after}")
        before = self.data()
        response = self.client.execute(4, False, "unused@example.test' WHERE id=999999 #")
        self.expect(response, "success", "no_change", 0)
        self.check(self.data() == before, "zero-row UPDATE changed database contents")
        self.reset()

    def delete(self):
        for secure in (False, True):
            before = self.reset()
            response = self.client.execute(5, secure, "1")
            self.expect(response, "success", "deleted", 1)
            after = self.data()
            expected_posts = [row for row in before["posts"] if int(row["id"]) != 1]
            self.check(after["posts"] == expected_posts and after["users"] == before["users"],
                       "normal post deletion removed additional posts or users")
            response = self.client.execute(5, secure, "999999")
            self.expect(response, "success", "no_change", 0)
            self.check(self.data() == after, "nonexistent-ID DELETE changed database contents")

        before = self.reset()
        for malformed in ("1 OR 1=1", "abc", "", "-1", "0", "1.0",
                          "1; DELETE FROM users", "999999999999999999999999999999"):
            response = self.client.execute(5, True, malformed)
            self.expect(response, "rejected", "invalid_input")
            self.check(self.data() == before,
                       f"rejected secure DELETE changed stored data: {malformed!r}")

        response = self.client.execute(5, False, "1 OR 1=1")
        self.expect(response, "success", "deleted", len(before["posts"]))
        after = self.data()
        self.check(after["posts"] == [] and after["users"] == before["users"],
                   "vulnerable DELETE should affect all posts and preserve every user")
        self.reset()

    def errors(self):
        before = self.reset()
        response = self.client.execute(2, False, "'")
        self.check(response.get("status") == "error", f"SQL syntax error claims success: {response}")
        self.check(self.data() == before, "SQL syntax error changed database contents")
        for scenario in ("unknown", "", "6"):
            response = self.client.execute(scenario, False, "1 OR 1=1")
            self.expect(response, "rejected", "invalid_input")
            self.check(self.data() == before, "unknown scenario changed database contents")

    def run(self):
        for group in (self.authentication, self.session_lifecycle_and_html, self.credential_collation,
                      self.search, self.insert, self.update, self.delete, self.errors):
            try:
                group()
                print(f"PASS {group.__name__}")
            except (AssertionError, urllib.error.URLError, TimeoutError, OSError) as error:
                self.failures.append(f"{group.__name__}: {error}")
                print(f"FAIL {group.__name__}: {error}", file=sys.stderr)
        # This origin/database is disposable, but still leave deterministic data.
        try:
            self.reset()
        except (AssertionError, urllib.error.URLError, TimeoutError, OSError) as error:
            self.failures.append(f"final reset: {error}")
        print(f"\n{self.assertions} assertions passed; {len(self.failures)} groups failed.")
        return 1 if self.failures else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=TEST_ORIGIN)
    parser.add_argument("--allow-demo-reset", action="store_true",
                        help="explicitly authorize destructive resets of the disposable test demo")
    parser.add_argument("--wait-seconds", type=int, default=60,
                        help="seconds to wait for the isolated app/database (default: 60)")
    args = parser.parse_args()
    parsed = urllib.parse.urlsplit(args.base_url)
    if args.base_url.rstrip("/") != TEST_ORIGIN or parsed.username or parsed.password \
            or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        parser.error(f"refusing this origin; only {TEST_ORIGIN} is permitted")
    if not args.allow_demo_reset:
        parser.error("--allow-demo-reset is required; no requests have been sent")
    if not 0 <= args.wait_seconds <= 300:
        parser.error("--wait-seconds must be between 0 and 300")

    verifier = Verification(TEST_ORIGIN)
    deadline = time.monotonic() + args.wait_seconds
    while True:
        try:
            code, _, data = verifier.client.json("GET", "/api/data")
            if code == 200 and isinstance(data.get("users"), list) \
                    and isinstance(data.get("posts"), list):
                break
            raise AssertionError(f"app/database not ready: HTTP {code}, {data}")
        except (AssertionError, urllib.error.URLError, TimeoutError, OSError) as error:
            if time.monotonic() >= deadline:
                print(f"Isolated app is not ready: {error}", file=sys.stderr)
                return 1
            time.sleep(1)
    return verifier.run()


if __name__ == "__main__":
    sys.exit(main())
