# Setting up RSpace from an AMI

This document describes the RSpace configuration for a server launched
from an RSpace AMI. A basic competence with Linux, file permissions,
Apache web server and shell commands is required.

## Setting deployment properties

The file `/etc/rspace/deployment.properties` contains key configuration
for how RSpace will run. There are some properties that must be set for
your installation at the top of this file.

Details of all the properties can be found in the document
[RSpaceConfiguration.md](public/RSpaceConfiguration.md)

## Updating RSpace
It may be that there is a newer version of RSpace. IF there is a newer
version, you can get the new distribution as follows. Credentials will
be supplied to you by RSpace.

```bash
wget --user=USERNAME --ask-password https://operations.researchspace.com/software/rspace/rspace-%VERSION%.zip
unzip rspace-%VERSION%.zip 
```

Inside the release distribution there is a document
[MaintainingRSpace.md](public/MaintainingRSpace.md)
which explains how to perform an update.

## Setting up SSL certificates

For security, RSpace should use an HTTPS connection to protect user data over
a network. This is configured in Apache web server, `/etc/apache2/rspace.conf`
file. Set the locations of your SSL certificates in this file.

## Stopping and starting RSpace

```bash
# Starting RSpace
sudo service tomcat9 start

# Stopping RSpace
sudo service tomcat9 stop
```

## Log files
Log files are in `/media/rspace/logs-audit`.

You can see live logs with: `tail -f /media/rspace/logs-audit/error.log`
