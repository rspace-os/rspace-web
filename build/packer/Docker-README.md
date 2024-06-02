You can run containers individually or all-at-once using docker-compose, described below.

## Running in Docker

### First time run for completely clean install

    docker network create rspace

### Launching  containers

These are the same whether running for first or subsequent times. Unless you've built all the images yourself you'll need Dockerhub password or token to login to access the docker images.

    docker run --name rspacedb --net rspace --mount source=rspace-db,target=/var/lib/mysql,type=volume -e MYSQL_USER=xxx -e MYSQL_PASSWORD=xxx -e MYSQL_DATABASE=rspace -d rspaceops/rspace-services:rspace-db-5.7

    docker run --name rspace-web --net rspace -p8080:8080 --mount source=rspace-web-config,target=/etc/rspace,type=volume  --mount source=rspace-web-files,target=/media/rspace,type=volume -d rspaceops/rspace-services:rspace-web-1.68.2

Note that version of the image may be different.

You can mount an external deployment.properties file from the host in order to configure RSpace more easily:

Replace `--mount source=rspace-web-config,target=/etc/rspace,type=volume` in the command above with

    --mount source=/absolute/path/to/directory/deployment.properties,target=/etc/rspace/deployment.properties,type=bind


### Starting from  stopped containers

    docker start rspacedb
    docker start rspace-web

 All files, DB data and configuration is stored on Docker volumes, and will be persisted through container stop/termination actions.

    docker volume ls

should show the following 3 named volumes (unless you mounted deployment.properties  from the host, in which case there will be 2).

    local               rspace-db
    local               rspace-web-config
    local               rspace-web-files

**Don't** delete these volumes, all your RSpace state is stored here!!

## Using docker-compose

### Running locally from pre-built images after checkout of this project 

This does not build the project from scratch, but launches RSpace from pre-built images

1. Copy the 'deployment.properties' file in web/resources to your home folder. Edit it as need be.
2. Edit the .env file to set the name of the RSpace Docker image to pull from Dockerhub. E.g. `RSPACE_TAG=rspace-web-1-69-1`
3. Login to dockerhub using `docker login` if you need.
4. Run `docker-compose -p rspace up -d`

After startup the application will run at localhost:8080 as normal

### Running locally from docker-compose file only

Note for production, the `docker-compose.yaml` file will be public, so should not contain any secrets.
Before launching you'll need the following ready:

* A Docker-ready deployment.properties file. This will be issued by ResearchSpace.
* A License key, issued by ResearchSpace
* A database name, username, and password for RSpace to use to access MySQL running in a container. This can be whatever you decide.
* A file called `.env` in the same folder as `docker-compose.yaml`, as described above

You will need to put a file called `rspace-vars.env` in the same folder as `docker-compose.yaml`, with the following variables set, replacing 'xxxx' with your actual
credentials:

    MYSQL_DATABASE=rspace  
    MYSQL_USER=xxxx
    MYSQL_PASSWORD=xxx

Then run 

    docker-compose -p rspace up -d

to launch RSpace, the database, aspose-web and snapgene-web containers.

If you are just testing and want to remove everything once you're done:

    docker-compose down -v

will delete all data volumes as well as containers.



As of August 2022, Aspose does not work in the docker version of RSpace. In the docker-compose file remove aspose: section and remove aspose from depends_on:. Also (this might be a windows only issue) the "volumes:, - type: bind, source:" for the deployment.properties you want to put the full path of the file (so source: C:\Users\Ramon\deployment.properties for me) as just {HOME}/ gave a file not found error for me
