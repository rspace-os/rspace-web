This document records significant changes to RSpace.
It includes a summary of new features, bugfixes and server-side configuration changes.
The intended audience is on-prem RSpace technical administrators who maintain RSpace.

You can find official changelog at https://documentation.researchspace.com/article/mx11qvqg0i-changelog

# 2.12.0 2025-07-15

### ELN Features

- RSDEV-681 new integration with PubChem, can be used by users who enabled 'Chemistry' App
- RSDEV-644 incoming messages count is added to Navbar, so it can be accessed from every page
- RSDEV-691 users can now trigger document creation inside shared notebooks
- RSDEV-684 chemical search dialog explains that only searching for a single molecule is supported

### ELN Bugfix

- RSDEV-729, RSDEV-733 performance fixes around opening notebooks, and navigating between notebook entries

### Inventory Features

- RSDEV-631 pre-created IGSN IDs can be now linked with CSV import

### Inventory Bugfix

- RSDEV-694 fix problem with Container tags not being saved during CSV import
- RSDEV-709 fix problem with 'previous location' column

### Contributed by open-source community

- ISSUE#232 align RSpace API and UI when retrieving audit trail events (contributed by richarda23)

# 2.11.0 2025-06-06

### ELN Features

- RSDEV-653 new System Setting for enabling/disabling OAuth authentication for RSpace API
- RSDEV-664 extended logging around usage of RSpace API, including new 'ApiEvents' file that tracks RSpace API interactions

### ELN Bugfix

- RSDEV-649 fix problem with 'delete notifications' action being very slow in some scenarios

### Inventory Features

- RSDEV-687 new "Identifiers" page for managing IGSN IDs, openable with button on Inventory sidebar
- RSDEV-676 clearly marking "Name" as a required field when creating a new Sample

# 2.10.0 2025-05-13

### ELN Features

- RSDEV-268 reordered setting on System -> Configuration page
- RSDEV-242 allow PIs to move shared documents into shared notebooks

### ELN Bugfix

- RSDEV-641 prevent user from inadvertently creating notebooks more than once
- RSDEV-595, RSDEV-606, RSDEV-665 various fixes around chemistry files processing and chemical search

### Inventory Features

- RSDEV-636 allow printing two copies of each barcode (raffle-book style) with a label printer

# 2.9.0 2025-04-10

### ELN Features

- RSDEV-438 (also #230) creation of documents/notebooks can be now triggered from within Shared folder
- RSDEV-394 various improvements around breadcrumbs displayed on new Gallery page

### ELN Bugfix

- RSDEV-613 fix rare problem where duplication of document that is shared into notebook could result in copy being added to the notebook
- RSDEV-549 disable spellcheck on various text fields
- RSDEV-619 fix link to 3rd party 'Buffer Calculator'

# 2.8.0 2025-03-07

### ELN Features

- RSDEV-508 new Gallery supports viewing of .dna files through SnapGene integration
- RSDEV-487 group invitation email updated with links that allow todirectly Accept/Decline the invitation
- RSDEV-557, ISSUE#227 'Usage' column on System -> Users listing is formatted as number in CSV export

### ELN Bugfix

- RSDEV-527 fix problems with exporting to repository when DMPonline integration was enabled
- RSDEV-528 fix problem with DMP from dmptool not being updated with a link to exported RSpace archive
- RSDEV-507 add scrolling to List of Materials selector
- RSDEV-557 fix rare UI problem where after period of inactivity user was navigated to a page with printout of json data

# 2.7.0 2025-02-11

### ELN Features

- RSDEV-451 new Gallery UI is now a default option (old Gallery still available at /oldGallery)
- RSDEV-297 new Gallery supporting URL-encoded paths
- RSDEV-423 new Navigation UI (replacing "tabs" on the top of page)
- RSDEV-435 PyRAT integration now supports connecting to multiple PyRAT servers (note: requires deployment property change, check the 1.106-to-1.107 changelog)
- RSDEV-347 attachments in restored documents are now clearly marked as historical versions
- RSDEV-362 persisting columns selection on System -> Users page 

### ELN Bugfix

- RSDEV-455 GitHub integration now recognize default branch (rather than returning 404)
- RSDEV-488 fix problem with public API /folder/tree endpoint returning 500 in some cases
- PRT-872: fix handling of oauth token after RSpace identity is changed within SSO session

### Contributed by open-source community

- #39 iRODS file system setup UX (contributed by ll4strw)
- #111 additional iRODS client/server negotiation policy configuration (contributed by ll4strw)

# 2.6.0 2025-01-10

### ELN Features

- RSDEV-359 DMPOnline integration with new oauth authentication flow
- RSDEV-445 improved performance of sharing/autosharing with group(s) having a lot of users

### ELN Bugfix

- RSDEV-425 more robust permission checks when sharing, to stop the possibility of sharing with unrelated users/groups

### Inventory Features

- RSDEV-294, RSDEV-342, RSDEV-434 various updates to 'Browse Gallery' dialog, including new 'Filestores' section

# 2.5.0 2024-12-06

### ELN Features

- RSDEV-274 new Workspace action to batch-compare ELN documents, with optional CSV export

### ELN Bugfix

- RSDEV-425 update OneDrive integration to make it work with latest OneDrive API
- RSDEV-418 fix delivery of notifications for some types of PDF export problems
- RSDEV-411 fix problem with PDF export of documents having html-entity char sequence in document name

### Inventory Features

- RSDEV-372, #193 Fieldmark integration
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
- RSDEV-308 UX improvements around subsample deletion
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
