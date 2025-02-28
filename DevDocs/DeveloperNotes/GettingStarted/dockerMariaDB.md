# Installing MariaDB using Docker

As of Jan 2025, you should be starting your database using MariaDB on Docker, this has a similar setup to production RSpace so you're less likely to encounter errors. It is also easier to setup on your machine.

Ensure you have docker and docker-compose-v2 installed on your machine.

Below is the main docker-compose.yaml file:

```
services:
    rspace-db:
      image: 'mariadb:lts-jammy'
      restart: always
      container_name: rspace-db
      volumes:
        - type: volume
          source: rspace-db
          target: /var/lib/mysql
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

You will also need the SQL file to import into the DB, which you can find here - https://github.com/rspace-os/rspace-web/blob/main/src/main/resources/sqlUpdates/liquibaseConfig/rs-dbbaseline-utf8.sql

At this stage you will need to import the SQL file into the database, you can do this by coping the file into the docker container and then importing the file from the containers bash command line.

You may now go back to continue the Getting Started Guide.
