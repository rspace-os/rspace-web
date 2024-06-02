package com.researchspace.document.importer;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.DocumentAlreadyEditedException;
import java.io.IOException;

/** Creates an RSpace document from a {@link ContentProvider}. */
public interface RSpaceDocumentCreator {

  /**
   * Creates a new record from a {@link ContentProvider}
   *
   * @param provider A {@link ContentProvider} that can provide content for a text document
   * @param targetFolder The {@link Folder} in which the new documetn will be placed
   * @param imageFolder The {@link Folder} in which images extracted from the Word doc will be
   *     place, can be <code>null</code> or else
   * @param origDocName The original name of the source
   * @param creator The subject
   * @return the newly created {@link BaseRecord}
   * @throws IOException
   */
  BaseRecord create(
      ContentProvider provider,
      Folder targetFolder,
      Folder imageFolder,
      String origDocName,
      User creator)
      throws IOException;

  /**
   * Replaces content of an existing document with content supplied from the {@link ContentProvider}
   *
   * @param toReplaceId the document with content to replace
   * @param provider
   * @param origDocName
   * @param user
   * @return the updated document
   * @throws IOException
   * @throws DocumentAlreadyEditedException
   */
  BaseRecord replace(Long toReplaceId, ContentProvider provider, String origDocName, User user)
      throws IOException, DocumentAlreadyEditedException;
}
