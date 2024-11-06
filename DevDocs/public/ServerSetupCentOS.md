(for reference only!) Installation notes for RSpace on RHEL/CentOS 7
==============================================

**Please note, that this instructions are out of date, and for reference only!** 
As of April 2024 RSpace requires minimum Java JDK 17, Tomcat 9.0, MariaDB 10.3, etc.
We recommend running Ubuntu server, please check our setup guides for Ubuntu OS to see the latest requirement. 

-------------------------------

These notes give installation and configuration information for the latest 
release of RSpace.

For RSpace application installation and configuration, please see:

RSpace Configuration

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

RSpace is a Electronic Lab Notebook (ELN) solution which allows you to digitise and centralise your research notes and data.
The application itself is a lightweight MySQL and Java application with a physical file-store which can be a local disk or remote disk array/SAN. 

The following services/packages are required by RSpace.

* Java JDK 8
* Tomcat Tomcat 7.54 or later,  or 8
* MySQL or MariadDB  5.6 
* Webserver (We recommend Apache2)

This is an overview of the steps needed. Each step is explained further in subsequent sections.
In these instructions, we assume you're using a Linux or other Unix OS; we recommend Ubuntu/CentOS or Redhat

* Create user with sudo access (if none already exists)
* Install JDK
* Install pre requisite software
* Configure Webserver
* Configure MySQL / MariaDB
* Setup file-store

Create a sudo user
-----------------------------

Add the sudo user and set the password. In this case we will use the username 'builder'

    sudo useradd -m builder
    sudo passwd builder

#add the builder user to the sudoers file

    sudo visudo
    add "builder ALL=(ALL:ALL) ALL"

You can then login as the new user.

You can also set up to ssh to the server with this account:
    
    sudo su
    cd /home/builder
    cp -rp ../centos/.ssh .
    chown -R builder:builder .ssh

Install JDK
-----------------------------

OpenJDK
    sudo yum install java-1.8.0-openjdk

    sudo yum update
    sudo yum install vim httpd tomcat mariadb-server unzip wget links mod_ssl openssl
  
We also want to make sure that the services start on startup

    sudo chkconfig httpd on
    sudo chkconfig mariadb on
    sudo chkconfig tomcat on

Configure MariaDB
-----------------------------

Once MariaDB is installed we run the secure installation script to remove any extras which we won't need.

    sudo service mariadb start

Before you secure mariadb, you might need to reset the root user
permissions to allow it full permissions from localhost.  You can try
without doing this, if you find you can't log in to mysql as root then
just uninstall (yum remove mariadb-server; yum purge; yum autoremove)
and reinstall (yum install mariadb).

    sudo mysql_secure_installation

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

Grant database permissions (As a minimum, RSpace requires read/write/alter/drop/create table access.):

    mysql> GRANT ALL ON rspace.* TO 'rspacedbuser'@'localhost';
	mysql> flush privileges;

Use the supplied script, which will populate the database schema :

    mysql -u[username] -p [databasename] < ReleaseContent-[VERSION]-RELEASE/rs-dbbaseline-utf8.sql


Configure Apache 
-----------------------------

Apache is our preferred webserver. However, there is nothing stopping you from using other servers such as nginx/lighthttpd etc. as long as they can proxy/pass on requests to Tomcat.

The Apache setup can be either with or without SSL depending on your own preference, below is configuration examples for both. In either case replace '_YOUR_DOMAIN' with your server address e.g. rspace.myorg.edu

Non SSL
----------------

    sudo vim /etc/httpd/conf.d/rspace.conf

    <VirtualHost *:80>
    ServerName _YOURDOMAIN_
    ProxyRequests Off
    Header edit Set-Cookie ^(.*)$ $1;SameSite=None
    ProxyPass / http://localhost:8080/
    ProxyPassReverse / http://localhost:8080/
    </VirtualHost>

SSL Enabled
----------------

    <VirtualHost *:80>
    ServerName _YOURDOMAIN_
    Redirect permanent / https://_YOURDOMAIN_
    </VirtualHost>

    <VirtualHost *:443>
	ServerName _YOURDOMAIN_
	ProxyRequests Off
    Header edit Set-Cookie ^(.*)$ $1;SameSite=None
	SSLCertificateFile /etc/httpd/ssl/YOURCERTIFICATEFILE.crt
	SSLCertificateKeyFile /etc/httpd/ssl/YOURCERTIFICATEKEY.key
	SSLCertificateChainFile /etc/httpd/ssl/YOUCERTIFICATECHAIN.crt
   
	ProxyPass / http://localhost:8080/
	ProxyPassReverse / http://localhost:8080/
    </VirtualHost>

And finally reload the apache configuration

    sudo service httpd restart


File-store creation
-----------------------------

The system file-store is the location that RSpace will store all files and documents users create in the application. This can be a local disk/partition or an external storage solution as long as the path can be mounted onto the filesystem.

In our example we will use a simple local disk. 

    sudo mkdir -p /data/rspace-store

The Tomcat user needs ownership of the directory to create and edit entries.

    sudo chown tomcat: /data/rspace-store


End of file
