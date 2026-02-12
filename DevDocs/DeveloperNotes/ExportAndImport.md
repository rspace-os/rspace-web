 Export and Import need to handle a) export into doc/pdf/html b) export as xml
 
Option a) only needs enough data to display some content whereas export to xml is to be used as a backup 
or as a means of data migration between RSpace instances (for example - customer migration from the
Community Server to a paid RSpace instance). Option b) therefore requires all data associated to be
exported as well.

Import of RSpace Archives must migrate the data in the archive to new instances in the RSpace DB (if appropriate: see below
for example with External Data where this is not appropriate). All linked data being imported must refer to the new RSpace instances.
For example, we import a document containing fields and one field contains an RSChemElement. The exported Archive contains data
for each of these and their relationship. The import mechanism has to create a new document which is a copy
of the one in the archive except in database IDs. with a new field which contains a new RSChemElement.

In some instances new data does not need to be created in the importing RSpaceDB: for example if ExternalData is exported.
Since ExternalData refers to something outside RSpace, if the importing RSpaceDB already contains this ExternalData, a new
must not be made on import. For this to work, external data (and any data that should be unique in an RSpaceDB) needs to
have a natural database key, eg a business key. An example is ExternalWorkFlow, which has the business key of the externalID
and the workflow name. When an ExternalWorkFlow is imported, we first look for an existing item in the DB and user that. If one
does not exist, we create a new ExternalWorkFlow instance in the DB.

Examples of the code required to export import new data to xml:

Galaxy export: d8cb04959c12bfe5feb49334450b0c26efa2ad17 RSDEV-910

Stoichiometries: RSDEV-948 https://github.com/rspace-os/rspace-web/pull/642

## Export

 - ExportObjectGenerator add a call in `createAssociateFiles` to explicitly import new data. See for example 
`addStoichiometryData` and `addExternalWorkFlowData`. The former calls code which first extracts RTFData from the archive field,
while the latter looks up data directly in the database.
 - You need to create a class that extends AbstractFieldExporter to do the export.
 - `doUpdateLinkText` allows you to modify the RTF data in the associated field. Return the RTF data unmodified if no changes are required
Do not return null or empty string etc because that will then become the value for the field's RTFData
 - `createFieldArchiveObject` this adds data to the ArchiveField - which is the object that is serialised to xml and then deserialised
during import. ArchiveField has to be modified to hold the new data. You can create a class that extends `ArchiveGalleryMetaData` to
hold the archive data (eg `ArchiveExternalWorkFlowData`) or just use a DTO (eg `StoichiometryDTO`). There appears to be no
difference unless you need all the extra fields inherited from `ArchiveGalleryMetaData`. Check that your export generates appropriate
sections in the exported documentSchema.xsd for your new data.
 - `getReplacementUrl` This is where to implement moving any required data into the export archive - for example `ExternalWorkFlowExporter`
moves EcatMedia files into the archive and the returns the moved file's name.

### ExportRecordList
Data to be exported needs to be added here - an example is the `stoichiometries` list of DTOs. On export, the code checks all exported
data to ensure that its ID is held somewhere in `ExportRecordList`. See `containsStoichiometries` as an example.

### Export of global data
Some data belongs with fields in documents while some is global (ie the instance is not associated with a
particular field in a document). An example of both is in ExternalWorkFlow export. ExternalWorkflows
are stored in `ArchivalDocumentParserRef` and serialised to their own document with its own schema. ExternalWorkFlowData
is stored in `ArchiveExternalWorkFlowData` which is associated with an ArchiveField: see `createFieldArchiveObject` in
`ExternalWorkFlowExporter`. `ArchiveModel` also needs to be altered as this stores the ExternalWorkFlow schema. A new
constant needs to be added to the `ExportImport` class for naming new schema files, see `EXT_WF_SCHEMA`. `XMLArchiveExportManagerServiceImpl`
needs to be altered to write new schema files to the archive, see `writeSchemaFiles`. The actual external workflows file,
called `recordFolder.getName() + "_externalWorkflows.xml"` is written by the `XMLWriter` class, see `marshalExternalWorkFlows`.

## Import

 - Key class in `AbstractImporterStrategyImpl`. Imports that have a global component (and hence need `ArchivalDocumentParserRef`)
can be called from `insertToDatabase`. See `importExternalWorkFlows`. Imports that are purely field based and need GalleryItems etc to have already been imported
go in `setFieldAssociates`. See `importChemElementsAndStoichiometries` or `importEmptyStoichiometries`. These imports use a map
of the IDs of GalleryItems from the Archive and the new IDs as they are imported into the DB, see `Map<String, EcatMediaFile> oldIdToNewGalleryItem`

- `ArchiveParserImpl` needs to be modified if new import documents (eg ExternalWorkFlows) are to be imported and read into the
  `ArchivalDocumentParserRef` class.

# Below is the opinion of the IntelliJ AI Assistant using Claude 4:
### Export and Import Code Architecture Documentation

## Core Architecture

### Export System - ExportObjectGenerator

The `ExportObjectGenerator` serves as the primary class responsible for creating exportable archives from the application's data. While the specific implementation details aren't visible in the provided code, based on the import counterpart, this class likely handles:

#### Key Responsibilities:
- **Data Serialization**: Converting database entities into archival formats
- **File Organization**: Structuring exported data into logical hierarchies
- **Metadata Generation**: Creating archival metadata for documents, gallery items, and associated files
- **Asset Management**: Handling media files, attachments, and other binary assets
- **Reference Resolution**: Maintaining relationships between exported entities

#### Expected Export Process:
1. **Data Collection**: Gather all records, documents, and associated files for export
2. **Relationship Mapping**: Create maps of entity relationships and dependencies
3. **File Preparation**: Copy and organize media files, attachments, and other assets
4. **Metadata Creation**: Generate archival metadata files describing the exported content
5. **Archive Assembly**: Package everything into a structured export format

### Import System - AbstractImporterStrategyImpl

The `AbstractImporterStrategyImpl` class serves as the foundation for all import operations in the system. It implements the Template Method pattern, providing a standardized framework for importing archived data.

#### Key Components and Dependencies:

The class integrates with numerous services to handle different aspects of the import process:

- **RecordManager**: Handles database record operations
- **FolderDao**: Manages folder structures and hierarchies
- **FormManager**: Processes form definitions and templates
- **FieldManager**: Manages document fields and their content
- **MediaManager**: Handles media files and gallery items
- **RSChemElementManager**: Processes chemical structure data
- **StoichiometryService**: Manages chemical stoichiometry calculations
- **InternalLinkDao**: Handles internal document links
- **ExternalWorkFlowDataManager**: Manages external workflow integrations
- **RichTextUpdater**: Updates rich text content and references

#### Core Import Workflow:

The import process follows this general pattern:

1. **Archive Parsing**: Parse the imported archive structure and metadata
2. **Validation**: Validate import configuration and permissions
3. **Folder Structure Creation**: Recreate the folder hierarchy in the target system
4. **Gallery Import**: Import media files and gallery items first (as they're referenced by documents)
5. **Document Processing**: Process and import documents with their fields
6. **Reference Resolution**: Update all internal links and references
7. **Finalization**: Complete the import and generate reports

#### Detailed Import Process:

##### Phase 1: Gallery Items Import
```
java
private Map<String, EcatMediaFile> importGalleryItems(
List<ArchivalGalleryMetaDataParserRef> galleryMetaData,
Map<Long, Folder> oldIdToNewFolder,
ProgressMonitor monitor,
int numElements,
ImportArchiveReport report,
User importingUser)
```
This method:
- Processes gallery metadata from the archive
- Creates new `EcatMediaFile` entities in the database
- Handles file format conversions (e.g., TIF to PNG)
- Maps old gallery item IDs to new ones for reference resolution

##### Phase 2: Document Import
```
java
private void insertToDatabase(
ArchivalDocumentParserRef ref,
User importingUser,
Map<Long, Long> formMap,
ArchivalLinkRecord linkRecord,
Map<Long, Folder> oldIdToNewFolder,
ImportArchiveReport report,
RecordContext context,
Map<String, EcatMediaFile> oldIdToNewGalleryItem,
ArchivalImportConfig iconfig)
```
This comprehensive method handles:
- Document metadata restoration
- Form association and creation
- Field content processing
- Asset linking and reference updates

##### Phase 3: Field Processing

The system processes various field types through specialized methods:

- **Text Fields**: `convertStructuredDocumentField()` - Handles rich text content
- **Images**: `importImages()` - Processes embedded images and references
- **Attachments**: `importAttachments()` - Handles file attachments
- **Audio/Video**: `importAudio()`, `importVideo()` - Processes multimedia content
- **Chemical Structures**: `importChemElementsAndStoichiometries()` - Handles chemical data
- **Mathematical Expressions**: `importMath()` - Processes mathematical notation
- **Sketches**: `importSketches()` - Handles drawing/sketch data
- **Comments**: `importComments()` - Processes field-level comments

##### Phase 4: Reference Resolution

The import system handles several types of references:

1. **Internal Links**: Links between documents within the system
2. **Gallery References**: Links to imported media files
3. **External Workflows**: Connections to external systems
4. **Field Attachments**: Associations between fields and their attachments

#### Import Configuration and Overrides

The system supports flexible import configurations:
```
java
private ImportOverride getArchivalDocImportOverride(ArchivalDocument archivalDoc)
private ImportOverride getGalleryMetaImportOverride(ArchivalGalleryMetadata galleryMeta)
```
These methods determine how to handle conflicts during import:
- **OVERWRITE**: Replace existing content
- **SKIP**: Skip conflicting items
- **RENAME**: Create new items with modified names

#### Error Handling and Reporting

The import system provides comprehensive error handling:

- **Progress Monitoring**: Real-time progress updates during import
- **Import Reports**: Detailed reports of import operations and any issues
- **Transaction Management**: Ensures data consistency during import operations
- **Validation**: Validates data integrity and user permissions

#### Template Method Implementation

The abstract class defines a template method that concrete implementations must follow:
```
java
public final void doImport(
User importingUser,
IArchiveModel archiveModel,
ArchivalImportConfig iconfig,
ArchivalLinkRecord linkRecord,
ImportArchiveReport report,
RecordContext context,
ProgressMonitor monitor)
```
Concrete subclasses must implement:
- `getMonitorMessage()`: Provides progress monitoring messages
- `doDatabaseInsertion()`: Handles the actual database insertion logic

## Data Flow

### Export Flow (Conceptual)
```

Application Data → ExportObjectGenerator → Archive Structure → Export File
```
### Import Flow (Actual)
```

Import File → Archive Parser → AbstractImporterStrategyImpl → Database Restoration
```
The import process maintains referential integrity by:
1. Creating ID mapping tables for old-to-new entity relationships
2. Processing dependencies in the correct order (folders → gallery → documents → references)
3. Updating all references after entity creation

## Key Features

### Flexible Import Strategies
- Multiple concrete implementations can extend `AbstractImporterStrategyImpl`
- Support for different archive formats and import scenarios
- Configurable import behavior through `ArchivalImportConfig`

### Comprehensive Content Support
- Documents with rich text fields
- Media files (images, audio, video)
- Chemical structures and stoichiometry data
- Mathematical expressions
- File attachments and annotations
- Comments and metadata

### Reference Preservation
- Internal document links maintained across import
- Gallery item references updated automatically
- External workflow connections preserved
- Field-level associations maintained

### Progress Monitoring and Reporting
- Real-time progress updates during long-running operations
- Detailed import reports with success/failure information
- Error handling with rollback capabilities

## Configuration Options

The system supports various configuration options through:
- `ArchivalImportConfig`: Controls import behavior and overrides
- `@Value` annotations: External configuration properties
- User permissions and context: Security and access control
