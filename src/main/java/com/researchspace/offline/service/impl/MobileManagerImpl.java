package com.researchspace.offline.service.impl;

import com.researchspace.dao.FolderDao;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.offline.dao.OfflineRecordUserDao;
import com.researchspace.offline.model.OfflineImage;
import com.researchspace.offline.model.OfflineRecord;
import com.researchspace.offline.model.OfflineRecordInfo;
import com.researchspace.offline.model.OfflineRecordUser;
import com.researchspace.offline.model.OfflineWorkType;
import com.researchspace.offline.service.MobileManager;
import com.researchspace.offline.service.OfflineManager;
import com.researchspace.service.EcatImageAnnotationManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.ThumbnailManager;
import com.researchspace.service.UserManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("mobileManager")
public class MobileManagerImpl implements MobileManager {

  public static final String CONFLICTING_RECORD_SUFFIX = "_offline_copy";

  private final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired RecordManager recordManager;
  protected @Autowired FolderDao folderDao;
  private @Autowired OfflineManager offlineManager;
  private @Autowired OfflineRecordUserDao offlineRecordUserDao;
  private @Autowired UserManager userManager;
  private @Autowired ThumbnailManager thumbnailManager;
  private @Autowired EcatImageAnnotationManager annotationManager;
  private @Autowired FieldParser fieldParser;
  private @Autowired RsToMobileContentConverter rsToMobileContentConverter;
  private @Autowired MobileToRsContentConverter mobileToRsContentConverter;
  private @Autowired MediaManager mediaManager;
  private @Autowired IPermissionUtils permissionUtils;

  @Override
  public List<OfflineRecordInfo> getOfflineRecordList(String username) {

    List<OfflineRecordInfo> resultList = new ArrayList<OfflineRecordInfo>();
    User user = userManager.getUserByUsername(username, false);

    List<OfflineRecordUser> offlineWorkForUser = offlineManager.getOfflineWorkForUser(user);
    if (offlineWorkForUser != null) {
      for (OfflineRecordUser workItem : offlineWorkForUser) {
        resultList.add(new OfflineRecordInfo(workItem));
      }
    }

    return resultList;
  }

  @Override
  public OfflineRecord getRecord(Long recordId, String username) throws Exception {

    Record record = recordManager.get(recordId);
    User user = userManager.getUserByUsername(username, true);

    boolean accessPermitted = permissionUtils.isPermitted(record, PermissionType.READ, user);
    if (!accessPermitted) {
      throw new AuthorizationException("user " + user.getId() + " can't read record " + recordId);
    }

    OfflineRecord offlineRecord = new OfflineRecord(record);
    RSPath recordPath = record.getParentHierarchyForUser(user);
    offlineRecord.updatePath(recordPath);

    OfflineRecordUser offlineRecordUser = offlineManager.getOfflineWork(recordId, user.getId());
    if (offlineRecordUser == null) {
      throw new IllegalArgumentException("record " + recordId + " is not marked for offline work");
    }
    offlineRecord.setLockType(offlineRecordUser.getWorkType());

    addImagesDataToRecord(offlineRecord, user);
    convertFieldContentToMobileFormat(offlineRecord);

    return offlineRecord;
  }

  private void addImagesDataToRecord(OfflineRecord offlineRecord, User user)
      throws IOException, URISyntaxException {

    FieldContents elementsFromContent =
        fieldParser.findFieldElementsInContent(offlineRecord.getContent());

    if (elementsFromContent.hasElements(EcatImage.class)) {
      for (EcatImage image : elementsFromContent.getElements(EcatImage.class).getElements()) {
        Thumbnail toCreate = new Thumbnail();
        toCreate.setHeight(image.getHeight());
        toCreate.setWidth(image.getWidth());
        toCreate.setSourceType(SourceType.IMAGE);
        toCreate.setSourceId(image.getId());
        toCreate.setSourceParentId(offlineRecord.getFieldId());
        Thumbnail thumbnail = thumbnailManager.getThumbnail(toCreate, user);
        byte[] data = thumbnailManager.getThumbnailData(thumbnail.getId());

        offlineRecord.addImage(new OfflineImage(image, data));
      }
    }
    if (elementsFromContent.hasImageAnnotations()) {
      for (EcatImageAnnotation annotation :
          elementsFromContent.getImageAnnotations().getElements()) {
        String annotationWithoutBgImg =
            annotationManager.removeBackgroundImageNodesFromZwibblerAnnotation(
                annotation.getAnnotations());
        annotation.setAnnotations(annotationWithoutBgImg);
        offlineRecord.addImage(new OfflineImage(annotation));
      }
    }
    if (elementsFromContent.hasSketches()) {
      for (EcatImageAnnotation sketch : elementsFromContent.getSketches().getElements()) {
        offlineRecord.addImage(new OfflineImage(sketch));
      }
    }
    if (elementsFromContent.hasElements(RSChemElement.class)) {
      for (RSChemElement chemElem :
          elementsFromContent.getElements(RSChemElement.class).getElements()) {
        offlineRecord.addImage(new OfflineImage(chemElem));
      }
    }
  }

  private void convertFieldContentToMobileFormat(OfflineRecord offlineRecord) {
    String convertedContent =
        rsToMobileContentConverter.convertFieldContent(
            offlineRecord.getContent(), offlineRecord.getFieldId());
    offlineRecord.setContent(convertedContent);
  }

  @Override
  public Long uploadRecord(OfflineRecord record, String username) throws IOException {

    User user = userManager.getUserByUsername(username, true);
    log.debug("uploading record: " + record.getName() + " for user: " + user.getId());

    Long recordId = null;
    if (isNewRecord(record)) {
      log.debug("creating new record");
      recordId = saveOfflineRecordAsNewDbRecord(record, creatNewDbRecordForOffline(user), user);
    } else {
      log.debug("modifying existing record: " + record.getId());
      /* may still create new record, i.e. if there is a conflict */
      recordId = updateExistingRecordFromOffline(record, user);
    }

    return recordId;
  }

  private boolean isNewRecord(OfflineRecord record) {
    return record.getId() == null;
  }

  private StructuredDocument creatNewDbRecordForOffline(User user) {
    Long rootFolderId = folderDao.getRootRecordForUser(user).getId();
    StructuredDocument newDbRecord = recordManager.createBasicDocument(rootFolderId, user);
    return newDbRecord;
  }

  protected Long saveOfflineRecordAsNewDbRecord(
      OfflineRecord incomingRecord, StructuredDocument dbRecord, User user) throws IOException {

    Record savedRecord = applyIncomingChangesToStructuredDocument(incomingRecord, dbRecord, user);
    offlineRecordUserDao.createOfflineWork(savedRecord, user, OfflineWorkType.EDIT);

    return savedRecord.getId();
  }

  protected Long updateExistingRecordFromOffline(OfflineRecord incomingRecord, User user)
      throws IOException {

    StructuredDocument dbRecord = (StructuredDocument) recordManager.get(incomingRecord.getId());

    // offline edit lock could have been reverted
    if (noOfflineEditLock(dbRecord, user)) {
      log.info("user doesn't have offline record lock for record " + dbRecord.getId());
      return saveConflictingModificationAsANewRecord(incomingRecord, dbRecord, user);
    }

    // dbrecord could be changed since last synchronisation
    if (incomingRecordIsStale(incomingRecord, dbRecord)) {
      log.info(
          "incoming record is not base on latest db version of the record " + dbRecord.getId());
      return saveConflictingModificationAsANewRecord(incomingRecord, dbRecord, user);
    }

    Record savedRecord = applyIncomingChangesToStructuredDocument(incomingRecord, dbRecord, user);
    return savedRecord.getId();
  }

  private Long saveConflictingModificationAsANewRecord(
      OfflineRecord incomingRecord, StructuredDocument dbRecord, User user) throws IOException {

    String newName = incomingRecord.getName() + CONFLICTING_RECORD_SUFFIX;
    incomingRecord.setName(newName);

    Folder parent = dbRecord.getParent();
    if (parent == null) {
      parent = folderDao.getRootRecordForUser(user);
    }

    RecordCopyResult copy =
        recordManager.copy(incomingRecord.getId(), newName, user, parent.getId());
    StructuredDocument copiedRecord = (StructuredDocument) copy.getCopy(dbRecord);

    return saveOfflineRecordAsNewDbRecord(incomingRecord, copiedRecord, user);
  }

  private Record applyIncomingChangesToStructuredDocument(
      OfflineRecord incomingRecord, StructuredDocument dbRecord, User user) throws IOException {

    Field dbField = dbRecord.getFields().get(0);
    dbRecord.setName(incomingRecord.getName());
    dbField.setFieldData(incomingRecord.getContent());

    saveAnnotationsAndUpdateFieldContent(incomingRecord.getImages(), dbField, user);
    convertMobileContentToRsFormat(dbField);

    Record savedRecord = recordManager.save(dbRecord, user);
    return savedRecord;
  }

  /** saves annotations/sketches to database and updates field content to point to new records. */
  private void saveAnnotationsAndUpdateFieldContent(
      List<OfflineImage> images, Field field, User user) throws IOException {

    if (images.isEmpty()) {
      return;
    }

    String fdata = field.getFieldData();
    Document document = Jsoup.parse(fdata);
    Elements imageElems = document.select("img");

    for (OfflineImage image : images) {

      String imageType = image.getType();
      if (!"sketch".equals(imageType) && !"annotation".equals(imageType)) {
        log.warn("skipping upload request for offline image of unsupported type: " + imageType);
        continue;
      }

      Elements matchingElems = imageElems.select("[data-type=" + imageType + "]");

      if (image.getId() != null) {
        // for images created on a server only data-id is available
        matchingElems = matchingElems.select("[data-id=" + image.getId() + "]");
      } else {
        // for images created on a device only data-localid is available
        matchingElems = matchingElems.select("[data-localid=" + image.getClientId() + "]");
      }

      if (matchingElems.size() < 1) {
        log.warn(
            "skipping upload request for offline image as field doesn't contain img with id "
                + image.getId()
                + "/"
                + image.getClientId());
        continue;
      }

      EcatImageAnnotation updatedAnnotation = null;
      if ("sketch".equals(imageType)) {
        String sketchId = "";
        if (image.getId() != null) {
          sketchId += image.getId();
        }
        updatedAnnotation =
            mediaManager.saveSketch(
                image.getAnnotation(),
                image.getBase64ImageData(),
                sketchId,
                field.getId(),
                field.getStructuredDocument(),
                user);
      }

      if ("annotation".equals(imageType)) {
        String imageId = matchingElems.first().attr("data-imageId");
        updatedAnnotation =
            mediaManager.saveImageAnnotation(
                image.getAnnotation(),
                image.getBase64ImageData(),
                field.getId(),
                field.getStructuredDocument(),
                Long.parseLong(imageId),
                user);
      }

      // override image id in content (in case of new annotation)
      matchingElems.attr("data-id", "" + updatedAnnotation.getId());
    }

    field.setFieldData(document.body().html());
  }

  private void convertMobileContentToRsFormat(Field importedField) {
    String convertedContent =
        mobileToRsContentConverter.convertFieldContent(
            importedField.getFieldData(), importedField.getId());
    importedField.setData(convertedContent);
  }

  private boolean noOfflineEditLock(Record dbRecord, User user) {
    OfflineRecordUser offlineRecordUser =
        offlineManager.getOfflineWork(dbRecord.getId(), user.getId());
    return offlineRecordUser == null
        || !OfflineWorkType.EDIT.equals(offlineRecordUser.getWorkType());
  }

  private boolean incomingRecordIsStale(OfflineRecord incomingRecord, Record dbRecord) {
    return !dbRecord
        .getModificationDate()
        .equals(incomingRecord.getLastSynchronisedModificationTime());
  }
}
