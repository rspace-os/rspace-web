RSpace Installation and update changelog 1.97
=============================================

This document describes configuration changes that should be made before updating RSpace server to 1.97.

**Note**

To update directly to 1.97, you must be running RSpace 1.73 or later.
This is so that database updates can be performed with the correct version of the code.
Please read the changelogs for all intermediate versions and apply **all** mandatory changes and any optional changes you require.

If updating from a version earlier than 1.73.0, please read the Changelogs for the relevant previous
versions (in the "previous_changelogs" directory) **before** updating.

If you are updating from an earlier version of RSpace, please follow the upgrade path below:

    | Updating to |   Oldest version to update from  | Oldest Compatible Document converter
    1.73.3               1.69.32                           0.18.1
    1.97.0               1.73.3                            0.18.1

All recent downloads are available from our download site:

    1.73.3  https://operations.researchspace.com/software/rspace/rspace-1.73.3.zip
    1.90.0  https://operations.researchspace.com/software/rspace/rspace-1.90.0.zip
    1.91.1  https://operations.researchspace.com/software/rspace/rspace-1.91.1.zip
    1.92.1  https://operations.researchspace.com/software/rspace/rspace-1.92.1.zip
    1.93.0  https://operations.researchspace.com/software/rspace/rspace-1.93.0.zip
    1.94.1  https://operations.researchspace.com/software/rspace/rspace-1.94.1.zip
    1.95.1  https://operations.researchspace.com/software/rspace/rspace-1.95.1.zip
    1.96.3  https://operations.researchspace.com/software/rspace/rspace-1.96.3.zip

The latest release version number can be checked at https://operations.researchspace.com/software/rspace/latestVersion.txt

Aspose Word converter:

    0.30.0  https://operations.researchspace.com/software/aspose/aspose-app-0.30.0.zip

For a complete listing and explanation of all deployment properties, please refer to the document `RSpaceConfiguration.md`.

## Server upgrades

### New version of Aspose Document Converter

Note that we released new 0.30.0 version of the Aspose Word converter app, which has new license valid until Jan 2025.

If you're running your own Aspose installation (on docker/through jar app) please switch to new version,
otherwise RSpace users won't be able to use MS Office file conversion capabilities within RSpace.

### Advance notice of Java 17 being required for running RSpace

Starting with 1.94 release, RSpace is compiled and tested with Java 17, with Java 11 backward compatibility flags.

We're planning to stop using Java 11 compatibility flags in Q2 2024, at this point you'll need to
run Tomcat on Java 17, or you won't be able to upgrade to new RSpace version.

## Changes to RSpace configuration

### Mandatory changes

No mandatory changes.

### Optional changes

#### More change to PyRAT integration (only for customers using PyRAT)

In RSpace 1.97.2 we've switched RSpace integration to use version 3 of PyRAT API, rather than version 2, which is obsolete in PyRAT 5.
The integration will expect to connect to endpoints of API v3, so please update the `pyrat.url` to point to `/v3/` rather than `/v2/`. 

If in doubt contact RSpace Support, or check our documentation of RSpace->PyRAT setup: 
https://researchspace.helpdocs.io/article/9kkeooveia-py-rat-integration

### Other notes

No other notes.

#### End of File
