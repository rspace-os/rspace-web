#!/bin/bash
set -x

## This performs an installation of RSpace web application. 

## TODO
## DB values in /etc/rspace/deployment.properties, license etc.
## DB set up in DB container.


CONFIG_DIR=/etc/rspace
MEDIA_ROOT=/media/rspace
TOMCAT_HOME=/usr/local/tomcat
## could comment this out or conditionally include for dev/prod builds
function installDevTools {
  apt-get -y update && apt get -y install vim mariadb-client less wget
}

function setupDirectories {
  mkdir -p ${MEDIA_ROOT}
  mkdir -p ${MEDIA_ROOT}/download
  mkdir -p ${MEDIA_ROOT}/logs-audit
  mkdir -p ${MEDIA_ROOT}/jmelody
  mkdir -p ${MEDIA_ROOT}/file_store
  mkdir -p ${MEDIA_ROOT}/archives
  mkdir -p ${MEDIA_ROOT}/tomcat-tmp
  mkdir -p ${MEDIA_ROOT}/backup
  mkdir -p ${MEDIA_ROOT}/indices/LuceneFTsearchIndices
  mkdir -p ${MEDIA_ROOT}/indices/FTsearchIndices
}

function configure {
 mkdir $CONFIG_DIR
 cp /deployment.properties $CONFIG_DIR/deployment.properties
 cp /license.cxl $CONFIG_DIR/license.cxl
 cp /setenv.sh $TOMCAT_HOME/bin
}
function setupWebApp {
   rm -rf $TOMCAT_HOME/webapps/*
   mv /ROOT.war $TOMCAT_HOME/webapps/ROOT.war
}

installDevTools
setupDirectories
configure
setupWebApp


