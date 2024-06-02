package com.researchspace.service.archive;

import com.researchspace.archive.ArchiveManifest;

/** Enum of rules that may be optional or mandatory to pass. */
public enum ImportValidationRule {
  ZIP_FILE_CAN_UNZIP("The archive should be a zip file, and should unzip fine.", true),
  CHECKSUM_CALCULATED("RSpace needs to be able generate a checksum of the archive.", true),
  MANIFEST_FILE_PRESENT(
      "The archive must contain a manifest file called " + ExportImport.EXPORT_MANIFEST, true),
  MANIFEST_SOURCE(
      "The archive manifest must contain a property '" + ArchiveManifest.SOURCE + "'", true),
  FOLDER_TREE_PRESENT(
      "The archive must contain an XML file of the folder tree called '"
          + ExportImport.FOLDER_TREE
          + "'",
      true),
  CHECKSUM_MATCHES(
      "The checksum of the archive must equal the checksum at the time of archive creation.", true),
  XMLSCHEMA("XML files must conform to their XML schemas", true),
  GENERAL_ARCHIVE_STRUCTURE("The archive should contain all required resources.", true),
  USER_SCHEMA_VERSION_RANGE_OK(
      " The User schema is incompatible with the current database version", true),
  USER_FILE_READABLE("The users.xml  file could not be parsed", true),
  DOC_SCHEMA_VERSION_RANGE_OK(
      " The Document schema of at least one record is incompatible with the current database"
          + " version",
      true),
  FORM_SCHEMA_VERSION_RANGE_OK(
      " The Form schema of at least one form is incompatible with the current database version",
      true),
  FOLDER_SCHEMA_VERSION_RANGE_OK(
      " The Folder schema of at least one form is incompatible with the current database version",
      true),
  FOLDER_FILE_READABLE("The folders.xml  file could not be parsed", true),
  UNKNOWN("The archive cannot have unexpected invalid content", false),
  ARCHIVE_NOT_TOO_NEW("The archive was made from a newer version of RSpace than this one", true);

  public String getDesc() {
    return desc;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  private String desc;
  private boolean mandatory;

  private ImportValidationRule(String description, boolean isMandatory) {
    this.desc = description;
    this.mandatory = isMandatory;
  }
}
