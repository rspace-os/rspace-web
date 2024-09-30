This document records significant changes to RSpace.
It includes a summary of new features, bugfixes and server-side configuration changes.
The intended audience is on-prem RSpace technical administrators who maintain RSpace.

You can find official changelog at https://documentation.researchspace.com/article/mx11qvqg0i-changelog 

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
