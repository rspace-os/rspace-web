RSpace Installation and update changelog 1.76
=============================================

This document describes configuration changes that should be made before updating RSpace server to 1.76.

**Note** 

To update to  1.76, you must first update to a version >= 1.73.0
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
    1.69.32              1.60.3                            0.18.1
    1.69.>32             1.60.32                            0.18.1
    1.70.x               1.69.32                           0.18.1
    1.71.x               1.69.32                           0.18.1
    1.72.x               1.69.32                           0.18.1
    1.73.x               1.69.32                           0.18.1
    1.74.x               1.73.3                            0.18.1
    1.75.x               1.73.3                            0.18.1
    1.76.x               1.73.3                            0.18.1

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
    1.75.7  https://operations.researchspace.com/software/rspace/rspace-1.75.7.zip
    1.76.0  https://operations.researchspace.com/software/rspace/rspace-1.76.0.zip

The latest release version number is at https://operations.researchspace.com/software/rspace/latestVersion.txt

 Aspose Word converter:

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

Please note that you **must** update to version 1.73.x (e.g. 1.73.3) before updating to 1.76.

There is a risk of data loss in the database if you update to 1.76 directly from a 1.72.x version or earlier.
Please read the release notes for each release before beginning the update process.

### Optional changes

New deployment properties:

* **slow.request.time** A duration, in milliseconds, default = 2000. Requests taking longer than this duration will be logged to SlowRequests.txt

### Other notes

  No other notes.

#### End of File