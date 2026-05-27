package com.researchspace.document.importer;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.DocumentAlreadyEditedException;
import java.io.IOException;
import java.io.InputStream;

/** Top-level interface for importing content from external sources into RSpace documents */
public interface ExternalFileImporter {

  /**
   * @param srcFile
   * @param user
   * @param targetFolder Workspace Folder in which to put created RSpace document
   * @param imageFolder Image Gallery solder in which to put images, can be null, in which cas goes
   *     in top-level ImageFolder
   * @param originalName
   * @return
   * @throws IOException
   */
  BaseRecord create(
      InputStream srcFile, User user, Folder targetFolder, Folder imageFolder, String originalName)
      throws IOException;

  /**
   * Replaces content of existing document
   *
   * @param wordFile
   * @param user
   * @param toReplaceId
   * @param originalName
   * @return
   * @throws IOException
   * @throws DocumentAlreadyEditedException
   */
  BaseRecord replace(InputStream wordFile, User user, Long toReplaceId, String originalName)
      throws IOException, DocumentAlreadyEditedException;
}
