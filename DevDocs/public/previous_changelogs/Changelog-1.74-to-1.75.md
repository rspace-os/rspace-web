RSpace Installation and update changelog 1.75
=============================================

This document describes configuration changes that should be made before updating RSpace server to 1.75.

**Note** 

To update to  1.75, you must first update to a version >= 1.73.0
This is so that database updates can be performed with the correct version of the code.  
Please read the changelogs for all intermediate versions and apply **all** mandatory changes and any optional changes you require.

If updating from a version earlier than 1.73.0, please read the Changelogs for the relevant previous 
versions (in the "previous_changelogs" directory) **before** updating.

If you are updating from an earlier version of RSpace, please follow the upgrade path below:

    | Updating to |   Oldest version to update from  | Oldest Compatible Document converter
    1.63.x               1.60.3                            0.18.1
    1.64.x               1.60.3                            0.18.1
    1.65.x               1.60.3                            0.18.1   
    1.66.x               1.60.3                            0.18.1 
    1.67.x               1.60.3                            0.18.1  
    1.68.x               1.60.3                            0.18.1
    1.69.x               1.60.3                            0.18.1
    1.70.x               1.60.3                            0.18.1
    1.71.x               1.60.3                            0.18.1
    1.72.x               1.60.3                            0.18.1
    1.73.x               1.60.3                            0.18.1

All recent downloads are available from our download site:

    1.63.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.63.3.zip
    1.64.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.64.3.zip
    1.65.2  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.65.2.zip
    1.65.2  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.65.2.zip
    1.67.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.67.3.zip
    1.68.6  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.68.6.zip
    1.69.54 https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.69.54.zip
    1.70.5  https://operations.researchspace.com/software/rspace/rspace-1.70.5.zip
    1.71.1  https://operations.researchspace.com/software/rspace/rspace-1.71.1.zip
    1.72.5  https://operations.researchspace.com/software/rspace/rspace-1.72.5.zip
    1.73.3  https://operations.researchspace.com/software/rspace/rspace-1.73.3.zip
    1.74.5  https://operations.researchspace.com/software/rspace/rspace-1.74.5.zip
    1.75.0  https://operations.researchspace.com/software/rspace/rspace-1.75.0.zip

The latest release version number is at https://operations.researchspace.com/software/rspace/latestVersion.txt

 Aspose Word converter:
 
    0.27.0  https://operations.researchspace.com/software/aspose/aspose-app-0.27.0.zip
    0.28.1  https://operations.researchspace.com/software/aspose/aspose-app-0.28.1.zip

For a complete listing and explanation of all deployment properties, please refer to the document `RSpaceConfiguration.md`.

Server upgrades
---------------

### Java 11 required now

Oracle's support for Java 8 has ended in January 2019, and RSpace now requires Java 11, which is the current LTS ("Long Term Support") version of Java. 
 
Unless you have commercial support from Oracle for Java 11, you must change to an openJDK build. 

Changes to RSpace configuration
-------------------------------

### Mandatory changes

Please note that you **must** update to version 1.73.x (e.g. 1.73.3) before updating to 1.75.

There is a risk of data loss in the database if you update to 1.75 directly from a 1.72.x version or earlier. Please read the release notes for each release before beginning the update process.

### Optional changes

  No optional changes.

### New deployment properties

There are new properties to support some optional integrations.

* **netfilestores.login.directory.option**  -
Enables SFTP filesystem users to specify the remote directory they log into. For example, connect to `<USER>`, instead of root dir. The property exposes an option to allow SFTP filesystem users to have target directories in the configuration page for Institutional File Systems.
Leave this property as false if SFTP filesystem users do not need to specify the remote directory they log into.

* **netfilestores.smbj.name.match.path**  -
  When true this will cause samba names to be superseded by file path names for the SMBv2/3 connector. It should normally be left as false.

Clustermarket is an equipment booking system. A prototype integration is now available. 

* **clustermarket.api.url** URL of the exposed Clustermarket API. For example
  `https://api.staging.clustermarket.com/v1/`.
* **clustermarket.web.url** URL of the Clustermarket website (eg staging/or real) . For example
  `https://staging.clustermarket.com/` (for staging) and `https://app.clustermarket.com/` (for the real website).
* **clustermarket.client.id** - Client id of Clustermarket App registered for given RSpace instance
* **clustermarket.secret** - Client secret of Clustermarket App registered for given RSpace instance

### Other notes

  No other notes.

#### End of File
