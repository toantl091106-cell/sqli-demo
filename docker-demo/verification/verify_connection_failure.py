"""Probe failure recovery only against the isolated 18080 test stack.

Use 'before', stop the verification db, use 'during', start that db, then use 'after'.
The during phase requests a sample delete/reset which must fail with the database stopped.
"""
import argparse
import json
from pathlib import Path
from verify_demo import Client, TEST_ORIGIN

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("phase", choices=["before", "during", "after"])
parser.add_argument("--allow-demo-reset", action="store_true")
args = parser.parse_args()
if not args.allow_demo_reset:
    parser.error("--allow-demo-reset required; no requests sent")
client = Client(TEST_ORIGIN)
baseline_path = Path(__file__).with_name("connection-baseline.json")
if args.phase == "before":
    code, _, data = client.json("GET", "/api/data")
    assert code == 200 and isinstance(data.get("users"), list) and isinstance(data.get("posts"), list)
    baseline_path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
elif args.phase == "during":
    _, _, data = client.json("GET", "/api/data")
    assert data.get("error") and "users" not in data and "posts" not in data, data
    result = client.execute(5, True, "1")
    assert result.get("status") == "error" and result.get("affectedRows") == 0, result
    _, _, result = client.json("POST", "/api/reset", {})
    assert result.get("status") == "error", result
else:
    _, _, data = client.json("GET", "/api/data")
    assert data == json.loads(baseline_path.read_text(encoding="utf-8")), "Failure changed stored data"
print("PASS database connection " + args.phase)
