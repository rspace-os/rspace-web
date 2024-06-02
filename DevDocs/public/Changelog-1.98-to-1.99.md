RSpace Installation and update changelog 1.99
=============================================

This document describes configuration changes that should be made before updating RSpace server to 1.99.

**Note**

To update directly to 1.99, you must be running RSpace 1.73 or later.
This is so that database updates can be performed with the correct version of the code.
Please read the changelogs for all intermediate versions and apply **all** mandatory changes and any optional changes you require.

If updating from a version earlier than 1.73.0, please read the Changelogs for the relevant previous
versions (in the "previous_changelogs" directory) **before** updating.

If you are updating from an earlier version of RSpace, please follow the upgrade path below:

    | Updating to |   Oldest version to update from  | Oldest Compatible Document converter
    1.73.3               1.69.32                           0.18.1
    1.99.0               1.73.3                            0.18.1

All recent downloads are available from our download site:

    1.73.3  https://operations.researchspace.com/software/rspace/rspace-1.73.3.zip
    1.92.1  https://operations.researchspace.com/software/rspace/rspace-1.92.1.zip
    1.93.0  https://operations.researchspace.com/software/rspace/rspace-1.93.0.zip
    1.94.1  https://operations.researchspace.com/software/rspace/rspace-1.94.1.zip
    1.95.1  https://operations.researchspace.com/software/rspace/rspace-1.95.1.zip
    1.96.3  https://operations.researchspace.com/software/rspace/rspace-1.96.3.zip
    1.97.5  https://operations.researchspace.com/software/rspace/rspace-1.97.5.zip
    1.98.1  https://operations.researchspace.com/software/rspace/rspace-1.98.1.zip

The latest release version number can be checked at https://operations.researchspace.com/software/rspace/latestVersion.txt

Aspose Word converter:

    0.30.0  https://operations.researchspace.com/software/aspose/aspose-app-0.30.0.zip

For a complete listing and explanation of all deployment properties, please refer to the document `RSpaceConfiguration.md`.

## Server upgrades

### Java 17 now required for running RSpace

RSpace supports Java 17 since 1.91 release, and starting with 1.98 release Java 17 is a requirement. 
If you haven't upgraded yet check our guide `Migrating-RSpace-to-Java-17.md`, or contact RSpace Support.

## Changes to RSpace configuration

### Mandatory changes

#### Deployment properties 'jdbc.url', 'jdbc.username' and 'jdbc.password' have new default values

We've changed default values of deployment properties used for establishing database connection.
Most probably your installation is not using defaults, but if your RSpace database is called
"ecat5_dev", or your username/password is  "ecatdev"/"ecatpwd", then you can revert to old 
values by adding/overriding some/all the following deployment.properties: 

jdbc.url=jdbc:mysql://localhost:3306/ecat5_dev
jdbc.username=ecatdev
jdbc.password=ecatpwd

We strongly advise to not use default password on mysql connection - you should create own secure 
password that is subsequently set in 'jdbc.password' deployment property.    

### Optional changes

No optional changes

### Other notes

No other notes.

#### End of File
