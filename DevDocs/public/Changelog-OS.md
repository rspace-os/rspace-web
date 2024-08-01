This document records significant changes to RSpace.
It includes a summary of new features, bugfixes and server-side configuration changes.
The intended audience is on-prem RSpace technical administrators who maintain RSpace.

You can find official changelog at https://documentation.researchspace.com/article/mx11qvqg0i-changelog 

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
