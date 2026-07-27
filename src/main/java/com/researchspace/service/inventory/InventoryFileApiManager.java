package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Handles API actions around Inventory File. */
public interface InventoryFileApiManager {

  /** Checks if Inventory File with given id exists */
  boolean exists(long id);

  /**
   * The Inventory items that attach the given Gallery media file, for the "Related inventory items"
   * back-reference panels. Covers both record-level and field-level attachments, each reported
   * against its owning item. The caller must be able to READ the target Gallery file; an
   * unreadable, missing, malformed, or non-Gallery Global ID all raise the same not-found error so
   * the endpoint discloses nothing (mirrors {@link InventoryLinkManager#findReferencingItems} and
   * ADR-0002). Source items the caller cannot read are filtered out.
   *
   * @param galleryFileGlobalId the Global ID of the Gallery file (GL...)
   * @param actor the requesting user
   */
  List<ApiInventoryReferencingItem> findAttachingItems(String galleryFileGlobalId, User actor);

  /** Retrieve Inventory File with given id */
  InventoryFile getInventoryFileById(Long id, User user);

  /** Save input stream as attachment to inventory record or sample attachment field. */
  InventoryFile attachNewInventoryFileToInventoryRecord(
      GlobalIdentifier globalIdToAttachTo, String fileName, InputStream inputStream, User user)
      throws IOException;

  /** Connect Gallery file to inventory record or sample attachment field. */
  InventoryFile attachGalleryFileToInventoryRecord(
      GlobalIdentifier globalIdToAttachTo, GlobalIdentifier galleryFileGlobalId, User user);

  /** Saves input stream as an inventory record in filestore. */
  FileProperty saveFileAndCreateFileProperty(
      User user, String fileName, String contentsHash, InputStream inputStream) throws IOException;

  /** Marks attachment as deleted. */
  InventoryFile markInventoryFileAsDeleted(Long id, User user);

  FileProperty getFilePropertyByContentsHash(String fileName, User user);
}
