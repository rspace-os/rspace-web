package com.researchspace.archive;

import com.researchspace.core.util.version.SemanticVersion;
import java.io.File;
import java.io.IOException;
import java.util.List;

public interface IArchiveModel {

  File getFormScheme();

  File getXmlSchema();

  File getLinkResolver();

  File getManifestFile();

  /**
   * Gets all parsed archived records
   *
   * @return
   */
  List<ArchivalDocumentParserRef> getAllVersions();

  /**
   * Gets the archive items that are exports of the most current revision.
   *
   * @return
   */
  List<ArchivalDocumentParserRef> getCurrentVersions();

  int getTotalRecordCount();

  int getCurrentRecordCount();

  ArchiveManifest getManifest() throws IOException;

  /**
   * Convenience method to get the RSpace version used to generate the archive.
   *
   * @return
   * @throws IOException
   */
  SemanticVersion getSourceRSpaceVersion();

  /**
   * Searches current records for the document with the given name
   *
   * @param srchTerm A name of a document
   * @return A possibly empty but non-null list of documents of the given name.
   */
  List<ArchivalDocumentParserRef> findCurrentDocArchiveByName(String srchTerm);

  List<ArchivalGalleryMetaDataParserRef> getMediaDocs();

  /**
   * Gets the folder tree file
   *
   * @return
   */
  File getFolderTree();

  ImmutableExportRecordList getFolderTreeList();

  /**
   * Gets the number of current (i.e., excluding revision history) documents in the archive
   *
   * @return
   */
  int getCurrentDocCount();

  /**
   * Gets the XML file of user info
   *
   * @return
   */
  File getUserInfo();
}
