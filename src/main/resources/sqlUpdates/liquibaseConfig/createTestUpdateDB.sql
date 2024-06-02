create  database if not exists testLiquibaseUpdate;
GRANT ALL ON testLiquibaseUpdate.* TO 'rspacedbuser'@'localhost';
use testLiquibaseUpdate;
source rs-dbbaseline-utf8.sql;
