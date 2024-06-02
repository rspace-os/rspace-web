#!/bin/bash
## Moving to this special folder will run the script 
## on 1st execution.
set -x

## Check download link before running, the link is not stable and changes.
TOMCAT_VERSION=8.5.56

function install_packages {
	echo "installing packages"
   DEBIAN_FRONTEND=noninteractive apt-get -y update  &&  DEBIAN_FRONTEND=noninteractive apt-get -y upgrade
   DEBIAN_FRONTEND=noninteractive apt-get -y install openjdk-11-jdk wget mariadb-client less vim

}

function install_tomcat {
 
  mkdir /usr/local/tomcat
  wget http://apache.cs.utah.edu/tomcat/tomcat-8/v$TOMCAT_VERSION/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz -O /tmp/tomcat.tar.gz
  cd /tmp && tar xvfz tomcat.tar.gz
  cp -Rv /tmp/apache-tomcat-${TOMCAT_VERSION}/* /usr/local/tomcat/
}
install_packages
install_tomcat
