## Day-to-day maintenance

This document describes basic information about RSpace installation and some commands to restart
RSpace if necessary.

It is provided to RSpace customers who provide service access to only a single ResearchSpace staff member
rather than a service account. In the event the staff member is not available,
IT staff can use this information to restart RSpace after an outage.

## Restart RSpace

    sudo systemctl restart tomcat9

is the standard restart command and fixes most issues, e.g. RSpace being down, or running slowly.

Once started, track the logs, e.g.  

     sudo tail -f /data/rspace/logs-audit/error.log

Check RSpace is running:

    ps -ax | grep tomcat9


## Important configuration files

RSpace is a Java web application. It is deployed as a single file and runs inside Apache Tomcat.
It  runs on localhost:8080

Apache Web Server acts as a reverse proxy server, handling SSL and forwarding requests to RSpace.
The examples below refer to Debian/Ubuntu installations

* Apache configuration is in `/etc/apache2/sites-enabled/rspace.conf`

* Tomcat configuration is in `/etc/default/tomcat9`

* RSpace is installed at `/var/lib/tomcat9/webapps/ROOT`.

* RSpace configuration is in `/etc/rspace/deployment-sso.properties` (SSO) or  `/etc/rspace/deployment-standalone.properties` (standalone)

* Shibboleth configuration is in `/etc/shibboleth`

* SSL certificates can be anywhere but typically in `/etc/apache2/ssl`

## RSpace database

-  standard MariaDB 10.3 - 10.6 or MySQL 5.7
-  Restart: `sudo systemctl restart mariadb` or `sudo systemctl restart mysql`

## Important log files

These should not be altered.

* General error logging:  `/media/rspace/logs-audit/error.log`
* Security log:  `/media/rspace/logs-audit/SecurityEvents.txt`
* Tomcat log: `/var/log/tomcat9/error.log` and `/var/lib/tomcat9/error.log` 
* Apache logs: `/var/log/httpd`
* Shibboleth logs: `/var/log/shibboleth/shibd.log`

## Switching between SSO and Standalone mode

It can be useful for tricky debugging or maintenance of an SSO-deployed RSpace to run 
in standalone mode that uses RSpace's own internal authentication system.

To switch from SSO mode to standalone mode: `sudo ./rhel8_enableStandalone.sh`

To switch from standalone mode to SSO  mode: ` sudo ./rhel8_enableSSO.sh`

## END OF DOCUMENT