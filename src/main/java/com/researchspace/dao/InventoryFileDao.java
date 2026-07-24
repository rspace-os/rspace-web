package com.researchspace.dao;

import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import java.util.List;

/** For DAO operations on Inventory File. */
public interface InventoryFileDao extends GenericDao<InventoryFile, Long> {

  InventoryFile getWithInitializedFields(Long id);

  /**
   * All live (non-deleted) inventory-file attachments of the given Gallery media file. Includes
   * both record-level and field-level attachments; the caller resolves each attachment's owning
   * inventory record (field-level attachments resolve to null here and are found via {@link
   * #findAttachmentFieldsByMediaFileId}).
   *
   * @param mediaFileId the db id of the Gallery {@code EcatMediaFile}
   */
  List<InventoryFile> findByMediaFileId(Long mediaFileId);

  /**
   * The live (non-deleted) sample/template attachment fields holding a live (non-deleted)
   * attachment of the given Gallery media file. Used to resolve field-level attachments back to
   * their owning item, which the attachment's own {@code getInventoryRecord()} cannot reach. Both
   * flags are checked because deleting a field does not soft-delete its {@code InventoryFile}.
   *
   * @param mediaFileId the db id of the Gallery {@code EcatMediaFile}
   */
  List<InventoryAttachmentField> findAttachmentFieldsByMediaFileId(Long mediaFileId);
}
