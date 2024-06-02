SET FOREIGN_KEY_CHECKS = 0;

--delete from UserConnection;
--delete from DATABASECHANGELOG;
--delete from DATABASECHANGELOGLOCK;


drop database if exists rspacenightly;
create database if not exists rspacenightly collate 'utf8mb4_unicode_ci';

SET FOREIGN_KEY_CHECKS = 1;