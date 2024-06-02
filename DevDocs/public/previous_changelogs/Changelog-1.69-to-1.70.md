RSpace Installation and update changelog 1.70
=============================================

This document describes configuration changes that should be made before updating RSpace server to  1.70.

**Note** 

To update to  1.70, you must first update to a version >= 1.60.4
This is so that database updates can be performed with the correct version of the code.  
Please read the changelogs for all intermediate versions and apply **all** mandatory changes and any optional changes you require.

If updating from a version earlier than 1.60.4, please read the Changelogs for the relevant previous 
versions (in the "previous_changelogs" directory) **before** updating.

If you are updating from an earlier version of RSpace, please follow the upgrade path below:

    | Updating to |   Oldest version to update from  | Oldest Compatible Document converter
    1.57.x               1.53.10                           0.18.1
    1.58.x               1.53.10                           0.18.1
    1.59.x               1.53.10                           0.18.1 
    1.60.x               1.59.2                            0.18.1 
    1.61.x               1.59.2                            0.18.1 
    1.62.x               1.59.2                            0.18.1 
    1.63.x               1.60.3                            0.18.1
    1.64.x               1.60.3                            0.18.1
    1.65.2               1.60.3                            0.18.1   
    1.66.x               1.60.3                            0.18.1 
    1.67.3               1.60.3                            0.18.1  
    1.68.0               1.60.3                            0.18.1
    1.69.0               1.60.3                            0.18.1   

All recent downloads are available from our download site:

    1.57.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.57.3.zip
    1.58.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.58.3.zip
    1.59.2  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.59.2.zip
    1.60.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.60.3.zip
    1.61.2  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.61.2.zip
    1.62.4  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.62.4.zip
    1.63.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.63.3.zip
    1.64.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.64.3.zip
    1.65.2  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.65.2.zip
    1.65.2  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.65.2.zip
    1.67.3  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.67.3.zip
    1.68.6  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.68.6.zip
    1.69.54  https://researchspace.com/electronic-lab-notebook/media/rspace/rspace-1.69.54.zip

 Aspose Word converter:
 
    0.26.0  https://researchspace.com/electronic-lab-notebook/media/rspace/aspose-app-0.26.0.zip

For a complete listing and explanation of all deployment properties, please refer to the document `RSpaceConfiguration.md`.

Server upgrades
---------------

None required, but see `Mandatory changes` section below.

#### Advance notice of Java upgrade

Oracle's support for Java 8 has ended in January 2019, and we are planning to migrate to Java 11.
Java 11 is the current LTS ("Long Term Support") version of Java. 
 
If you self-manage your RSpace server, please let us know if updating from Java 8 will be problematic for you.

Unless you have commercial support from Oracle for Java 8, you must change to an openJDK build. 

Changes to RSpace configuration
-------------------------------

### Mandatory changes

  Please note that you **must** update to version 1.60.x (e.g. 1.60.4) before updating to 1.70.
  
  There is a risk of data loss in the database if you update to 1.70 directly from a 1.59.x version or earlier. Please read the release notes for each release before beginning the update process.
  

### Optional changes


### New deployment properties

A new property **api.permissiveCors.enabled** can be used to allow 3rd party websites to access RSpace API. If the property is set to 'true' the API endpoints will set CORS headers (e.g. 'Access-Control-Allow-Origin') in API responses. Default is 'false'.

### Other notes

  No other notes.

#### End of File
