package com.researchspace.service.inventory.impl;

import static com.researchspace.api.v1.model.ApiInventoryRecordInfo.tagDifferenceExists;

import com.axiope.search.SearchUtils;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiGroupBasicInfo;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
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
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
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
import com.researchspace.service.inventory.DataCiteRelationType;
import com.researchspace.service.inventory.InventoryApiManager;
import com.researchspace.service.inventory.InventoryFileApiManager;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.InventoryLinkValidator;
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;

@Slf4j
public abstract class InventoryApiManagerImpl<T extends InventoryRecord>
    implements InventoryApiManager<T> {

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
  protected @Autowired InventoryLinkManager inventoryLinkManager;
  private @Autowired InventoryEditLockTracker tracker;
  private @Autowired GroupDao groupDao;
  private @Autowired InventoryFileApiManager inventoryFileApiManager;
  @Autowired @Lazy private DocumentTagManager documentTagManager;
  private @Autowired FileStoreMetaManager fileMetaManagerImpl;

  /**
   * Copies each template link field's allowed-relation-types whitelist onto the matching record
   * link field. The model's per-field template sync ({@code
   * InventoryEntityField#updateToLatestTemplateDefinition}) copies name/columnIndex/mandatory/
   * deletion but not the link whitelist, so without this an existing record (sample or instrument)
   * keeps the whitelist captured when it was created and never picks up a later template edit
   * (RSDEV-1200). New records are unaffected: they clone the template field via {@code
   * shallowCopy()}, which copies the whitelist. A record link field with no connected template link
   * field is left untouched.
   *
   * @return true if any record field's whitelist was changed
   */
  static boolean syncLinkFieldWhitelistsFromTemplate(List<InventoryEntityField> recordFields) {
    boolean changed = false;
    for (InventoryEntityField field : recordFields) {
      if (field instanceof InventoryLinkField
          && field.getTemplateField() instanceof InventoryLinkField) {
        InventoryLinkField recordLink = (InventoryLinkField) field;
        String templateWhitelist =
            ((InventoryLinkField) field.getTemplateField()).getAllowedRelationTypes();
        if (!Objects.equals(recordLink.getAllowedRelationTypes(), templateWhitelist)) {
          recordLink.setAllowedRelationTypes(templateWhitelist);
          changed = true;
        }
      }
    }
    return changed;
  }

  /**
   * Whether a link field's allowed-relation-types whitelist permits the given relation type. An
   * empty (or absent) whitelist permits all of them.
   */
  static boolean isRelationPermitted(InventoryLinkField field, String relationType) {
    String allowed = field.getAllowedRelationTypes();
    if (allowed == null || allowed.trim().isEmpty()) {
      return true;
    }
    return Arrays.asList(allowed.split("\\|")).contains(relationType);
  }

  /**
   * Rejects a template edit that narrows a link field's allowed-relation-types whitelist so that
   * the field's own default link (RSDEV-1246) would no longer be permitted. The per-link check in
   * each manager's {@code assertRelationAllowed} only sees an incoming link, and an unchanged
   * default is a no-op on that path, so a whitelist-only edit would otherwise leave the template
   * holding a default its own field forbids. Failing the edit is preferable to silently dropping
   * the default.
   */
  static void assertDefaultLinksMatchWhitelists(List<InventoryEntityField> templateFields) {
    for (InventoryEntityField field : templateFields) {
      if (field instanceof InventoryLinkField) {
        InventoryLinkField linkField = (InventoryLinkField) field;
        InventoryLink link = linkField.getLink();
        if (link != null && !isRelationPermitted(linkField, link.getRelationType())) {
          throw new ApiRuntimeException(
              "errors.inventory.field.link.defaultRelationTypeNotPermitted",
              link.getRelationType(),
              linkField.getName());
        }
      }
    }
  }

  /**
   * Soft-deletes the {@link InventoryLink} backing a structured link field once that field has been
   * soft-deleted, so the link row (and its Envers audit trail) stays in step with the field. The
   * field's {@code deleted} flag is flipped in the model layer (a template link-field delete, or
   * its propagation to records through {@code updateToLatestTemplateVersion}), which cannot reach
   * the service-layer {@link InventoryLinkManager}; a soft-delete is also an ordinary update rather
   * than a JPA remove, so the {@code cascade}/{@code orphanRemoval} on {@code
   * InventoryLinkField#link} never fires. Without this the link row would linger with {@code
   * deleted=false} after its field is gone (RSDEV-1270). No-op unless the field is a deleted {@link
   * InventoryLinkField} whose link is still live.
   */
  void softDeleteLinkOfDeletedLinkField(InventoryEntityField field, User user) {
    if (field instanceof InventoryLinkField && field.isDeleted()) {
      InventoryLink link = ((InventoryLinkField) field).getLink();
      if (link != null && !link.isDeleted()) {
        inventoryLinkManager.deleteLink(link, user);
      }
    }
  }

  /**
   * Applies a record's chosen link value to its structured link field, going through the {@link
   * InventoryLinkManager} so the target is parsed/validated and the Envers revision captured (the
   * same path used by extra-field links). An unchanged payload is a no-op (previously every save
   * replaced the row, resetting its identity and creation date); a changed payload updates the
   * field's existing InventoryLink row in place; clearing the value dereferences the row, which the
   * field's {@code orphanRemoval} mapping hard-deletes at flush (an Envers DEL revision keeps the
   * history in {@code InventoryLink_AUD}; a prior soft-delete write would be collapsed into that
   * same DEL revision, so none is attempted). This differs deliberately from the extra-field delete
   * path, where the FIELD itself is soft-deleted and its link row therefore survives soft-deleted
   * alongside it. The chosen relation type must be permitted by the template field's
   * allowed-relation-types whitelist (an empty whitelist permits all).
   *
   * <p>Item semantics: an omitted link clears. See the four-argument overload.
   */
  boolean applyLinkFieldValue(
      InventoryLinkField field, ApiInventoryEntityField apiField, User user) {
    return applyLinkFieldValue(field, apiField, user, false);
  }

  /**
   * @param omittedLinkPreservesExisting how to read a payload that carries no {@code link} key at
   *     all. True for a <b>template</b> field, whose PUT accepts a partial field list: a
   *     whitelist-only edit or a rename must not destroy the default link. False for an <b>item</b>
   *     field, whose field list always arrives complete, so an absent link means the user cleared
   *     it (RSDEV-1131, pinned by {@code
   *     InstrumentEntityApiManagerTest.linkFieldValue_clearedWhenInstrumentUpdated}). An explicit
   *     {@code "link": null} clears in both cases.
   */
  boolean applyLinkFieldValue(
      InventoryLinkField field,
      ApiInventoryEntityField apiField,
      User user,
      boolean omittedLinkPreservesExisting) {
    ApiInventoryLink apiLink = apiField.getLink();
    String target = apiLink == null ? null : apiLink.getTargetGlobalId();
    InventoryLink existing = field.getLink();
    if (target == null || target.trim().isEmpty()) {
      if (existing == null) {
        return false; // no link before, none requested now
      }
      if (omittedLinkPreservesExisting && !apiField.isLinkProvided()) {
        // a partial template update that never mentions the link: leave it alone
        return false;
      }
      field.setLink(null); // orphanRemoval hard-deletes the dereferenced row at flush
      return true;
    }
    Long effectivePin =
        apiLink.derivedVersionPin() != null ? apiLink.derivedVersionPin() : apiLink.getVersionPin();
    // compare on the parsed base id, not the raw string: the stored row holds the
    // unsuffixed id (the pin lives in versionPin), so a suffixed incoming id like
    // "SA2v4" would otherwise never compare equal and every save would fire a
    // spurious update (and Envers revision). Mirrors ApiExtraFieldsHelper.linkChanged.
    GlobalIdentifier incoming = parseTargetOrNull(target);
    if (existing != null
        && incoming != null
        && incoming.getPrefix() == existing.getTargetPrefix()
        && Objects.equals(incoming.getDbId(), existing.getTargetDbId())
        && Objects.equals(effectivePin, existing.getVersionPin())
        && Objects.equals(apiLink.getRelationType(), existing.getRelationType())) {
      return false; // unchanged
    }
    assertRelationAllowed(field, apiLink.getRelationType());
    if (existing != null) {
      field.setLink(inventoryLinkManager.updateLink(existing, apiLink, user));
    } else {
      field.setLink(inventoryLinkManager.createLink(apiLink, user));
    }
    return true;
  }

  /**
   * Applies link values to an existing record's structured link fields (the update path). The DTO
   * apply loop leaves link fields untouched because it cannot reach the service-layer {@link
   * InventoryLinkManager}; this matches each modified link field by id and applies it here.
   *
   * <p>Templates go through this same path as items: a template's link field carries an editable
   * default link of its own (RSDEV-1246). Callers pass the pieces rather than their own record
   * type, because {@code isTemplate()} lives on the sample and instrument entities rather than on
   * {@link InventoryRecord}.
   */
  boolean applyLinkFieldValuesOnUpdate(
      List<ApiInventoryEntityField> apiFields,
      List<InventoryEntityField> dbActiveFields,
      InventoryRecord dbRecord,
      boolean isTemplate,
      User user) {
    if (apiFields == null) {
      return false;
    }
    boolean changed = false;
    for (ApiInventoryEntityField apiField : apiFields) {
      if (apiField.isNewFieldRequest()
          || apiField.isDeleteFieldRequest()
          || apiField.getId() == null) {
        continue;
      }
      Optional<InventoryEntityField> dbFieldOpt =
          dbActiveFields.stream()
              .filter(
                  f ->
                      f instanceof InventoryLinkField
                          && Objects.equals(f.getId(), apiField.getId()))
              .findFirst();
      if (dbFieldOpt.isPresent()) {
        rejectSelfLink(apiField.getLink(), dbRecord);
        changed |=
            applyLinkFieldValue((InventoryLinkField) dbFieldOpt.get(), apiField, user, isTemplate);
      }
    }
    return changed;
  }

  /**
   * Applies a template link field's optional default link (RSDEV-1246). The stateless {@code
   * ApiFieldToModelFieldFactory} sets only the allowed-relation-types whitelist, so the default has
   * to be created here, through the same {@link InventoryLinkManager} write path an item's link
   * uses: validated against the DataCite vocabulary and the field's own whitelist, with the Envers
   * revision captured. Items created from the template are then stamped with a copy of it by {@code
   * InventoryLinkField#shallowCopy()}, needing no further code. No-op for any other field type, and
   * for a link field whose payload carries no link.
   */
  void applyDefaultLinkOfNewTemplateField(
      InventoryEntityField toAdd,
      ApiInventoryEntityField apiField,
      InventoryRecord dbTemplate,
      User user) {
    if (toAdd instanceof InventoryLinkField) {
      // the same self-link rejection the edit path applies: adding a link field to an already-saved
      // template must not be a way in for a default that targets that very template
      rejectSelfLink(apiField.getLink(), dbTemplate);
      applyLinkFieldValue((InventoryLinkField) toAdd, apiField, user, true);
    }
  }

  private void assertRelationAllowed(InventoryLinkField field, String relationType) {
    // a chosen relation must be a real DataCite relation type, even when the whitelist is empty.
    // ApiRuntimeException maps to a 422 with the resolved bundle message, where a raw
    // IllegalArgumentException would surface as an unmapped 500.
    if (!DataCiteRelationType.isValid(relationType)) {
      throw new ApiRuntimeException("errors.inventory.field.linkRelationTypeInvalid", relationType);
    }
    if (!isRelationPermitted(field, relationType)) {
      throw new ApiRuntimeException(
          "errors.inventory.field.linkRelationTypeNotPermitted", relationType, field.getName());
    }
  }

  private void rejectSelfLink(ApiInventoryLink apiLink, InventoryRecord dbRecord) {
    // getId() first: getOid() throws rather than returning null on an unsaved record, and this is
    // reached while creating a template, which has no id yet (and so nothing to self-link to).
    // Returning early there is not a hole: the template is not in the database yet either, so
    // InventoryLinkManager.createLink's target-exists-and-readable check rejects its own future
    // Global ID before any link row is written. Every path where a self-link IS reachable (a
    // template or item that already exists) passes a saved record and so runs the check below.
    if (apiLink == null || dbRecord.getId() == null || dbRecord.getOid() == null) {
      return;
    }
    GlobalIdentifier target = parseTargetOrNull(apiLink.getTargetGlobalId());
    if (target == null) {
      return; // malformed/blank targets are handled by the manager / clear path
    }
    if (InventoryLinkValidator.isSelfLink(target, dbRecord.getOid().toString())) {
      throw new ApiRuntimeException(
          "errors.inventory.field.link.selfLinkForbidden", apiLink.getTargetGlobalId());
    }
  }

  private GlobalIdentifier parseTargetOrNull(String targetGlobalId) {
    try {
      return new GlobalIdentifier(targetGlobalId);
    } catch (IllegalArgumentException | NullPointerException ex) {
      return null;
    }
  }

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

    // create extra-fields (Link fields included) through the link-aware helper so a record
    // created together with a link persists that link; a plain create loop here built the
    // ExtraLinkField but dropped its InventoryLink (RSDEV-1131).
    extraFieldHelper.addExtraFieldsForNewInventoryRecord(apiInvRec.getExtraFields(), invRec, user);

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
      return inventoryFileApiManager.saveFileAndCreateFileProperty(
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

  <T extends InventoryRecord> T doSaveImage(
      InventoryRecord record, String base64Image, User user, Class<T> type, UnaryOperator<T> dao)
      throws IOException {
    createImagesForRecord(record, base64Image, user);
    return dao.apply(type.cast(record));
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
