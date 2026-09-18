-- Least-privilege grants. Run after schema scripts.

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX
    ON opsconsole.* TO 'opsconsole'@'%';

GRANT SELECT
    ON bre_underwriting.transaction_details TO 'opsconsole_txn'@'%';
GRANT SELECT
    ON bre_underwriting.transaction_details_srcreq_otp TO 'opsconsole_txn'@'%';
GRANT SELECT
    ON bre_underwriting.festival_summary_detailed_mins_new TO 'opsconsole_txn'@'%';
GRANT SELECT
    ON bre_underwriting.festival_summary_detailed_new TO 'opsconsole_txn'@'%';

FLUSH PRIVILEGES;
