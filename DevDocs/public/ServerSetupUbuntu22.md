Initial Installation notes for RSpace on Ubuntu 22 LTS : May 2022
====================================================

These notes give installation and configuration information for the latest 
release of ResearchSpace.
For RSpace application installation and configuration, please see:

Rspace Configuration

Contents of the release bundle
-------------------------------

The unzipped release should contain:

* RSpace installation notes.
* Server pre requisite installation notes.
* An example server log file illustrating successful server startup logs.
* The RSpace web application .war file
* A configuration file, deployment.properties
* A folder for 3rd party licenses
* A MySQL script to initialise the database. 

System Requirements
---------------------------

RSpace is a Electronic Lab Notebook (ELN) solution which allows you to
digitise and centralise your research notes and data.  The application
itself is a lightweight MySQL and Java application with a physical
file-store which can be a local disk or remote disk array/SAN.

The following services/packages are required by RSpace and are the default packages on Ubuntu22.

* Java JDK 17
* Tomcat 9.0.x
* MariaDB 10.6
* Webserver (We recommend Apache 2.4.x)


This is an overview of the steps needed. Each step is explained
further in subsequent sections.  In these instructions, we assume
you're using a Linux or other Unix OS; we recommend Ubuntu/CentOS or
Redhat

* Create user with sudo access (if none already exists)
* Set up application repository
* Install pre requisite software
* Configure Webserver
* Configure MySQL
* Setup file-store


Server setup
---------------------------
Persistent data (database and files) are best stored on a mounted volume. This facilitates transfer of data to a new instance
when updating a server. As little data as possible should be on the system disk. The volumes should be mounted under
`/media/mysql` and `/media/rspace`

Create a sudo user
-----------------------------

Add the sudo user and set the password. In this case we will use the username 'builder'

    sudo useradd -m builder
    sudo passwd builder
    sudo usermod -s /bin/bash builder

add the builder user to the sudoers file

    sudo visudo 
    add "builder ALL=(ALL:ALL) ALL"


You can then login as the new user.

Update and install required packages.
-----------------------------

	sudo apt-get update
	sudo apt-get upgrade

Reboot and repeat if necessary. Now install Java 17 ( the default fo Ubuntu22  is java 11 )

	 sudo apt install openjdk-17-jre-headless

The following will ask you to set a root password on Ubuntu but not on Debian, so have one ready
	
	sudo apt-get install mysql-server 

	sudo apt-get install wget apache2 tomcat9 unzip lynx emacs vim curl git

We also want to make sure that the services start on startup

    sudo update-rc.d apache2 defaults
    sudo update-rc.d mysql defaults
    sudo update-rc.d tomcat8 defaults
    
Get configuration files from GitHub:
------------------------------------

    git config --global credential.helper store
    git clone https://github.com/ResearchSpace-ELN/rspace-update.git
    cd rspace-update/
    cp templates/updateConfig-template.sh updateConfig.sh
    vim updateConfig.sh 
    
 And edit paths to their desired location, e.g to match:
 
 
    sudo  -u username mkdir -p /media/rspace/backup
    sudo mkdir -p /media/rspace/download
    sudo mkdir -p /media/rspace/logs-audit
    sudo mkdir -p /media/rspace/jmelody
    sudo mkdir -p /media/rspace/file_store
    sudo mkdir -p /media/rspace/archives
    sudo mkdir -p /media/rspace/tomcat-tmp

    sudo chown tomcat:tomcat /media/rspace/logs-audit
    sudo chown builder:builder /media/rspace/backup/
    sudo chown builder:builder /media/rspace/download
    sudo chown tomcat:tomcat /media/rspace/jmelody
    sudo chown tomcat:tomcat /media/rspace/archives
    sudo chown tomcat:tomcat /media/rspace/tomcat-tmp
    sudo chown tomcat:tomcat /media/rspace/file_store
    
The project contains files in setup/ which can be used directly instead of editing.

Configure MariaDB
-----------------

Once MariaDB is installed we run the secure installation script to remove any extras which we won't need.

    sudo mysql_secure_installation  (accept defaults.)
    
On Ubuntu22:
Follow instructions from https://www.digitalocean.com/community/tutorials/how-to-install-mariadb-on-ubuntu-20-04. 

Before creating RSpace database you must ensure that your database server settings are set 
 to use `utf8mb4` character encoding and `utf8mb4_unicode_ci` collation. 
The default for MariaDB 10.6  is utf8mb4, which will  store scientific or non-Western language symbols, but it's worth checking. 

Also configure `datadir` variable; this should be `/media/mysql` for a standard install. This is where datafiles will be kept.

I.e.

* stop the mysql server
* copy data files: `sudo rsync -av /var/lib/mysql /media/`
* edit mysql.d as below
* restart the server.
    
    [mysqld]
    ### character encoding ###
    character-set-client-handshake = FALSE
    sql_mode = STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION
    datadir = /media/mysql
    

Now, restart MySQL server, e.g. `sudo systemctl restart mysql`.
After restarting, assert that the following variables are set:

mysql> show variables like '%char%';

    +--------------------------+----------------------------+
    | Variable_name            | Value                      |
    +--------------------------+----------------------------+
    | character_set_client     | utf8mb4                    |
    | character_set_connection | utf8mb4                    |
    | character_set_database   | utf8mb4                     |
    | character_set_filesystem | binary                     |
    | character_set_results    | utf8mb4                    |
    | character_set_server     | utf8mb4                    |
    | character_set_system     | utf8                       |
    | character_sets_dir       | /usr/share/mysql/charsets/ |
    +--------------------------+----------------------------+
 
We can then create the Application database and user

In our example we will be using the following

    Database: rspace
    Username: rspacedbuser
    Password: rspacedbpwd

NOTE: If you use custom details please ensure you keep an eye out for the Editing Properties section of the RSpace Configuration document.

Create the database:

    mysql> create database rspace collate 'utf8mb4_unicode_ci';

Create the user, either at localhost or a remote database: 

    mysql> CREATE USER 'rspacedbuser'@'localhost' IDENTIFIED BY 'rspacedbpwd';

Grant database permissions (At a minimum, RSpace requires read/write/alter/drop/create table access.):

    mysql> GRANT ALL ON rspace.* TO 'rspacedbuser'@'localhost';
    mysql> flush privileges;

Use the supplied script, which will populate the database schema :
If you're using UTF-8 in the database ( which you should be) use this updated baseline script:

    mysql -u[username] -p [databasename] < ReleaseContent-[VERSION]-RELEASE/rs-dbbaseline-utf8.sql
    
    
Configure Apache 
-----------------------------

Apache is our preferred webserver, however there is nothing stopping you from using other servers such as nginx/lighthttpd etc. as long as they can proxy/pass on requests to Tomcat.

The Apache setup can be either with or without SSL depending on your own preference, below is configuration examples for both.

Below are described full instructions, however you can copy pre-configured files from the update project and then enable the modules 

    sudo cp  rspace-update/setup/rspace.conf /etc/apache2/sites-available/
    sudo cp  rspace-update/setup/ssl.conf /etc/apache2/mods-available/


SSL Enabled
----------------

    "<VirtualHost *:80>
        ServerName _YOURDOMAIN_
	Redirect permanent / https://_YOURDOMAIN_
	RemoteIPHeader X-Forwarded-For
    </VirtualHost>

    <VirtualHost *:443>
	ServerName _YOURDOMAIN_
	ProxyRequests Off
	
	Header edit Set-Cookie ^(.*)$ $1;SameSite=None

	RemoteIPHeader X-Forwarded-For
   	SSLEngine On
	SSLCertificateFile /etc/httpd/ssl/YOURCERTIFICATEFILE.crt
	SSLCertificateKeyFile /etc/httpd/ssl/YOURCERTIFICATEKEY.key
	SSLCertificateChainFile /etc/httpd/ssl/YOUCERTIFICATECHAIN.crt
   
	ProxyPass / ajp://localhost:8009/
	ProxyPassReverse / ajp://localhost:8009/
    </VirtualHost>""

We also have to ensure the proxy module is enabled
    
    sudo a2enmod proxy && sudo a2enmod headers &&  sudo a2enmod remoteip && sudo a2enmod ssl

then 

    sudo a2enmod proxy_ajp (if using ajp protocol)
    
or    
    
    sudo a2enmod proxy_http (if using http protocol)

#### ssl.conf changes


Edit or set the following properties in ssl.conf
    
    SSLCompression off
    SSLUseStapling on
    SSLStaplingCache "shmcb:logs/stapling-cache(150000)"
    SSLSessionCacheTimeout 86400

and also enforce at least TLS1.2:

    SSLProtocol TLSv1.2 TLSv1.3

And finally we enable to vhost and reload the apache configuration

    sudo a2ensite rspace && sudo systemctl restart apache2
    
Configuring Tomcat
------------------

Some variables in Tomcat need to be set for the application to work properly; there are several ways of setting these. However in our example, we will assume no other Tomcat applications are running on the server.

In Ubuntu these settings are set in the following file.

    /etc/default/tomcat9

So we edit this file and set the following;

    sudo vim /etc/default/tomcat9

    JAVA_OPTS="-Xms512m -Xmx2048m \
      -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/media/rspace/logs-audit"
    CATALINA_OPTS="-DpropertyFileDir=file:/etc/rspace/
     -DRS_FILE_BASE=/PATH/TO/FILESTORAGE -Djava.awt.headless=true\
     -Dliquibase.context=run -Dspring.profiles.active=prod -Djmelody.dir=/media/rspace/jmelody"
    CATALINA_TMP=/media/rspace/tomcat-tmp
    
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64  

(Adjust java_home variable to be that of the installed java jdk)
PLEASE NOTE: "/PATH/TO/FILESTORAGE" should be the server path to your filestore eg. /data/rspace-filestore 

The `-XX:+HeapDumpOnOutOfMemoryError` and `-XX:HeapDumpPath` are optional arguments that  set a  path to a folder that a heap dump can be written to in the event of an OutOfMemory error.
This is very useful for error diagnosis. A suitable folder would be writable by Tomcat, for example the folder holding the error logs.

The `jmelody.dir` holds a path to a folder that is writable by Tomcat and stores records of CPU/memory usage
 etc for trouble-shooting and monitoring of the server. To create this folder:
 
 
## Additional Tomcat settings:

1) You need to export CATALINA_OPTS

add
`export CATALINA_OPTS`
to `/usr/libexec/tomcat9/tomcat-start.sh`

2) Edit `/etc/systemd/system/multi-user.target.wants/tomcat.service`. It has more security than previous installations, you need to grant access to add paths that tomcat writes to, e.g.  append

`ReadWritePaths=/media/rspace/` 

Persistent backed-up space
--------------------------

RSpace needs a file area that will be backed up.  This currently contains:

1. The file store.  The RSpace file-store is the location that RSpace will store all files and documents users create in the application. 
2. The audit trail (and other log files that don't matter)
3. The MySQL database backups from attempted RSpace updates (this is optional but we recommend it)

This can be a local disk/partition or an external storage solution as long as the path can be mounted onto the filesystem.  And it should be backed up!

In our example we will use a simple local disk. 

    sudo mkdir -p /media/rspace

We make three areas for the three purposes listed above and give then the appropriate permissions, where <user> is the username that you do system admin tasks under.:

    sudo mkdir /data/rspace/filestore
    sudo chown tomcat8:tomcat8 /data/rspace/filestore
    sudo mkdir /data/rspace/logs-audit 
    sudo chown tomcat8:tomcat8 /data/rspace/logs-audit
    sudo mkdir /data/rspace/update-backups
    sudo chown user:user /data/rspace/update-backups
   
End of file
