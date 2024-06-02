RSpace Installation and update changelog 1.92
=============================================

This document describes configuration changes that should be made before updating RSpace server to 1.92.

**Note** 

To update directly to 1.92, you must be running RSpace 1.73 or later.
This is so that database updates can be performed with the correct version of the code.  
Please read the changelogs for all intermediate versions and apply **all** mandatory changes and any optional changes you require.

If updating from a version earlier than 1.73.0, please read the Changelogs for the relevant previous 
versions (in the "previous_changelogs" directory) **before** updating.

If you are updating from an earlier version of RSpace, please follow the upgrade path below:

    | Updating to |   Oldest version to update from  | Oldest Compatible Document converter
    1.73.3               1.69.32                           0.18.1
    1.86.6               1.73.3                            0.18.1
    1.87.3               1.73.3                            0.18.1
    1.88.1               1.73.3                            0.18.1
    1.89.2               1.73.3                            0.18.1
    1.90.0               1.73.3                            0.18.1

All recent downloads are available from our download site:

    1.73.3  https://operations.researchspace.com/software/rspace/rspace-1.73.3.zip
    1.86.6  https://operations.researchspace.com/software/rspace/rspace-1.86.6.zip
    1.87.3  https://operations.researchspace.com/software/rspace/rspace-1.87.3.zip
    1.88.1  https://operations.researchspace.com/software/rspace/rspace-1.88.1.zip
    1.89.2  https://operations.researchspace.com/software/rspace/rspace-1.89.2.zip
    1.90.0  https://operations.researchspace.com/software/rspace/rspace-1.90.0.zip
    1.91.1  https://operations.researchspace.com/software/rspace/rspace-1.91.1.zip

The latest release version number can be checked at https://operations.researchspace.com/software/rspace/latestVersion.txt

Aspose Word converter:

    0.29.1  https://operations.researchspace.com/software/aspose/aspose-app-0.29.1.zip

For a complete listing and explanation of all deployment properties, please refer to the document `RSpaceConfiguration.md`.

## Server upgrades

### Java 17 now recommended for running RSpace

OpenJDK Java 11 LTS, which was previously-recommended version to run RSpace (Tomcat9) on, is reaching end of life in September 2023.
We recommend upgrading to OpenJDK Java 17 LTS, which is supported by RSpace since RSpace 1.90 release. 

When upgrading, you may want to check our [Migrating-RSpace-to-Java-17.md](Migrating-RSpace-to-Java-17.md) guide.

If you need any help upgrading Java and/or Tomcat installation on your RSpace server please contact support@researchspace.com. 

### Advance notice of Java 17 being required for running RSpace 

We're planning to start building RSpace with OpenJDK Java 17 from October 2023 (RSpace 1.93 release). 
At this point you'll need to run Tomcat on Java 17, or you won't be able to upgrade to new RSpace version. 
 
### Ubuntu 18 EOL in April 2023

Note that Ubuntu 18.04.6 LTS operating system is end of life now.
If you're running RSpace on Ubuntu 18, you should upgrade to next LTS version, e.g. Ubuntu 20.04.5.

## Changes to RSpace configuration

### Mandatory changes

No mandatory changes.

### Optional changes

No optional changes.

### Other notes

No other notes.

#### End of File