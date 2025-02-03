package com.researchspace.service.inventory;

import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import java.io.IOException;
import java.io.InputStream;

/** Handles API actions around Inventory File. */
public interface InventoryFileApiManager {

  /** Checks if Inventory File with given id exists */
  boolean exists(long id);

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
