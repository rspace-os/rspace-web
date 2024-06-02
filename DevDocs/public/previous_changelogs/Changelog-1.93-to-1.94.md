RSpace Installation and update changelog 1.94
=============================================

This document describes configuration changes that should be made before updating RSpace server to 1.94.

**Note** 

To update directly to 1.94, you must be running RSpace 1.73 or later.
This is so that database updates can be performed with the correct version of the code.  
Please read the changelogs for all intermediate versions and apply **all** mandatory changes and any optional changes you require.

If updating from a version earlier than 1.73.0, please read the Changelogs for the relevant previous 
versions (in the "previous_changelogs" directory) **before** updating.

If you are updating from an earlier version of RSpace, please follow the upgrade path below:

    | Updating to |   Oldest version to update from  | Oldest Compatible Document converter
    1.73.3               1.69.32                           0.18.1
    1.93.0               1.73.3                            0.18.1

All recent downloads are available from our download site:

    1.73.3  https://operations.researchspace.com/software/rspace/rspace-1.73.3.zip
    1.90.0  https://operations.researchspace.com/software/rspace/rspace-1.90.0.zip
    1.91.1  https://operations.researchspace.com/software/rspace/rspace-1.91.1.zip
    1.92.1  https://operations.researchspace.com/software/rspace/rspace-1.92.1.zip
    1.93.0  https://operations.researchspace.com/software/rspace/rspace-1.93.0.zip

The latest release version number can be checked at https://operations.researchspace.com/software/rspace/latestVersion.txt

Aspose Word converter:

    0.29.1  https://operations.researchspace.com/software/aspose/aspose-app-0.29.1.zip

For a complete listing and explanation of all deployment properties, please refer to the document `RSpaceConfiguration.md`.

## Server upgrades

### Java 11 EOL in September 2023

OpenJDK Java 11 LTS, which was previously-recommended version to run RSpace (Tomcat9) on, has reached end of life.
We recommend upgrading to OpenJDK Java 17 LTS, which is supported since RSpace 1.90 release.

When upgrading, you may want to check our [Migrating-RSpace-to-Java-17.md](Migrating-RSpace-to-Java-17.md) guide.

If you need any help upgrading Java and/or Tomcat installation on your RSpace server please contact support@researchspace.com.

### Advance notice of Java 17 being required for running RSpace

Starting with 1.94 release, RSpace is compiled and tested with Java 17, with Java 11 backward compatibility flags.

We're planning to stop using Java 11 compatibility flags from beginning of 2024, at this point you'll need to 
run Tomcat on Java 17, or you won't be able to upgrade to new RSpace version.

## Changes to RSpace configuration

### Mandatory changes

No mandatory changes.

### Optional changes

No optional changes.

### Other notes

No other notes.

#### End of File