RSpace Installation and update changelog 1.87
=============================================

This document describes configuration changes that should be made before updating RSpace server to 1.87.

**Note** 

To update directly to 1.87, you must be running RSpace 1.73 or later.
This is so that database updates can be performed with the correct version of the code.  
Please read the changelogs for all intermediate versions and apply **all** mandatory changes and any optional changes you require.

If updating from a version earlier than 1.73.0, please read the Changelogs for the relevant previous 
versions (in the "previous_changelogs" directory) **before** updating.

If you are updating from an earlier version of RSpace, please follow the upgrade path below:

    | Updating to |   Oldest version to update from  | Oldest Compatible Document converter
    1.73.3               1.69.32                           0.18.1
    1.82.4               1.73.3                            0.18.1
    1.83.1               1.73.3                            0.18.1
    1.84.3               1.73.3                            0.18.1
    1.85.1               1.73.3                            0.18.1
    1.86.6               1.73.3                            0.18.1

All recent downloads are available from our download site:

    1.73.3  https://operations.researchspace.com/software/rspace/rspace-1.73.3.zip
    1.80.3  https://operations.researchspace.com/software/rspace/rspace-1.80.3.zip
    1.81.2  https://operations.researchspace.com/software/rspace/rspace-1.81.2.zip
    1.82.4  https://operations.researchspace.com/software/rspace/rspace-1.82.4.zip
    1.83.1  https://operations.researchspace.com/software/rspace/rspace-1.83.1.zip
    1.84.3  https://operations.researchspace.com/software/rspace/rspace-1.84.3.zip
    1.85.1  https://operations.researchspace.com/software/rspace/rspace-1.85.1.zip
    1.86.6  https://operations.researchspace.com/software/rspace/rspace-1.86.6.zip

The latest release version number can be checked at https://operations.researchspace.com/software/rspace/latestVersion.txt

Aspose Word converter:

    0.29.1  https://operations.researchspace.com/software/aspose/aspose-app-0.29.1.zip

For a complete listing and explanation of all deployment properties, please refer to the document `RSpaceConfiguration.md`.

## Server upgrades

### Ubuntu 18 EOL in April 2023

Note that Ubuntu 18.04.6 LTS operating system is approaching end of life date. 
If you're running RSpace on Ubuntu 18, you should upgrade to next LTS version, e.g. Ubuntu 20.04.5.

## Changes to RSpace configuration

### Mandatory changes

Please note that you **must** run RSpace 1.73.x (e.g. 1.73.3), or later, before updating to 1.87.

### Optional changes

No other changes

### Other notes

No other notes

#### End of File