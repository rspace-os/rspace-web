package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.dao.InstrumentEntityDao;
import com.researchspace.dao.InventoryEntityFieldDao;
import com.researchspace.dao.InventoryFileDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.FileStoreMetaManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InventoryFileApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.InventoryRecordRetriever;
import com.researchspace.service.inventory.LinkTargetResolver;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("inventoryFileApiManager")
@Slf4j
public class InventoryFileApiManagerImpl implements InventoryFileApiManager {

  private static final String INVENTORY_FILESTORE_CATEGORY = "inventory";

  private @Autowired InventoryFileDao inventoryFileDao;
  private @Autowired InventoryPermissionUtils invPermissions;
  private @Autowired SampleDao sampleDao;
  private @Autowired InstrumentEntityDao<Instrument> instrumentEntityDao;
  private @Autowired InventoryEntityFieldDao inventoryEntityFieldDao;
  private @Autowired MessageSourceUtils messages;
  private @Autowired IRecordFactory recordFactory;
  private @Autowired BaseRecordManager baseRecordManager;
  private @Autowired LinkTargetResolver linkTargetResolver;

  @Autowired
  @Qualifier("compositeFileStore")
  private FileStore fileStore;

  @Autowired private FileStoreMetaManager fileStoreMetaManager;

  @Autowired private InventoryRecordRetriever inventoryRecordRetriever;

  @Override
  public boolean exists(long id) {
    return inventoryFileDao.exists(id);
  }

  @Override
  public List<ApiInventoryReferencingItem> findAttachingItems(
      String galleryFileGlobalId, User actor) {
    GlobalIdentifier target = parseGalleryTargetOrThrow(galleryFileGlobalId);
    // read-gate: the caller must be able to READ the target Gallery file. Unreadable, missing,
    // malformed and non-Gallery ids all raise the same not-found so the endpoint never discloses a
    // file's existence or its inbound attachments (ADR-0002). Resolve through the shared
    // LinkTargetResolver (as the sibling links endpoint does): it is collision-safe for the GL/
    // FL/SD id space and returns false rather than throwing when the id resolves to a non-media
    // record, so a crafted GL<non-media-id> cannot become an existence oracle via a 500.
    if (!linkTargetResolver.targetExistsAndIsReadable(target, actor)) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.targetNotFound", galleryFileGlobalId);
    }
    List<ApiInventoryReferencingItem> rows = new ArrayList<>();
    // record-level attachments: the attachment resolves its own owning record
    for (InventoryFile file : inventoryFileDao.findByMediaFileId(target.getDbId())) {
      addAttachmentRowIfReadable(rows, file.getInventoryRecord(), actor);
    }
    // field-level attachments: resolved through the owning attachment field, since the attachment
    // itself cannot reach the record it hangs off a field on
    for (InventoryAttachmentField field :
        inventoryFileDao.findAttachmentFieldsByMediaFileId(target.getDbId())) {
      addAttachmentRowIfReadable(rows, field.getInventoryRecord(), actor);
    }
    return rows;
  }

  private GlobalIdentifier parseGalleryTargetOrThrow(String galleryFileGlobalId) {
    GlobalIdentifier gid;
    try {
      gid = new GlobalIdentifier(galleryFileGlobalId);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.targetNotFound", galleryFileGlobalId);
    }
    // attachments only ever target a Gallery media file; any other kind is treated as not-found so
    // a caller cannot probe non-Gallery ids through this endpoint
    if (gid.getPrefix() != GlobalIdPrefix.GL) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.targetNotFound", galleryFileGlobalId);
    }
    return gid;
  }

  private void addAttachmentRowIfReadable(
      List<ApiInventoryReferencingItem> rows, InventoryRecord owningRecord, User actor) {
    if (owningRecord == null || owningRecord.isDeleted()) {
      return;
    }
    if (!invPermissions.canUserReadInventoryRecord(owningRecord, actor)) {
      return;
    }
    ApiInventoryReferencingItem row = new ApiInventoryReferencingItem();
    row.setSourceGlobalId(owningRecord.getOid().toString());
    row.setSourceName(owningRecord.getName());
    row.setSourceType(owningRecord.getType().toString());
    // relationType/versionPin/modifiedAt stay null: an attachment carries no DataCite relation; the
    // client labels these rows "Attachment"
    rows.add(row);
  }

  @Override
  public InventoryFile getInventoryFileById(Long id, User user) {
    boolean exists = inventoryFileDao.exists(id);
    if (!exists) {
      throwNotFoundException(id, "Inventory file");
    }

    InventoryFile invFile = inventoryFileDao.getWithInitializedFields(id);
    GlobalIdentifier parentInvRecOid =
        getGlobalIdOfParentInventoryItem(invFile.getConnectedRecordOid());
    boolean canRead = invPermissions.canUserReadInventoryRecord(parentInvRecOid, user);
    if (!canRead) {
      throwNotFoundException(id, "Inventory file");
    }
    return invFile;
  }

  private void throwNotFoundException(Long id, String recordType) {
    String msg = messages.getResourceNotFoundMessage(recordType, id);
    throw new NotFoundException(msg);
  }

  @Override
  public InventoryFile attachNewInventoryFileToInventoryRecord(
      GlobalIdentifier globalIdToAttachTo,
      String originalFileName,
      InputStream inputStream,
      User user)
      throws IOException {

    GlobalIdentifier invItemOid = getGlobalIdOfParentInventoryItem(globalIdToAttachTo);
    InventoryRecord invRec = invPermissions.assertUserCanEditInventoryRecord(invItemOid, user);

    String filestoreName =
        String.format("att_%s_%s", globalIdToAttachTo.getIdString(), originalFileName);
    FileProperty fileProp = saveFileAndCreateFileProperty(user, filestoreName, inputStream);

    InventoryFile invFile = recordFactory.createInventoryFile(originalFileName, fileProp, user);
    invFile.setExtension(MediaUtils.getExtension(originalFileName));
    String mimeType = URLConnection.guessContentTypeFromName(originalFileName);
    if (mimeType == null) {
      mimeType = MediaUtils.getContentTypeForFileExtension(invFile.getExtension());
    }
    invFile.setContentMimeType(mimeType);

    return attachInventoryFileToInventoryRecord(invRec, invFile, globalIdToAttachTo);
  }

  @Override
  public InventoryFile attachGalleryFileToInventoryRecord(
      GlobalIdentifier globalIdToAttachTo, GlobalIdentifier galleryFileGlobalId, User user) {

    GlobalIdentifier invItemOid = getGlobalIdOfParentInventoryItem(globalIdToAttachTo);
    InventoryRecord invRec = invPermissions.assertUserCanEditInventoryRecord(invItemOid, user);

    EcatMediaFile galleryFile =
        baseRecordManager.retrieveMediaFile(user, galleryFileGlobalId.getDbId());
    InventoryFile invFile = new InventoryFile(galleryFile);

    return attachInventoryFileToInventoryRecord(invRec, invFile, globalIdToAttachTo);
  }

  private InventoryFile attachInventoryFileToInventoryRecord(
      InventoryRecord invRec, InventoryFile invFile, GlobalIdentifier globalIdToAttachTo) {

    if (GlobalIdPrefix.SF.equals(globalIdToAttachTo.getPrefix())) {
      // sample fields can belong to a sample or a template, so cast to the common supertype
      SampleEntity sample = (SampleEntity) invRec;
      InventoryEntityField field = sample.getFieldById(globalIdToAttachTo.getDbId()).orElse(null);
      if (field == null) {
        throwNotFoundException(globalIdToAttachTo.getDbId(), "Sample field");
      }
      field.setAttachedFile(invFile);
    } else {
      invRec.addAttachedFile(invFile);
    }
    return inventoryFileDao.save(invFile);
  }

  /**
   * Finds global id of an inventory item based on invRecGlobalId.
   *
   * @param invRecGlobalId
   * @return
   */
  private GlobalIdentifier getGlobalIdOfParentInventoryItem(GlobalIdentifier invRecGlobalId) {
    if (GlobalIdPrefix.SF.equals(invRecGlobalId.getPrefix())) {
      InventoryEntityField field = inventoryEntityFieldDao.get(invRecGlobalId.getDbId());
      GlobalIdentifier parentInvItemOid;
      String whatsMissed = "";
      if (field.getSample() != null) {
        parentInvItemOid = sampleDao.getSampleGlobalIdFromFieldId(invRecGlobalId.getDbId());
        whatsMissed = "Sample";
      } else {
        parentInvItemOid =
            inventoryEntityFieldDao.getInstrumentEntityGlobalIdFromFieldId(
                invRecGlobalId.getDbId());
        whatsMissed = "Instrument Entity";
      }
      if (parentInvItemOid == null) {
        throwNotFoundException(invRecGlobalId.getDbId(), whatsMissed + " field");
      }
      return parentInvItemOid;
    }
    return invRecGlobalId;
  }

  private FileProperty saveFileAndCreateFileProperty(
      User user, String filestoreName, InputStream inputStream) throws IOException {
    // FileProperty.contentsHash is only relevant to images and the way they are retrieved therefore
    // set to an empty string here.
    String emptyContentsHash = "";
    return saveFileAndCreateFileProperty(user, filestoreName, emptyContentsHash, inputStream);
  }

  @Override
  public FileProperty saveFileAndCreateFileProperty(
      User user, String fileName, String contentsHash, InputStream inputStream) throws IOException {

    FileProperty fileProperty = new FileProperty();
    fileProperty.setFileCategory(INVENTORY_FILESTORE_CATEGORY);
    if (!user.getGroups().isEmpty()) {
      fileProperty.setFileGroup(user.getGroups().iterator().next().getUniqueName());
    } else {
      fileProperty.setFileGroup(user.getUsername());
    }
    fileProperty.setFileOwner(user.getUsername());
    fileProperty.setFileUser(user.getUsername());
    fileProperty.setFileVersion("1");
    fileProperty.setContentsHash(contentsHash);
    // we always save locally on initial upload
    fileProperty.setRoot(fileStore.getCurrentLocalFileStoreRoot());

    URI uri = fileStore.save(fileProperty, inputStream, fileName, FileDuplicateStrategy.AS_NEW);
    log.debug("File property {} created at URI {}", fileProperty.getId(), uri);
    log.info("URI is {}", fileProperty.getAbsolutePathUri());

    return fileProperty;
  }

  @Override
  public InventoryFile markInventoryFileAsDeleted(Long id, User user) {
    InventoryFile invFile = getInventoryFileById(id, user);
    GlobalIdentifier parentInvRecOid =
        getGlobalIdOfParentInventoryItem(invFile.getConnectedRecordOid());
    invPermissions.assertUserCanEditInventoryRecord(parentInvRecOid, user);

    // mark file as deleted
    invFile.setDeleted(true);
    invFile = inventoryFileDao.save(invFile);

    // refresh attachments list of parent record
    if (invFile.getInventoryRecord() != null) {
      invFile.getInventoryRecord().refreshActiveAttachedFiles();
    }

    return invFile;
  }

  public FileProperty getFilePropertyByContentsHash(String contentsHash, User user) {
    Map<String, String> properties = new HashMap<>();

    // for better performance to potentially avoid checking permissions, check if the user owns
    // any doc using the image with the contentsHash
    if (fileStoreMetaManager.doesUserOwnDocWithHash(user, contentsHash)) {
      return fileStoreMetaManager.getByHash(contentsHash);
    }

    // get any usage of the contents hash, rather than also being restricted by user, since e.g. the
    // image could belong to a template which wasn't created by the requesting user
    properties.put("contentsHash", contentsHash);
    List<FileProperty> filePropsWithHash = fileStoreMetaManager.findProperties(properties);

    if (filePropsWithHash.isEmpty()) {
      throw new NotFoundException(String.format("Image with hash %s not found.", contentsHash));
    }

    for (FileProperty fileProperty : filePropsWithHash) {
      if (inventoryRecordRetriever.userHasCanViewFileProperty(user, fileProperty)) {
        return fileProperty;
      }
    }

    // FileProperty exists, but the user doesn't have read permissions on any file which uses
    // the FileProperty
    throw new AuthorizationException(
        String.format(
            "User doesn't have permissions to read image " + "file with hash %s.", contentsHash));
  }
}
