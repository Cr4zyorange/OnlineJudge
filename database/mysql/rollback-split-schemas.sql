-- Rollback only changes routing. The source database remains authoritative
-- throughout the compatibility window; split schemas are intentionally retained
-- as recoverable backups until an operator separately approves their removal.
SELECT 'Set all service JDBC URLs back to the source schema; do not drop split schemas.' AS rollback_action;
