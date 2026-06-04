-- Runs once, on first DB container start (empty data directory).
--
-- The MariaDB image already creates the `rspace` database and the
-- `rspacedbuser` account (scoped to that one database). RSpace's
-- `drop-recreate-db` build step, however, issues `DROP DATABASE` /
-- `CREATE DATABASE` as `rspacedbuser`, which requires server-wide privileges.
-- Broaden the grant here. These are throwaway local-development credentials and
-- must never be used for a real deployment.
CREATE DATABASE IF NOT EXISTS rspace CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'rspacedbuser'@'%' IDENTIFIED BY 'rspacedbpwd';
GRANT ALL PRIVILEGES ON *.* TO 'rspacedbuser'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
