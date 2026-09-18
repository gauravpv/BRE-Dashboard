-- Sanity checks after schema + grants. Expect 11 application tables.

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'opsconsole'
ORDER BY table_name;

SELECT table_name, table_type
FROM information_schema.tables
WHERE table_schema = 'bre_underwriting'
  AND table_name IN (
        'transaction_details',
        'transaction_details_srcreq_otp',
        'festival_summary_detailed_mins_new',
        'festival_summary_detailed_new'
      )
ORDER BY table_name;

SHOW GRANTS FOR 'opsconsole'@'%';
SHOW GRANTS FOR 'opsconsole_txn'@'%';
