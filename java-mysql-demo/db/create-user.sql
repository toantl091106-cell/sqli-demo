-- Run once in MySQL Workbench using an administrative account.
-- These new local demo accounts receive only data permissions on this demo schema.
-- The password below is a sample for this lab account, not your existing MySQL password.
CREATE USER 'sqli_demo_native'@'localhost' IDENTIFIED BY 'local_code_db_2026';
CREATE USER 'sqli_demo_native'@'127.0.0.1' IDENTIFIED BY 'local_code_db_2026';

GRANT SELECT, INSERT, UPDATE, DELETE ON sqli_demo_native.* TO 'sqli_demo_native'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON sqli_demo_native.* TO 'sqli_demo_native'@'127.0.0.1';
