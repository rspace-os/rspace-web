RSpace Installation and update changelog 1.98
=============================================

This document describes configuration changes that should be made before updating RSpace server to 1.98.

**Note**

To update directly to 1.98, you must be running RSpace 1.73 or later.
This is so that database updates can be performed with the correct version of the code.
Please read the changelogs for all intermediate versions and apply **all** mandatory changes and any optional changes you require.

If updating from a version earlier than 1.73.0, please read the Changelogs for the relevant previous
versions (in the "previous_changelogs" directory) **before** updating.

If you are updating from an earlier version of RSpace, please follow the upgrade path below:

    | Updating to |   Oldest version to update from  | Oldest Compatible Document converter
    1.73.3               1.69.32                           0.18.1
    1.98.0               1.73.3                            0.18.1

All recent downloads are available from our download site:

    1.73.3  https://operations.researchspace.com/software/rspace/rspace-1.73.3.zip
    1.90.0  https://operations.researchspace.com/software/rspace/rspace-1.90.0.zip
    1.91.1  https://operations.researchspace.com/software/rspace/rspace-1.91.1.zip
    1.92.1  https://operations.researchspace.com/software/rspace/rspace-1.92.1.zip
    1.93.0  https://operations.researchspace.com/software/rspace/rspace-1.93.0.zip
    1.94.1  https://operations.researchspace.com/software/rspace/rspace-1.94.1.zip
    1.95.1  https://operations.researchspace.com/software/rspace/rspace-1.95.1.zip
    1.96.3  https://operations.researchspace.com/software/rspace/rspace-1.96.3.zip
    1.97.5  https://operations.researchspace.com/software/rspace/rspace-1.97.5.zip

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

#### Deployment property 'jdbc.charset' renamed to 'jdbc.connectionProperties'

We've changed the name of a deployment property 'jdbc.charset' to 'jdbc.connectionProperties' to correctly describe its function. 

The property had an empty default value by default, but it could have been overridden in your installation. 
You mmust check your deployment.properties file, and if it defines 'jdbc.charset' property, change it to 'jdbc.connectionProperties'.
Failing to do so may cause problems with UTF characters encoding across the application.

### Optional changes

#### Deployment property 'pdffont.dir' no longer used

The property was previously used for dynamically loading non-standard font files that could be used during PDF export.
Since RSpace 1.98 we're using a different font loading mechanism, and the required font files are now bundled within RSpace .war package.
Therefore, 'pdffont.dir' deployment property is no longer used, and can be removed. 

#### More change to PyRAT integration (only for customers using PyRAT)

In RSpace 1.97.2 we've switched RSpace integration to use version 3 of PyRAT API, rather than version 2, which is obsolete in PyRAT 5.
The integration will expect to connect to endpoints of API v3, so please update the `pyrat.url` to point to `/v3/` rather than `/v2/`. 

If in doubt contact RSpace Support, or check our documentation of RSpace->PyRAT setup: 
https://researchspace.helpdocs.io/article/9kkeooveia-py-rat-integration

### Other notes

No other notes.

#### End of File
