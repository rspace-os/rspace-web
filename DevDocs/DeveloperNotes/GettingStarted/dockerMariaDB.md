# Installing MariaDB using Docker

For local development, use MariaDB 10.11 or 11.8, the versions tested in CI. The [Docker dev stack](/docker/dev/README.md) defaults to 11.8 and uses the database settings from [rspace-docker](https://github.com/rspace-os/rspace-docker), with a smaller InnoDB buffer pool for local use. Use this guide if you need a standalone database container.

Ensure you have docker and docker-compose-v2 installed on your machine.

Below is the main docker-compose.yaml file:

```
services:
    rspace-db:
      image: 'mariadb:11.8'
      restart: always
      container_name: rspace-db
      volumes:
        - type: volume
          source: rspace-db
          target: /media/mysql
        - type: bind
          source: ./db-config.cnf
          target: /etc/alternatives/my.cnf
      environment:
          MARIADB_ROOT_PASSWORD: rspacedbpwd
          MARIADB_USER: rspacedbuser
          MARIADB_PASSWORD: rspacedbpwd
      ports:
        - '127.0.0.1:3306:3306'
volumes:
  rspace-db:
```

You can get the db-config.cnf file from here - https://github.com/rspace-os/rspace-docker/blob/main/configs/db-config.cnf
Its `datadir` must match the `/media/mysql` volume target above, or database files will not persist when the container is recreated.

Place all these files in your home dir, inside a folder called "rspace-db". Once inside that folder, you can run the container commands:
- Start container: docker compose up -d 
- Stop container: docker compose down
- Check container logs: docker logs rspace-db


Once the database container has started up for the first time, you will need to create the database with the following commands:
```
docker exec -it rspace-db bash
mariadb -u root -p
CREATE DATABASE rspace collate 'utf8mb4_unicode_ci';                
GRANT ALL ON rspace.* TO 'rspacedbuser'@'127.0.0.1';
exit
```
***For local development with docker MariaDB, you might need to use ``` GRANT ALL ON rspace.* TO 'rspacedbuser'``` or ``` GRANT ALL ON rspace.* TO 'rspacedbuser'@'localhost'``` instead***

(Note for developers - no need to manually import and run this file when using maven to drop-recreate-db on your locally installed MariaDB and RSpace)
You will also need the SQL file to import into the DB, which you can find here - https://github.com/rspace-os/rspace-web/blob/main/src/main/resources/sqlUpdates/liquibaseConfig/rs-dbbaseline-utf8.sql

At this stage you will need to import the SQL file into the database, you can do this by coping the file into the docker container and then importing the file from the containers bash command line.

You may now go back to continue the Getting Started Guide.
