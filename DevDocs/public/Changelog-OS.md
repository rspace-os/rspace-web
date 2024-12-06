This document records significant changes to RSpace.
It includes a summary of new features, bugfixes and server-side configuration changes.
The intended audience is on-prem RSpace technical administrators who maintain RSpace.

You can find official changelog at https://documentation.researchspace.com/article/mx11qvqg0i-changelog 

# 2.5.0 2024-12-06

### ELN Features

- RSDEV-274 new Workspace action to batch-compare ELN documents, with optional CSV export

### ELN Bugfix

- RSDEV-425: update OneDrive integration to make it work with latest OneDrive API 
- RSDEV-418: fix delivery of notifications for some types of PDF export problems   
- RSDEV-411: fix problem with PDF export of documents having html-entity char sequence in document name

### Inventory Features

- RSDEV-314, #193 Fieldmark integration
- RSDEV-339, RSDEV-388, RSDEV-391 new actions and improvements in 'Browse Gallery' dialog

# 2.4.0 2024-11-01

### ELN Features

- RSDEV-284 RSpace can be now deployed in Single-Sign On environment using 'OpenID Connect' protocol
- RSDEV-163 separate deployment properties for analytics and live chat, to allow using support chat without passing analytics data
- RSDEV-378 Apps page describes RSpace integration provided by ascenscia.ai

### ELN Bugfix

- RSDEV-145 XML/HTML export now reports more errors
- RSDEV-344 'move' action in RSpace Gallery now works correctly for folders having non-unique name
- RSDEV-357 more robust support for special characters when using filters on some dialogs

### Inventory Features

- RSDEV-314 User can now create/add new subsamples to an existing Sample
- RSDEV-373 'Gallery' dialog has a pagination mechanism now
- RSDEV-381 'Gallery' dialog supports new 'open' and 'preview' actions
- RSDEV-345 text editor is now displayed inlined, rather than floating

# 2.3.0 2024-09-30

### ELN Features

- RSDEV-267 when sysadmin deletes a user who created shared forms, transfer the forms to sysadmin
- RSDEV-310 users' API keys are now stored hashed on the database, and only displayed plaintext immediately after creation

### Inventory Features

- RSDEV-174 Gallery files can be now attached to Inventory items
- RSDEV-292 new API endpoint for retrieving images/thumbnails by their contentsHash, for better performance
- RSDEV-308 UX improvments around subsample deletion
- RSDEV-340 persisting more of UI state, e.g. single-column toggle

# 2.2.0 2024-08-30

### ELN Features

- RSDEV-221 integration with Digital Commons Data repository
- RSDEV-224 minor visual fixes to Auditing page

### Inventory Features

- RSDEV-257 when deleting a Sample user can decide to force-delete all Subsamples regardless of their location
- RSDEV-304, RSDEV-306, RSDEV-307 more UI/UX changes when displaying Samples that contain just one Subsample

### Inventory Bugfix

- RSDEV-326 more robust validation of Sample date/time fields

## 2.1.1 2024-08-01

### ELN Features

- RSDEV-258 reactivated Google Drive integration
- RSDEV-301, RSDEV-302 enhancements to table inserted with PyRAT integration 

### ELN Bugfix
- it should no longer be possible for sysadmin to see user's API key (https://github.com/rspace-os/rspace-web/issues/73)

# 2.1.0 2024-07-26

### ELN Features

- improved performance of API document search, and of Workspace UI 'View All' listing
- mentions feature in document editor ('@') now shows longer list of users

### ELN Bugfix

- RSDEV-240 PDF export now correctly handle option about restarting page numbering for each document
- RSDEV-273 sysadmin 'delete user' action is now enabled only if the functionality is enabled in deployment properties

### Inventory Features

- RSDEV-260 sample views include details about subsamples
- RSDEV-81, RSDEV-279, RSDEV-282 various UI/UX changes when displaying samples that contain just one subsample
- RSDEV-280 when sample deletion is blocked due to subsamples being in container, list details of these subsamples

Additionally: all rspace-os dependencies required for building 2.1.0 version are now available through jitpack.io.

# 2.0.0 2024-06-26

- first public release of open-source RSpace 

# 2.0.0-RC2 2024-06-25

- all project dependencies updated to versions available at https://github.com/rspace-os/ repos 

# 2.0.0-RC1 2024-06-04

- private pre-release of open-source codebase of rspace-web project 
