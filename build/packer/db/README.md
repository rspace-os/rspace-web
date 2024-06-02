Builds a MySQL database docker image.
When launched , the container will run RSpace DB initialisation script.

Should be launched with following properties via -e on docker command  (or see docker-compose)

MYSQL_DATABASE=rspace
MYSQL_USER=<username>
MYSQL_PASSWORD=<password>