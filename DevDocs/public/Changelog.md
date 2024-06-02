This document records significant changes to RSpace.
It includes a summary of new features, bugfixes and server-side configuration changes.
The intended audience is on-prem RSpace technical administrators who maintain RSpace.

## 1.99.1 2024-05-24

### ELN Features

- RSDEV-198 log4j logging library upgraded to latest

# 1.99.0 2024-05-16

### ELN Features

- RSDEV-155 allow uploading Gallery files to connected iRODS filestore location 
- RSDEV-197 add iRODS branding to filestore links inserted into document 
- RSDEV-105 old version of Apps page is now removed

### Inventory Features

- RSDEV-207 if item cannot be saved, the 'Save' button shows the explanation, rather than being disabled  

## 1.98.2 2024-05-22

### ELN Bugfix

- RSDEV-231 fix problem with viewing MS Office documents other than MS Word

## 1.98.1 2024-04-18

### ELN Bugfix

- RSDEV-190 fix problem with PDF export of documents containing unresolvable embedded image 

### Inventory Bugfix

- PRT-778 fix problem with displaying Preview Image of the item

# 1.98.0 2024-04-12

### ELN Features

- RSDEV-160 re-implemented PDF export feature uses different back-end library and bundled fonts
- RSDEV-132 improving audit trail for "duplicate" action

### ELN Bugfix

- RSDEV-142 fix occasional problem with autosharing of duplicated documents. This is a wider fix
that may solve various rare problems with Group permissions
- RSDEV-170 fix export to Dryad repository
- RSDEV-185 add missing Audio Gallery icon to a sidebar of a Gallery page

### Inventory Features

- RSDEV-80 add "Sample fields" section to Subsample details view

### Inventory Bugfix

- RSDEV-14 fix occasional problem with operation on multiple items throwing incomprehensible 'batch update returned unexpected new count' error
- RSDEV-158 fix setting of a preview image from custom Attachment field

## 1.97.5 2024-04-05

### ELN Bugfix

- RSDEV-180 on group details page, show 'pending invitations' to all users administering the group 

## 1.97.4 2024-03-25

### Inventory Bugfix

- RSDEV-127 fix ELN API error when initializing barcodes on some documents that use List of Materials

## 1.97.3 2024-03-20

### ELN Bugfix

- RSDEV-149, PRT-764 Various fixes to new UI on System -> Users page

## 1.97.2 2024-03-18

### ELN Features

- RSDEV-134 PyRAT integration now connecting to PyRAT API v3 (previously: v2)

### ELN Bugfix

- PRT-760, PRT-761 Various fixes to new UI on System -> Users page

## 1.97.1 2024-03-08

### Inventory Bugfix

- PRT-759 Fix a page error appearing after hovering over 'info' icon of an attachment  

# 1.97.0 2024-03-08

### ELN Features

- RSDEV-102 New UI for System -> Users page, which allows tagging of users and export to CSV 
- RSDEV-57 Document 'rename' action is now visible in audit trail 
- RSDEV-130 New API endpoints to allow sysadmin to disable/enable the users 

## 1.96.5 2024-03-21

### ELN Features

- RSOPS-542: renewed Chemaxon license

## 1.96.4 2024-03-18

### ELN Bugfix

- RSDEV-156 Fix problem that was blocking removal of some users from LabGroups created before 1.94 release

## 1.96.3 2024-03-06

### ELN Bugfix

- RSDEV-141 Fix problem with Google login/signup on RSpace Community server

## 1.96.2 2024-03-01

### ELN Bugfix

- TSK-25 Fix sporadic problem with assigning Lab Admin role to a LabGroup member   

## 1.96.1 2024-02-14

### ELN Features

- RSDEV-103 PyRAT integration modified to communicate with PyRAT server through RSpace, rather than directly by the browser 

# 1.96.0 2024-02-08

### ELN Features

- RSDEV-98 Tagging mechanism extended to folders and notebooks
- RSDEV-61 Experimental integration with DMPonline service

### Inventory Features

- RSDEV-76 Changes to public page for published IGSN identifier

## 1.95.1 2024-01-15

### ELN Bugfix

- RSDEV-93 Argos integration: fix missing scrollbar in DMP import dialog
- RSDEV-95 DMPTool integration: fix missing scrollbar in DMP import dialog

# 1.95.0 2024-01-08

### ELN Features

- RSDEV-55 eCAT integration is now removed from Apps page
- RSDEV-70, RSDEV-74, RSDEV-78 various performance improvements to Workspace loading time and folder navigation

### ELN Bugfix

- RSDEV-69 Zenodo integration: update deposit code so it works with latest Zenodo API

### Inventory Features

- RSDEV-16 IGSN geolocations: integrate with OpenStreetMaps to visually display provided coordinates
- RSDEV-75 Republish button for IGSNs
- RSDEV-13 Published IGSNs now contain organization details set through ROR

### Inventory Bugfix

- RSDEV-71 Subsample listing was not filtering results correctly in some scenarios

## 1.94.1 2023-12-04

### ELN Bugfix

- RSDEV-65 Fix potential problem with refactored database permission model, which could block 1.94 update

# 1.94.0 2023-11-23

### ELN Features

- RSDEV-5 Allow sysadmin to configure ROR details through System -> Settings screen
- RSDEV-39 PyRAT integration: show useful error when pagination is not available due to CORS
- RSDEV-22 Refactor permission model used on database level, for better performance in some scenarios

### ELN Bugfix

- RSDEV-39 In revision history, 'rename' action was always pointing to document's owner, rather than to renaming user

### Inventory Features

- RSDEV-19 Further work on IGSN geolocations (polygon geolocations)

### Inventory Bugfix

- RSDEV-50 "Contents of" search filter sometimes gets stuck
- RSDEV-53 Choosing a tag does not update URL
- RSDEV-56 Default page size is 10, but 20 results are loaded

# 1.93.0 2023-10-19

### ELN Features

- RSPAC-2821 Extend permission caching to improve Workspace loading time in some setups
- RSPAC-2588 Let user managed their global PI role if a specific flag is set in SSO environment

### Inventory Features

- RSINV-871 Add various geolocation inputs when preparing IGSN for publishing with DataCite
- RSINV-878 Simplify UX for DataCite configuration

## 1.92.1 2023-09-20

### ELN Features

- RSPAC-522 Add pagination to 'Deleted Items' page

# 1.92.0 2023-09-14

### ELN Features

- RSPAC-2708 when exporting a tagged archive to repository, auto-populate tags used in exported content

### ELN Bugfix

- RSPAC-2739 fix protocols.io integration problem with listing private protocols
- RSPAC-2778 correct formatting of choice fields printout when exported to pdf/word/html
- RSPAC-2768 all members of Project Group can now organize group's Shared folder

### Inventory Features

- RSINV-873 allow tagging items with tags coming from controlled vocabulary
- RSINV-856 allow sysadmin to configure DataCite connection through new Inventory-> Settings screen

### Inventory Bugfix

- RSINV-875 fix subsample colouring in grid containers

## 1.91.2 2023-09-12

### Inventory Bugfix

- RSINV-875 restore coloured location border indicating sample

## 1.91.1 2023-08-24

### ELN Bugfix

- RSPAC-2808 don't show BioPortal Ontology tag suggestion to users who are not in any group

# 1.91.0 2023-08-17

### ELN Features

- RSPAC-2767 for tag ontologies, capture metadata and URIs on CSV import, then show this metadata in UI
- RSPAC-2741 include tags as vocabulary term URIs when exporting RSpace archive to repository
- RSPAC-2794 allow tag suggestions to come from BioPortal Ontologies service

### ELN Bugfix

- RSPAC-2777 fix problem with date format being reset when editing Date field on ELN Form
- various fixes around repository export workflow with DMPTool/Dryad/Dataverse

### Inventory Features

- RSINV-831 early-stage integration with DataCite IGSNs (currently only working with Test DataCite API)

### Inventory Bugfix

- RSINV-862 fix problem with scrolling content of large grid containers

# 1.90.0 2023-07-06

### ELN Features

- RSPAC-2627 integration with OMERO open microscopy environment

### ELN Bugfix

- RSPAC-2761 when re-importing exported ELN documents apply original 'last modified' dates
- RSPAC-2710 customizable 'no account' message for SSO environments

## 1.89.2 2023-06-08

### ELN Features

- RSPAC-2751 pressing 'slash' when editing a text field opens menu with insert options
- RSPAC-1593 support pasting of images from clipboard directly into text field

## 1.89.1 2023-06-05

### Inventory Bugfix

- RSINV-847 fix handling of partially-populated barcodes created directly through API

# 1.89.0 2023-06-01

### ELN Features

- RSPAC-2649 introducing 'project groups' - new type of group that allows sharing, but doesn't have a PI

## 1.88.1 2023-05-08

### ELN Bugfix

- RSPAC-2738 fix protocols.io integration

# 1.88.0 2023-04-27

### Inventory Features

- RSINV-765 batch edit of consumed amount on list of materials connected to ELN document
- RSINV-820 modified UI for setting storage temperature of the sample

### ELN Features

- RSPAC-2675 integration with Zenodo repository

## 1.87.3 2023-04-17

### ELN Bugfix

- RSPAC-2727 improve performance of messaging and requests, especially group invitations
- RSPAC-2731 fix server-side caching issue that caused updates to Apps page being ignored until cache expired

## 1.87.2 2023-04-14

### ELN Bugfix

- RSPAC-2732 fix problem with displaying shared snippets in Workspace listing
- RSPAC-2733 fix performance problem with API /documents endpoint

## 1.87.1 2023-03-30

### ELN Features

- RSPAC-2422 switch to new Google API for login/signup with Google on RSpace Community version

### Inventory Bugfix

- RSINV-520 set of fixes/improvements around new 'Create' dialog

# 1.87.0 2023-03-23

### Inventory Features

- RSINV-410 Allow creating samples directly within containers

### ELN Features

- RSPAC-2674 integration with Argos tool

## 1.86.6 2023-03-08

### ELN Bugfix

- RSPAC-2706 reduce logged severity level of a known permissionString parsing issue

## 1.86.5 2023-03-03

### Inventory Bugfix

- RSINV-520 set of fixes/improvements around new 'Create' dialog

### ELN Features

- RSPAC-2715: allow disabling specific memory caches by setting their size to '0' in deployment properties

## 1.86.4 2023-03-01

### ELN Features

- RSOPS-463: renewed Chemaxon license

## 1.86.3 2023-02-27

### ELN Bugfix

- RSPAC-2368 correct chemical attachment display in notebook view

## 1.86.2 2023-02-24

### ELN Bugfix

- PRT-693 correct tinyMCE issue caused by upgrade on Inventory pages

# 1.86.1 2023-02-23

### Inventory Features

- RSINV-520 Allow creating containers directly within other containers
- RSINV-543 tinyMCE upgrade (5.5.1 -> 6.3.1, Inventory pages only)

### ELN Bugfix

- RSPAC-2673 fix an error appearing on first refresh after long inactivity in SSO environment
- RSPAC-2691 fix an error appearing during scheduled maintenance in SSO environment

## 1.85.1 2023-02-06

### Inventory Bugfix

- RSINV-791 Fix grid container size menu selection

# 1.85.0 2023-01-30

### Inventory Features

- RSINV-734 More volume/mass unit options for sample templates
- RSINV-763 Pin item heading when scrolling

### ELN Features

- RSPAC-2665 Form fields can be marked as 'required'

## 1.84.3 2023-01-17

### ELN Bugfix

- RSPAC-2699 Gallery page 'share' action should be only available for snippets

## 1.84.2 2023-01-16

### ELN Bugfix

- RSPAC-2697 Fix for 'share' button not working in document/notebook view

## 1.84.1 2022-12-13

### ELN Bugfix

- RSPAC-2683 Fix for individual snippet sharing not working for some users

# 1.84.0 2022-12-09

### Inventory Features

- RSINV-741 Make right-hand panel collapsible, either on click or conditionally
- RSINV-732 Image attachment can be used as a preview image with one-click action
- RSINV-743 Enhance card view for a larger preview image and more metadata
- RSINV-491 Improve performance of workbench/container content listing

###  ELN Features

- RSPAC-2660 Gallery Snippets can be shared with user's groups
- RSPAC-2667 Radio field can be now displayed as a picklist, with autocomplete supported
- RSPAC-2651 Handle chemistry file attachments containing R-Groups or Markush structures

## 1.83.1 2022-11-21

### Inventory Features

- RSINV-714 Permission system should allow Lab Admin with view all permission to access  group items same way as PI

### ELN Bugfix

- RSPAC-2595 Fix for navigating from Workspace search result directly to Gallery item

# 1.83.0 2022-11-10

### Inventory Features

- RSINV-705 Limiting item details visible in some scenarios where user doesn't have full permission to the item
- RSINV-212 Scanning barcode of any inventory item displays its name and owner even if user has no permissions to that item
- RSINV-715 Permission system extended to Sample Templates to allow sharing them with any group

###  ELN Features

- RSPAC-2587 iRODS integration (Gallery filestore connector)
- RSPAC-2618 previews available for chemical structures in Gallery

## 1.82.4 2022-10-19

### ELN Features

- RSPAC-2603 Ontology files import handling more cases

## 1.82.3 2022-10-18

### ELN Bugfix

- TSK-43 Fix problem blocking the update to 1.81/1.82 on servers that still run MySQL 5.6 database

## 1.82.2 2022-10-12

### ELN Bugfix

- RSPAC-2626 RSpace Community: fix error on attempt to share a document

## 1.82.1 2022-10-12

### ELN Bugfix

- RSPAC-2608 Date chooser in ELN document doesn't update date-field content

# 1.82.0 2022-10-06

### Inventory Features

- RSINV-235 Reworked permission system allows sharing items with particular groups
- RSINV-691 Barcode printing now supports specialized label printers, e.g. Zebra

###  ELN Features

- RSPAC-2602 Reworked tagging system allows key-value pairs and controlled vocabularies

## 1.81.2 2022-09-16

### Inventory Features
- RSINV-516 Generate detailed audit trail event when inventory items are associated with ELN experiment

# 1.81.1 2022-09-09

### Inventory Features

- RSINV-367 Allow user to create lists of items ("baskets")
- RSINV-588 Collapsible sections in item details panel
- RSINV-113 Allow users to see a list of experiments that are using a particular item
- RSINV-688 Way to list all connected inventory items on RSpace document page

###  ELN Features

- RSPAC-2460 Public sharing of documents and notebooks
- RSPAC-2546 Dryad Integration
- RSPAC-2309 Support more scenarios for LDAP authentication

## 1.80.3 2022-08-17

###  ELN Features and Improvements

- RSPAC-2578 Nextcloud integration: resource link now opens files on remote server

## 1.80.2 2022-08-11

###  ELN Features and Improvements

- RSPAC-2578 Nextcloud integration

## 1.80.1 2022-08-08

### Inventory Bugfix

- PRT-575 Improve displaying barcode data before navigating
- PRT-571 Barcode print options sometimes overflow label boundary
- PRT-566 Unexpected results when searching by a URL

### ELN Bugfix

- SUPPORT-481 Update error handling in PI group creation workflow

# 1.80.0 2022-08-04

### Inventory Features

- RSINV-677 Allow barcodes printing
- RSINV-684 Support barcodes in batch edit
- RSINV-681 Default barcode should encode global URL of the item rather than global ID

### ELN Bugfix

- RSPAC-2580 - Uploading large number of files to the Gallery may result in out-of-memory error

## 1.79.2 2022-07-19

### Inventory Bugfix

- PRT-558 Scrolling using mouse middle wheel is broken on Safari

### ELN Bugfix

- PRT-559 Delete icon in notebook toolbar broken on Safari

## 1.79.1 2022-07-13

### Inventory Bugfix

- PRT-545 fix creation of samples with mandatory radio/choice fields

### ELN Bugfix

- Fix initialisation of jQuery code in toolbars in Safari

# 1.79.0 2022-07-07

### Inventory Features

- RSINV-24 Manage barcodes connected to inventory items
- RSINV-643 Be able to scan barcodes from inside Inventory UI
- RSINV-657 Workflow for scanning 1-d barcodes (and other non-QR formats) with a camera
- RSINV-633 Export generates a zip file with multiple CSV files, rather than one combined CSV file

### ELN Bugfix

- RSPAC-2559 Duplicating gallery folders creates broken content
- RSPAC-2560 Chemical file in gallery cannot be duplicated
- RSPAC-2554 Database constraints blocking user account deletion

# 1.78.0 2022-05-26

### ELN Features and Improvements

- RSPAC-2480 JoVE integration for linking article/video into RSpace document
- RSPAC-2416 ListOfMaterials button visual changes

### ELN Bugfix

- RSPAC-2544 User listings of Communities are not correct

### Inventory Features

- RSINV-612 Allow CSV export of Containers, with the option to also include all of the content.
- RSINV-645 Allow CSV export of List of Materials connected to ELN document
- RSINV-650 Allow CSV export of Sample Templates
- RSINV-484 Ability to mark selected template fields as mandatory

## 1.77.4 2022-05-12

### ELN Bugfix

- SUPPORT-247 Change location of export-import page to avoid name conflict in a particular tomcat setup

## 1.77.3 2022-05-09

### Server Bugfix

- RSPAC-2542 Fix false negative assertion for presence of 'admin' user on server start-up.
- RSPAC-2543 Stop a database migration script from being re-run.

## 1.77.2 2022-05-05

### Inventory Bugfix

- PRT-472 Creating a sample from a template with fields doesn't show those fields

### ELN Bugfix
- RSPAC-2527 Clustermarket integration recognizing server maintenance mode

## 1.77.1 2022-05-03

### Inventory Features

- RSINV-627 Exported CSV starts with comments summarizing export action

# 1.77.0 2022-04-28

### ELN Features and Improvements

- RSPAC-2482 PIs can create new LabGroups
- RSPAC-2455 Text file attachments in Markdown format can be previewed in-browser
- RSPAC-2513 Clustermarket integration enhancements
- RSPAC-2506 Improved use of vector fonts in PDF exports of mathematical and technical characters. Requires installation of fonts.
- RSPAC-1519 Improved explanation to the purpose of verification password

### ELN Bugfix
- RSPAC-2445 Some filenames containing some Unicode whitespace encodings were not escaped properly.
- RSPAC-2500 Collaboration Group profile page gave inaccurate listings of member LabGroups. This is replaced with a list of PIs.
- RSPAC-2529 A Community Admin (CA) could escalate their role to 'Sysadmin' using 'Operate As'. Now, CA can only operate as
     User or PI.

### Inventory Features

- RSINV-464 Allow export of Samples and Subsamples to CSV. That could be an export of selected items, or all items belonging to the user
- RSINV-249 During CSV import, allow importing into pre-existing Containers, and importing Subsamples into pre-existing Samples
- RSINV-616 During CSV import allow importing of Subsample's quantity
- RSINV-188 Add template search component on Sample creation screen
- RSINV-607 Allow creating a new sample directly from a selected template

### Inventory Bugfix

- RSINV-620 various issues with displaying content of text fields on item details page

### Server

- RSPAC-2521 Removal of obsolete backup tables generated during 1.69->1.70 migration
- RSPAC-2515 Explicit setting of collation of utf8mb4 database tables to utf8mb4_unicode_ci


## 1.76.8 2022-04-22

### ELN Bugfix

- RSPAC-2533 possible fix for occasional ClassDefNotFoundError loading image plugins

## 1.76.7 2022-04-20

### ELN Bugfix

-  RSPAC-2523 enable creation of additional Community Admin accounts on SSO systems configured with /adminLogin page

## 1.76.6 2022-04-15

### Inventory Bugfix

-  RSINV-625 fix ELN API error on retrieving documents that have List of Materials

## 1.76.5 2022-04-15

### ELN Bugfix

-  RSPAC-2481  Broken external image links caused export to hang

## 1.76.4 2022-04-07

### ELN Bugfix

-  RSPAC-2520 fix Open with Office button regression

## 1.76.3 2022-03-30

### ELN Bugfix

- RSPAC-2465 Close tab when custom Collabora button clicked

## 1.76.2 2022-03-29

### ELN Features

- RSPAC-2465 Collabora Integration

## 1.76.1 2022-03-28

### ELN Bugfix

- RSPAC-2157-clustermarket-table-fix-and-api-fix

# 1.76.0 2022-03-23

### ELN Bugfix

- Empty HTML tables could break PDF export; this is now fixed.
- It was possible to join a group multiple times by repeatedly clicking on the 'join group' acceptance button in invitation pop-up; this is now fixed

### Inventory Features

- Import Samples, Subsamples and Containers into Inventory from CSV files. All Inventory types can now be imported into RSpace.
- Imported items now are placed in their own Container on the workbench. This enables imports to be segregated and handled independently of each other. Previously, all imported items were placed directly into the Workbench.
- Improved user interface for setting sample storage temperatures. Previously, this was over-complicated and error-prone.
- A default storage temperature can be set in a Template, so that all samples created from that Template will have that storage temperature set automatically.

### Inventory Bugfix

- Radio / choice field options are now displayed in Template View mode.
- Searches that are based purely on combinations of filters, without a query term, can now be saved and re-used.

### Server

- All configurable deployment properties are logged at startup.
- Slow requests now get logged to a dedicated log file, SlowLogs.txt.
- New deployment property **slow.request.time** A duration, in milliseconds, default = 2000. Requests taking longer than this duration will be logged to SlowRequests.txt
