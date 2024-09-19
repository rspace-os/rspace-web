package com.researchspace.service.inventory.impl;

import static com.researchspace.api.v1.model.ApiInventoryRecordInfo.tagDifferenceExists;

import com.axiope.search.SearchUtils;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiGroupBasicInfo;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.core.util.imageutils.ImageUtils;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.MovableInventoryRecord;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.service.FileStoreMetaManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.DocumentTagManagerImpl;
import com.researchspace.service.inventory.ApiBarcodesHelper;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.service.inventory.ApiIdentifiersHelper;
import com.researchspace.service.inventory.InventoryApiManager;
import com.researchspace.service.inventory.InventoryFileApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;

@Slf4j
public abstract class InventoryApiManagerImpl implements InventoryApiManager {

  static final int THUMBNAIL_MAX_SIZE_IN_PX = 150;
  final Long DEFAULT_ICON_ID = -1L;
  protected @Autowired IRecordFactory recordFactory;
  protected @Autowired ApiExtraFieldsHelper extraFieldHelper;
  protected @Autowired ApiBarcodesHelper barcodesHelper;
  protected @Autowired ApiIdentifiersHelper identifiersHelper;
  protected @Autowired ApplicationEventPublisher publisher;
  protected @Autowired UserManager userManager;
  protected @Autowired ContainerDao containerDao;
  protected @Autowired InventoryPermissionUtils invPermissions;
  private @Autowired InventoryEditLockTracker tracker;
  private @Autowired GroupDao groupDao;
  private @Autowired InventoryFileApiManager inventoryFileApiManager;
  @Autowired @Lazy private DocumentTagManager documentTagManager;
  private @Autowired FileStoreMetaManager fileMetaManagerImpl;

  protected void updateOntologyOnUpdate(
      ApiInventoryRecordInfo original, ApiInventoryRecordInfo updated, User user) {
    if (tagDifferenceExists(original, updated)) {
      documentTagManager.updateUserOntologyDocument(user);
    }
  }

  protected void updateOntologyOnRecordChanges(ApiInventoryRecordInfo affected, User user) {
    if (!affected.getTags().isEmpty()) {
      documentTagManager.updateUserOntologyDocument(user);
    }
  }

  protected void setBasicFieldsFromNewIncomingApiInventoryRecord(
      InventoryRecord invRec, ApiInventoryRecordInfo apiInvRec, User user) {
    invRec.setDescription(apiInvRec.getDescription());
    invRec.setIconId(apiInvRec.getIconId() == null ? DEFAULT_ICON_ID : apiInvRec.getIconId());
    invRec.setTagMetaData(apiInvRec.getDBStringFromTags());
    invRec.setTags(
        String.join(
            ",",
            DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta(
                apiInvRec.getDBStringFromTags())));
    updateOntologyOnRecordChanges(apiInvRec, user);
    if (apiInvRec.getSharingMode() != null) {
      invRec.setSharingMode(
          InventoryRecord.InventorySharingMode.valueOf(apiInvRec.getSharingMode().toString()));
    }
    saveSharingACLForIncomingApiInvRec(invRec, apiInvRec);

    for (ApiExtraField apiExtraField : apiInvRec.getExtraFields()) {
      ExtraField extraField =
          recordFactory.createExtraField(
              apiExtraField.getName(), apiExtraField.getTypeAsFieldType(), user, invRec);
      extraField.setData(apiExtraField.getContent());
      invRec.addExtraField(extraField);
    }

    for (ApiBarcode apiBarcode : apiInvRec.getBarcodes()) {
      Barcode barcode = new Barcode(apiBarcode.getData(), user.getUsername());
      barcode.setFormat(apiBarcode.getFormat());
      barcode.setDescription(apiBarcode.getDescription());
      invRec.addBarcode(barcode);
    }
  }

  protected boolean saveSharingACLForIncomingApiInvRec(
      InventoryRecord invRec, ApiInventoryRecordInfo apiInvRec) {
    boolean changed = false;
    if (apiInvRec.getSharedWith() != null) {
      List<String> newSharedWith = new ArrayList<>();
      for (ApiGroupInfoWithSharedFlag groupShared : apiInvRec.getSharedWith()) {
        if (groupShared.isShared()) {
          Group group = groupDao.get(groupShared.getGroupInfo().getId());
          newSharedWith.add(group.getUniqueName());
        }
      }
      List<String> currentlySharedWith = invRec.getSharedWithUniqueNames();
      if (!CollectionUtils.isEqualCollection(currentlySharedWith, newSharedWith)) {
        RecordSharingACL newACL = new RecordSharingACL();
        for (String uniqueName : newSharedWith) {
          newACL.addACLElement(
              new ACLElement(
                  uniqueName,
                  new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE)));
        }
        invRec.setSharingACL(newACL);
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public void setOtherFieldsForOutgoingApiInventoryRecord(
      ApiInventoryRecordInfo recordInfo, InventoryRecord invRec, User user) {
    invPermissions.setPermissionsInApiInventoryRecord(recordInfo, invRec, user);
    if (recordInfo.isLimitedReadItem()) {
      recordInfo.clearPropertiesForLimitedView();
    } else if (recordInfo.isPublicReadItem()) {
      recordInfo.clearPropertiesForPublicView();
    }
    if (recordInfo.getModifiedBy() != null) {
      String modifiedByFullName = userManager.getFullNameByUsername(recordInfo.getModifiedBy());
      recordInfo.setModifiedByFullName(modifiedByFullName);
    }
  }

  protected void populateSharingPermissions(
      List<ApiGroupInfoWithSharedFlag> sharingPermissions, InventoryRecord dbRec) {
    if (sharingPermissions == null) {
      return;
    }
    sharingPermissions.clear();
    List<String> sharedWithUniqueNames = dbRec.getSharedWithUniqueNames();

    // add each of user's groups, while setting share permission dependent on acl
    for (Group group : dbRec.getOwner().getGroups()) {
      boolean isShared =
          sharedWithUniqueNames != null && sharedWithUniqueNames.remove(group.getUniqueName());
      sharingPermissions.add(
          new ApiGroupInfoWithSharedFlag(new ApiGroupBasicInfo(group), isShared, true));
    }

    // add remaining groups mentioned in acl
    if (sharedWithUniqueNames != null) {
      for (String aclGroupName : sharedWithUniqueNames) {
        Group aclGroup = groupDao.getByUniqueName(aclGroupName);
        if (aclGroup != null) { // RSINV-761 - the group could be deleted since sharing
          sharingPermissions.add(
              new ApiGroupInfoWithSharedFlag(new ApiGroupBasicInfo(aclGroup), true, false));
        }
      }
    }
  }

  /**
   * Sorts, repaginates and converts db records to search result object.
   *
   * @param pgCrit
   * @param dbRecords
   * @return
   */
  @Override
  public ApiInventorySearchResult sortRepaginateConvertToApiInventorySearchResult(
      PaginationCriteria<InventoryRecord> pgCrit,
      List<? extends InventoryRecord> dbRecords,
      User user) {

    SearchUtils.sortInventoryList(dbRecords, pgCrit);
    List<? extends InventoryRecord> contentPage =
        SearchUtils.repaginateResults(
            dbRecords, pgCrit.getResultsPerPage(), pgCrit.getPageNumber().intValue());

    return convertToApiInventorySearchResult(
        (long) dbRecords.size(), pgCrit.getPageNumber().intValue(), contentPage, user);
  }

  @Override
  public ApiInventorySearchResult convertToApiInventorySearchResult(
      Long totalHits, Integer pageNumber, List<? extends InventoryRecord> dbRecords, User user) {

    List<ApiInventoryRecordInfo> contentInfos = new ArrayList<>();
    for (InventoryRecord invRec : dbRecords) {
      ApiInventoryRecordInfo apiInvRec = ApiInventoryRecordInfo.fromInventoryRecord(invRec);
      setOtherFieldsForOutgoingApiInventoryRecord(apiInvRec, invRec, user);
      contentInfos.add(apiInvRec);
    }

    ApiInventorySearchResult apiSearchResult = new ApiInventorySearchResult();
    apiSearchResult.setTotalHits(totalHits);
    apiSearchResult.setPageNumber(pageNumber);
    apiSearchResult.setItems(contentInfos);

    return apiSearchResult;
  }

  FileProperty saveImageFile(User user, String base64Image) throws IOException {
    String imageExtension = ImageUtils.getExtensionFromBase64DataImage(base64Image);
    byte[] imageBytes = ImageUtils.getImageBytesFromBase64DataImage(base64Image);
    InputStream imageIS = new ByteArrayInputStream(imageBytes);
    String contentsHash = CryptoUtils.hashWithSha256inHex(base64Image);
    String filename = String.format("%s.%s", contentsHash, imageExtension);

    return saveOrRetrieveImage(user, filename, imageIS, contentsHash);
  }

  FileProperty saveThumbnailImageFile(User user, String base64Image) throws IOException {
    String imageExtension = ImageUtils.getExtensionFromBase64DataImage(base64Image);
    byte[] imageBytes = ImageUtils.getImageBytesFromBase64DataImage(base64Image);

    InputStream thumbnailForHash = new ByteArrayInputStream(imageBytes);
    String contentsHash =
        CryptoUtils.hashWithSha256inHex(Arrays.toString(thumbnailForHash.readAllBytes()));
    String filename = String.format("%s.%s", contentsHash, imageExtension);

    try (InputStream imageIS = createThumbnailFromImageBytes(imageBytes, imageExtension)) {
      return saveOrRetrieveImage(user, filename, imageIS, contentsHash);
    }
  }

  /*
  The same FileProperty (and therefore the same file on disk) can belong to many InventoryRecords.
  Checks if a FileProperty already exists for the given user and hash of the contents of the image
  and returns that if so. Otherwise, generates a new FileProperty.
   */
  private FileProperty saveOrRetrieveImage(
      User user, String fileName, InputStream imageIs, String contentsHash) throws IOException {
    Optional<FileProperty> existingFile =
        getExistingFilePropertyForImage(contentsHash, user.getUsername());
    if (existingFile.isPresent()) {
      return existingFile.get();
    } else {
      return inventoryFileApiManager.generateInventoryFileProperty(
          user, fileName, contentsHash, imageIs);
    }
  }

  InputStream createThumbnailFromImageBytes(byte[] imageBytes, String outputFormat)
      throws IOException {

    InputStream imageIS = new ByteArrayInputStream(imageBytes);
    Optional<BufferedImage> image = ImageUtils.getBufferedImageFromInputImageStream(imageIS);
    if (image.isPresent()) {
      int width = Math.min(image.get().getWidth(), THUMBNAIL_MAX_SIZE_IN_PX);
      int height = Math.min(image.get().getHeight(), THUMBNAIL_MAX_SIZE_IN_PX);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageUtils.createThumbnail(image.get(), width, height, baos, outputFormat);
      return new ByteArrayInputStream(baos.toByteArray());
    }
    return new ByteArrayInputStream(new byte[0]);
  }

  @Override
  public void createImagesForRecord(InventoryRecord invRec, String base64Image, User user)
      throws IOException {
    // main image
    FileProperty mainImage = saveImageFile(user, base64Image);
    invRec.setImageFileProperty(mainImage);

    // thumbnail version
    FileProperty thumbnail = saveThumbnailImageFile(user, base64Image);
    invRec.setThumbnailFileProperty(thumbnail);
  }

  private Optional<FileProperty> getExistingFilePropertyForImage(
      String contentsHash, String userName) {
    Map<String, String> properties =
        Map.ofEntries(
            Map.entry("fileGroup", userName),
            Map.entry("fileOwner", userName),
            Map.entry("contentsHash", contentsHash));
    return fileMetaManagerImpl.findProperties(properties).stream().findFirst();
  }

  /**
   * Save incoming main image.
   *
   * @return true if any images were saved
   */
  <T extends InventoryRecord> boolean saveIncomingImage(
      InventoryRecord dbRecord,
      ApiInventoryRecordInfo incomingApiRecord,
      User user,
      Class<T> type,
      UnaryOperator<T> dao) {

    boolean result = false;
    String recordImage = incomingApiRecord.getNewBase64Image();
    if (recordImage != null) {
      try {
        doSaveImage(dbRecord, recordImage, user, type, dao);
        result = true;
      } catch (IOException e) {
        log.error("Failed saving incoming image for record [{}]", dbRecord.getGlobalIdentifier());
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  <T extends InventoryRecord> T doSaveImage(
      InventoryRecord record, String base64Image, User user, Class<T> type, UnaryOperator<T> dao)
      throws IOException {
    createImagesForRecord(record, base64Image, user);
    record = dao.apply((T) record);
    return (T) record;
  }

  @Override
  public InventoryFile saveAttachment(
      GlobalIdentifier parentOid, String originalFileName, InputStream inputStream, User user)
      throws IOException {
    return inventoryFileApiManager.attachNewInventoryFileToInventoryRecord(
        parentOid, originalFileName, inputStream, user);
  }

  protected void setWorkbenchAsParentForNewInventoryRecord(
      Container workbench, MovableInventoryRecord invRec) {
    if (invRec.getParentContainer() == null) {
      invRec.moveToNewParent(workbench);
      invRec.setLastMoveDate(null); // for records created in workspace don't start move timer
    }
  }

  protected void setNewCreatorForCopiedInventoryRecord(InventoryRecord copy, User user) {
    copy.setCreatedBy(user.getUsername());
    copy.setModifiedBy(user.getUsername());
  }

  /**
   * If the item is located in workbench of originalOwner, move it to workbench of targetOwner.
   *
   * @param movableInvRec
   * @param originalOwner
   * @param targetOwner
   */
  protected void moveItemBetweenWorkbenches(
      MovableInventoryRecord movableInvRec, User originalOwner, User targetOwner) {
    boolean isOnOriginalOwnerWorkbench =
        movableInvRec.getParentContainer() != null
            && movableInvRec.getParentContainer().isWorkbench()
            && movableInvRec.getParentContainer().getOwner().equals(originalOwner);

    if (isOnOriginalOwnerWorkbench) {
      Container targetOwnerWorkbench = containerDao.getWorkbenchForUser(targetOwner);
      movableInvRec.moveToNewParent(targetOwnerWorkbench);
    }
  }

  abstract <T extends InventoryRecord> T getIfExists(Long id);

  /**
   * Locks the item for edit (if it wasn't locked before), or extend the pre-existing lock.
   *
   * <p>Throws exception if item cannot be locked by the user.
   *
   * <p>Subsequent code should consider re-fetching the Inventory Record entity, as only when locked
   * it's guaranteed not to change.
   *
   * @return true if new lock was created, false if record was already locked
   */
  protected boolean lockItemForEdit(InventoryRecord invRec, User user) {
    ApiInventoryEditLock apiLock = tracker.attemptToLockForEdit(invRec.getGlobalIdentifier(), user);
    if (ApiInventoryEditLockStatus.CANNOT_LOCK.equals(apiLock.getStatus())) {
      throw new IllegalArgumentException(apiLock.getMessage());
    }

    return ApiInventoryEditLockStatus.LOCKED_OK.equals(apiLock.getStatus());
  }

  /** Unlocks the locked item so other users can edit again. */
  protected void unlockItemAfterEdit(InventoryRecord invRec, User user) {
    tracker.attemptToUnlock(invRec.getGlobalIdentifier(), user);
  }

  protected void populateSubSampleParentContainerChain(SubSample subSample) {
    populateParentContainerChain(subSample.getParentContainer());
  }

  private void populateParentContainerChain(Container container) {
    if (container != null) {
      container.getActiveBarcodes();
      populateParentContainerChain(container.getParentContainer());
    }
  }

  /*
   * ================
   *  for testing
   * ================
   */

  @Override
  public void setPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }
}
