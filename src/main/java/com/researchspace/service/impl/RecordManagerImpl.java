package com.researchspace.service.impl;

import static com.researchspace.model.comms.NotificationType.NOTIFICATION_DOCUMENT_EDITED;
import static com.researchspace.model.record.BaseRecord.DEFAULT_VARCHAR_LENGTH;
import static java.lang.String.format;
import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang.StringUtils.abbreviate;
import static org.apache.commons.lang.StringUtils.trim;

import com.axiope.search.SearchUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.AuditDao;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.FormUsageDao;
import com.researchspace.dao.NameDateFilter;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.dao.RecordUserFavoritesDao;
import com.researchspace.dao.UserDao;
import com.researchspace.linkedelements.FieldContentDelta;
import com.researchspace.linkedelements.FieldLinksEntitiesSynchronizer;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.linkedelements.TextFieldDataSanitizer;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.GalleryFilterCriteria;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.events.RecordCopyEvent;
import com.researchspace.model.events.RecordCreatedEvent;
import com.researchspace.model.events.RecordRenameEvent;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.DocumentFieldInitializationPolicy;
import com.researchspace.model.record.DocumentInitializationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.FormUsage;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.FolderRecordPair;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.DocumentCopyManager;
import com.researchspace.service.NotificationConfig;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RecordContext;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RequiresActiveLicense;
import com.researchspace.service.UserManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** */
@Service("recordManager")
@Transactional
public class RecordManagerImpl implements RecordManager {

  private static final String RECORD_ACCESS_FAILURE_MSG =
      "This record does not exist, or you do not have permission to access it.";

  private final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired CommunicationManager commMgr;
  private @Autowired FieldDao fieldDao;
  private @Autowired FolderDao folderDao;
  private @Autowired EcatImageDao imageDao;
  private @Autowired FormUsageDao formUsageDao;
  private @Autowired RecordEditorTracker tracker;
  private @Autowired FieldLinksEntitiesSynchronizer fieldContentSynchroniser;
  private @Autowired FormDao formDao;
  private @Autowired RecordGroupSharingDao recordGroupSharingDao;
  private @Autowired RecordUserFavoritesDao recordUserFavoritesDao;
  private @Autowired IRecordFactory recordFactory;
  private @Autowired MovePermissionChecker movePermissionChecker;
  private @Autowired IPermissionUtils permissnUtils;
  private @Autowired RecordDao recordDao;
  private @Autowired UserManager userManager;
  private @Autowired UserDao userDao;
  private @Autowired AuditDao auditDao;
  private @Autowired FieldParser fieldParser;
  private @Autowired TextFieldDataSanitizer textFieldDataSanitizer;

  private @Autowired NameDateFilter folderFilter;
  private @Autowired RichTextUpdater updater;
  private @Autowired DocumentCopyManager copyMgr;
  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired OperationFailedMessageGenerator authMsgGenerator;

  @Override
  public Record get(long id) {
    return recordDao.get(id);
  }

  @Override
  public Optional<Record> getSafeNull(long id) {
    return recordDao.getSafeNull(id);
  }

  @Override
  public boolean exists(long id) {
    try {
      return recordDao.exists(id);
    } catch (DataAccessException e) { // needed for RSPAC-641
      return false;
    }
  }

  /**
   * Checks parents folders of the record for the folder belonging to the User. Omits shared folder.
   */
  @Override
  public Folder getParentFolderOfRecordOwner(Long recordId, User user) {
    for (Folder folder : get(recordId).getParentFolders()) {
      if (folder.getOwner().equals(user) && !folder.isSharedFolder()) {
        return folder;
      }
    }
    return null;
  }

  public Folder getRecordParentPreferNonShared(User user, BaseRecord original) {
    Folder parent = original.getParent();
    if (parent == null) {
      for (RecordToFolder rtf : original.getParents()) {
        if (rtf.getUserName().equals(user.getUsername())) {
          parent = rtf.getFolder();
          if (!parent.isSharedFolder()) {
            return parent;
          }
        }
      }
    }
    return parent;
  }

  /** Convenience method to get a subclass of record already cast to the appropriate type. */
  @SuppressWarnings("unchecked")
  public <T extends Record> T getAsSubclass(long id, Class<T> clazz) {
    return (T) recordDao.get(id);
  }

  public Record save(Record record, User user) {
    return recordDao.save(record);
  }

  public RecordCopyResult createFromTemplate(
      long templateId, String newname, User user, Long targetFolderId) {
    RecordCopyResult copy = copy(templateId, newname, user, targetFolderId);
    StructuredDocument createdDoc = (StructuredDocument) copy.getUniqueCopy();
    List<Field> listFields = createdDoc.getFields();
    createdDoc.setAllFieldsValid(listFields.stream().allMatch(Field::isMandatoryStateSatisfied));
    createdDoc.notifyDelta(
        DeltaType.CREATED_FROM_TEMPLATE, getTemplateCreationDeltaMsg(templateId, copy, createdDoc));
    // now update this so it is not a template
    createdDoc.removeType(RecordType.TEMPLATE);

    StructuredDocument originalTemplate = copy.getOriginalFromId(templateId).asStrucDoc();

    if (originalTemplate.isTemplate()) {
      createdDoc.setTemplateSource(originalTemplate);
    }

    recordDao.save(createdDoc);
    publisher.publishEvent(new RecordCreatedEvent(createdDoc, user));
    return copy;
  }

  // logs as json string for presentation in the UI
  private String getTemplateCreationDeltaMsg(
      long id, RecordCopyResult copy, StructuredDocument rc) {
    ObjectNode el = JacksonUtil.createObjectNode();
    el.put("deltaType", DeltaType.CREATED_FROM_TEMPLATE.name());
    el.put("globalId", copy.getOriginalFromId(id).getGlobalIdentifier());
    el.put("ownerId", copy.getOriginalFromId(id).getOwner().getId());
    el.put("ownerName", copy.getOriginalFromId(id).getOwner().getFullName());
    return JacksonUtil.toJson(el);
  }

  public boolean canMove(BaseRecord original, Folder targetParent, User user) {
    if (targetParent == null || original == null) {
      log.warn("newparent[{}] or original [{}] was null", targetParent, original);
      return false;
    }
    return movePermissionChecker.checkMovePermissions(user, targetParent, original);
  }

  public ServiceOperationResult<BaseRecord> move(
      Long id, Long targetParent, Long currParentId, User user) {
    Record toMove = get(id);
    Folder currparent = getFolderOrRecordParent(currParentId, user, toMove);
    Folder newparent = folderDao.get(targetParent);
    if (newparent != null && toMove != null) {
      if (!movePermissionChecker.checkMovePermissions(user, newparent, toMove)) {
        return new ServiceOperationResult<>(null, false);
      }
      boolean moved = toMove.move(currparent, newparent, user);
      if (moved) {
        if (currparent.isFolder()) {
          folderDao.save(currparent);
        }
        folderDao.save(newparent);
        recordDao.save(toMove);
        return new ServiceOperationResult<>(toMove, true);
      }
    }
    return new ServiceOperationResult<>(null, false);
  }

  @Override
  public ISearchResults<BaseRecord> listFolderRecords(
      Long parentId,
      PaginationCriteria<? extends BaseRecord> pgCrit,
      RecordTypeFilter recordTypefilter) {
    Validate.notNull(pgCrit, "Pagination criteria can't be null!");
    return recordDao.getPaginatedChildRecordsOfParentWithFilter(parentId, pgCrit, recordTypefilter);
  }

  @Override
  public ISearchResults<BaseRecord> listFolderRecords(
      Long parentId, PaginationCriteria<? extends BaseRecord> pgCrit) {
    return listFolderRecords(parentId, pgCrit, null);
  }

  @RequiresActiveLicense
  @Override
  public StructuredDocument createNewStructuredDocument(Long parentId, Long formID, User user) {
    return createNewStructuredDocument(parentId, formID, "", user, new DefaultRecordContext());
  }

  @RequiresActiveLicense
  @Override
  public StructuredDocument createNewStructuredDocument(
      Long parentId, Long formID, User user, RecordContext context, ImportOverride override) {
    return doCreateDocument(parentId, formID, "", user, context, override);
  }

  @RequiresActiveLicense
  @Override
  public StructuredDocument createNewStructuredDocument(
      Long parentId, Long formID, String name, User user, RecordContext context) {
    return doCreateDocument(parentId, formID, name, user, context, null);
  }

  private StructuredDocument doCreateDocument(
      Long parentId,
      Long formID,
      String name,
      User user,
      RecordContext context,
      ImportOverride override) {
    Validate.notNull(formID, "FormId can't be null!");
    Validate.notNull(user, "User can't be null!");

    Folder parentFolder = null;
    if (parentId != null) {
      parentFolder = folderDao.get(parentId);
    }
    if (!context.enableDirectTemplateCreationInTemplateFolder()
        || !(parentFolder.hasAncestorOfType(RecordType.TEMPLATE, true))) {
      permissnUtils.assertIsPermitted(
          parentFolder, PermissionType.CREATE, user, "create document in parent folder");
    }

    RSForm form = formDao.get(formID);

    if (form.isPublishedAndHidden() && !context.ignoreUnpublishedForms()) {
      log.warn("Trying to create document from hidden form, not allowed");
      return null;
    }

    String recordName = StringUtils.isNotBlank(name) ? name : StructuredDocument.DEFAULT_NAME;
    StructuredDocument rc;
    if (override == null) {
      rc = recordFactory.createStructuredDocument(recordName, user, form);
    } else {
      rc = recordFactory.createStructuredDocument(recordName, user, form, override);
    }
    List<Field> listFields = rc.getFields();
    rc.setAllFieldsValid(listFields.stream().allMatch(Field::isMandatoryStateSatisfied));

    rc.setIconId(form.getIconId());
    cleanTextFieldsContent(rc);

    // we need read permission for form, and create permission
    permissnUtils.assertIsPermitted(form, PermissionType.READ, user, "user can't read the form ");

    if (parentFolder != null) {
      recordDao.save(rc);
      parentFolder.addChild(rc, user);
      folderDao.save(parentFolder);
    }
    recordDao.save(rc);
    formDao.save(rc.getForm());
    formUsageDao.save(new FormUsage(user, form));
    // if this is just a single create-doc operation, we want to publish its event.
    // if it's part of a bigger operation (e.g. importing) we want to only publish when its
    // completed.
    // see rspac-2126
    if (!context.isRecursiveFolderOperation()) {
      publisher.publishEvent(new RecordCreatedEvent(rc, user));
    }

    return rc;
  }

  private void cleanTextFieldsContent(StructuredDocument doc) {
    for (Field field : doc.getFields()) {
      if (field.isTextField()) {
        field.setData(cleanTextHtml(field.getData()));
      }
    }
  }

  @Override
  public Snippet createSnippet(String name, String content, User user) {
    Validate.notEmpty(name, "name can't be empty");
    Validate.notEmpty(content, "content can't be empty");
    Validate.notNull(user, "user can't be null");

    Snippet snippet = recordFactory.createSnippet(name, content, user);
    recordDao.save(snippet);

    Folder snippetGaleryFolder = getGallerySubFolderForUser(Folder.SNIPPETS_FOLDER, user);
    snippetGaleryFolder.addChild(snippet, user);
    folderDao.save(snippetGaleryFolder);

    String updatedContent = copyMgr.copyElementsInContent(null, snippet, content, user);
    snippet.setContent(updatedContent);
    recordDao.save(snippet);
    return snippet;
  }

  @Override
  public EditStatus requestRecordEdit(Long recordId, User user, UserSessionTracker activeUsers) {
    return requestRecordEdit(recordId, user, activeUsers, SessionAttributeUtils::getSessionId);
  }

  @Override
  public EditStatus requestRecordEdit(
      Long recordId,
      User user,
      UserSessionTracker activeUsers,
      Supplier<String> sessionIDProvider) {
    EditStatus basicStatus = checkBasicEditStatusForRecordAndUser(recordId, user);
    if (basicStatus != null) {
      return basicStatus;
    }
    return tracker.attemptToEdit(recordId, user, activeUsers, sessionIDProvider);
  }

  @Override
  public EditStatus requestRecordView(Long recordId, User user, UserSessionTracker activeUsers) {
    EditStatus basicStatus = checkBasicEditStatusForRecordAndUser(recordId, user);
    if (basicStatus != null) {
      return basicStatus;
    }
    // check if someone else is editing
    String editor = tracker.getEditingUserForRecord(recordId);
    if (editor != null) {
      if (editor.equals(user.getUsername())) {
        return EditStatus.EDIT_MODE;
      }
      return EditStatus.CANNOT_EDIT_OTHER_EDITING;
    }
    return EditStatus.VIEW_MODE;
  }

  /**
   * Calculates EditStatus based on permissions and state of the document.
   *
   * <p>If user cannot edit the record, returns EditStatus pointing to the reason. If permissions
   * are OK, returns null.
   */
  private EditStatus checkBasicEditStatusForRecordAndUser(Long recordId, User user) {
    Record record;
    try {
      record = recordDao.get(recordId);
    } catch (DataAccessException e) {
      log.error(RECORD_ACCESS_FAILURE_MSG);
      return EditStatus.ACCESS_DENIED;
    }

    // deleted record trumps all permissions lookups.
    if (record.isDeleted()
        || record.isDeletedForUser(user)
        || record.isDeletedForUser(record.getOwner())) {
      return EditStatus.ACCESS_DENIED;
    }
    boolean isReadable =
        permissnUtils.isPermitted(
            record, PermissionType.READ, getUserWithRefreshedPermissions(user));

    if (record.isSigned()) {
      return EditStatus.CAN_NEVER_EDIT;
    }
    if (isReadable && !record.isEditable()) {
      return EditStatus.CAN_NEVER_EDIT;
    }

    boolean isEditable =
        permissnUtils.isPermitted(
            record, PermissionType.WRITE, getUserWithRefreshedPermissions(user));
    if (isReadable && !isEditable) {
      return EditStatus.CANNOT_EDIT_NO_PERMISSION;
    } else if (!isReadable && !isEditable) {
      return EditStatus.ACCESS_DENIED;
    }
    return null;
  }

  private User getUserWithRefreshedPermissions(User user) {
    if (permissnUtils.refreshCacheIfNotified()) {
      return userManager.getUserByUsername(user.getUsername(), true);
    }
    return user;
  }

  // revision history seems work OK for chem, sketches etc. using the
  // revision number in the URL which is added to links when viewing a revision.
  public void forceVersionUpdate(
      Long recordId, DeltaType deltaType, String optionalDeltaMsg, User user) {

    BaseRecord rec = recordDao.get(recordId);
    if (!rec.isStructuredDocument()) { // should never happen
      throw new IllegalStateException("Tried to force version update of non-document " + recordId);
    }
    StructuredDocument sd = (StructuredDocument) rec;
    sd.notifyDelta(deltaType, optionalDeltaMsg);
    sd.setModifiedBy(user.getUsername());
    recordDao.save(sd);
  }

  @Override
  public BaseRecord getRecordWithLazyLoadedProperties(
      long id,
      User user,
      DocumentInitializationPolicy initializationPolicy,
      boolean ignorePermissions) {
    Record record = get(id);
    assertPermissions(id, user, ignorePermissions, record);
    initializationPolicy.initialize(record);
    return record;
  }

  private void assertPermissions(long id, User user, boolean ignorePermissions, Record record) {
    if (!ignorePermissions) {
      if (!permissnUtils.isPermitted(record, PermissionType.READ, user)) {
        throw new AuthorizationException(
            "Attempt to load record with id [" + id + "] by user " + user.getId());
      }
    }
  }

  @Override
  public Optional<Record> getOptRecordWithLazyLoadedProperties(
      long id,
      User user,
      DocumentInitializationPolicy initializationPolicy,
      boolean ignorePermissions) {
    Optional<Record> record = getSafeNull(id);
    if (record.isPresent()) {
      assertPermissions(id, user, ignorePermissions, record.get());
      initializationPolicy.initialize(record.get());
    }
    return record;
  }

  @Override
  public BaseRecord getRecordWithFields(long recordId, User user) {
    return getRecordWithLazyLoadedProperties(
        recordId, user, new DocumentFieldInitializationPolicy(), false);
  }

  @Override
  public Long getModificationDate(long recordId, User user) {
    Record record = get(recordId);
    if (!permissnUtils.isPermitted(record, PermissionType.READ, user)) {
      throw new AuthorizationException(
          "Attempt to load record with id [" + recordId + "] by user " + user.getId());
    }
    return record.getModificationDate();
  }

  private String cleanTextHtml(String input) {
    return textFieldDataSanitizer.cleanData(input);
  }

  private String TextOnlyHtml(String input) {
    return textFieldDataSanitizer.textDataOnly(input);
  }

  @Override
  public StructuredDocument saveTemporaryDocument(Long currFieldId, User subject, String data) {
    Field field = fieldDao.get(currFieldId);
    return saveTemporaryDocument(field, subject, data);
  }

  @Override
  public StructuredDocument saveTemporaryDocument(Field currField, User subject, String data) {
    // collect some info as this might be slow
    StopWatch sw = new StopWatch();
    sw.start();

    if (currField.isTextField()) {
      data = cleanTextHtml(data);
    }
    if (FieldType.STRING.equals(currField.getType())) {
      data = TextOnlyHtml(data);
    }
    Long currFieldId = currField.getId();
    logAutosaveStageTime(sw, "cleaning field data", currFieldId);

    Long recordId = currField.getStructuredDocument().getId();
    StructuredDocument structuredDocument = saveTempRecord(recordId, subject);
    logAutosaveStageTime(sw, "saving temp record", recordId);

    saveTempField(data, currField, structuredDocument, subject);
    logAutosaveStageTime(sw, "saving temp field", currFieldId);

    return structuredDocument;
  }

  private StructuredDocument saveTempRecord(Long recordId, User user) {
    StructuredDocument record = (StructuredDocument) getRecordWithFields(recordId, user);
    StructuredDocument tempRecord = (StructuredDocument) record.getTempRecord();
    if (tempRecord == null) {
      tempRecord = record.copyNoFields();
      tempRecord.setTemporaryDoc(true);
    }
    Timestamp time = new Timestamp(Calendar.getInstance().getTime().getTime());
    tempRecord.setModificationDate(time);
    tempRecord.setModifiedBy(user.getUsername());
    record.setTempRecord(tempRecord);

    return (StructuredDocument) save(record, user);
  }

  // overwrites existing temp field.
  private void saveTempField(
      String newFieldData,
      Field permanentField,
      StructuredDocument structuredDocument,
      User subject) {
    // collect some info as this might be slow
    StopWatch sw = new StopWatch();
    sw.start();

    Field temp = permanentField.getTempField();
    if (temp == null) {
      temp = (Field) permanentField.shallowCopy();
    }
    logAutosaveStageTime(sw, "temp field saving: retrieving field", temp.getId());

    fieldContentSynchroniser.syncFieldWithEntitiesOnautosave(
        permanentField, temp, newFieldData, subject);
    logAutosaveStageTime(sw, "temp field saving: syncing field with entities", temp.getId());

    temp.setFieldData(newFieldData);
    temp.setModificationDate(structuredDocument.getTempRecord().getModificationDate());
    temp = fieldDao.save(temp);
    if (permanentField.getTempField() == null) {
      permanentField.setTempField(temp);
      fieldDao.save(permanentField);
    }
    logAutosaveStageTime(sw, "temp field saving: saving", temp.getId());

    fieldDao.logAutosave(temp, permanentField);
    logAutosaveStageTime(sw, "temp field saving: logging autosave", temp.getId());
  }

  private static final int AUTOSAVE_STAGE_TIME_LOG_THRESHOLD = 1000; // 1 second

  private void logAutosaveStageTime(StopWatch stopWatch, String stageDesc, Long id) {
    stopWatch.suspend();
    long stageTime = stopWatch.getTime();
    if (stageTime > AUTOSAVE_STAGE_TIME_LOG_THRESHOLD) {
      log.warn("Autosave stage '{}' for id: {} took {} millis", stageDesc, id, stageTime);
    } else {
      log.debug("Autosave stage '{}' for id: {} took {} millis", stageDesc, id, stageTime);
    }
    stopWatch.reset();
    stopWatch.start();
  }

  @Override
  @CacheEvict(cacheNames = "com.researchspace.documentPreview", key = "#structuredDocumentId")
  public FolderRecordPair saveStructuredDocument(
      long structuredDocumentId, String userName, boolean unlockRecord, ErrorList warningList)
      throws DocumentAlreadyEditedException {
    User userEditor = userDao.getUserByUsername(userName);
    StructuredDocument structuredDocument = get(structuredDocumentId).asStrucDoc();
    assertNotEditedByOther(userEditor, structuredDocument);
    permissnUtils.assertIsPermitted(
        structuredDocument, PermissionType.WRITE, userEditor, "save document");

    List<Field> listFields = structuredDocument.getFields();
    // List<FieldContentDelta> fieldChanges = new ArrayList<>();
    boolean contentChanged = false;
    for (Field field : listFields) {
      Field connectedTempField = field.getTempField();
      if (connectedTempField != null) {
        log.info("moving temp data to id: {}", field.getId());
        String tempFieldData = connectedTempField.getFieldData();

        String orgFieldData = field.getFieldData();
        contentChanged = contentChanged || !StringUtils.equals(orgFieldData, tempFieldData);

        field.setFieldData(tempFieldData);
        field.setModificationDate(connectedTempField.getModificationDate());

        long idTempField = connectedTempField.getId();
        fieldDao.remove(idTempField);
        field.setTempField(null);
      }
    }
    structuredDocument.setAllFieldsValid(
        listFields.stream().allMatch(Field::isMandatoryStateSatisfied));

    if (!contentChanged && warningList != null) {
      warningList.addErrorMsg("content.not.changed");
    }

    StructuredDocument temp = (StructuredDocument) structuredDocument.getTempRecord();
    if (temp != null) {
      structuredDocument.setModificationDate(temp.getModificationDate());
      structuredDocument.setModifiedBy(temp.getModifiedBy(), IActiveUserStrategy.CHECK_OPERATE_AS);
      structuredDocument.setTempRecord(null);
    }

    save(structuredDocument, userEditor);
    if (unlockRecord) {
      tracker.unlockRecord(structuredDocument, userEditor, SessionAttributeUtils::getSessionId);
      log.info("unlocked edit mode for document");
    }
    log.debug("fields saved");

    Folder parentFolder = retrieveParentFolder(userName, userEditor, structuredDocument);
    NotificationConfig cfg =
        NotificationConfig.builder()
            .notificationType(NOTIFICATION_DOCUMENT_EDITED)
            .broadcast(true)
            .recordAuthorisationRequired(true)
            .build();
    commMgr.notify(
        userEditor,
        structuredDocument,
        cfg,
        structuredDocument.getName() + " edited by " + userEditor.getUsername());
    return new FolderRecordPair(structuredDocument, parentFolder);
  }

  @Override
  public BaseRecord cancelStructuredDocumentAutosavedEdits(
      long structuredDocumentId, String userName) throws DocumentAlreadyEditedException {

    User userEditor = userDao.getUserByUsername(userName);
    StructuredDocument structuredDocument = (StructuredDocument) get(structuredDocumentId);
    assertNotEditedByOther(userEditor, structuredDocument);

    List<Field> listFields = structuredDocument.getFields();
    List<FieldContentDelta> fieldChanges = new ArrayList<>();
    for (Field field : listFields) {
      if (field.getTempField() != null) {
        Field tempField = field.getTempField();
        FieldContentDelta fieldDelta =
            fieldContentSynchroniser.revertSyncFieldWithEntitiesOnCancel(field, tempField);
        fieldChanges.add(fieldDelta);
        long idTempField = tempField.getId();
        fieldDao.remove(idTempField);
        field.setTempField(null);
        if (field.isTextField()) {
          // now we need to see if any sketches or chem elements were updated
          AuditedEntity<StructuredDocument> latestDoc =
              auditDao.getNewestRevisionForEntity(StructuredDocument.class, structuredDocumentId);
          // now, are there any newer revisions?
          if (latestDoc != null) {
            updater.updateLinksWithRevisions(field, latestDoc.getRevision().intValue());
          }
        }
      }
    }
    fieldContentSynchroniser.revertSyncDocumentWithEntitiesOnCancel(
        structuredDocument, fieldChanges);
    StructuredDocument temp = (StructuredDocument) structuredDocument.getTempRecord();
    if (temp != null) {
      structuredDocument.setTempRecord(null);
    }
    structuredDocument.notifyDelta(DeltaType.NOREVISION_UPDATE);
    save(structuredDocument, userEditor);
    log.debug("fields saved");

    tracker.unlockRecord(structuredDocument, userEditor, SessionAttributeUtils::getSessionId);
    log.info("unlocked edit mode for document");

    return retrieveParentFolder(userName, userEditor, structuredDocument);
  }

  private void assertNotEditedByOther(User userEditor, StructuredDocument structuredDocument)
      throws DocumentAlreadyEditedException {
    Optional<String> isEditing = tracker.isEditing(structuredDocument);
    if (isEditing.isPresent() && !isEditing.get().equals(userEditor.getUsername())) {
      throw new DocumentAlreadyEditedException("Already edited by " + isEditing);
    }
  }

  private Folder retrieveParentFolder(
      String userName, User u, StructuredDocument structuredDocument) {
    Folder parentFolder;
    parentFolder = structuredDocument.getParent();
    if (parentFolder == null) {
      for (RecordToFolder rtf : structuredDocument.getParents()) {
        if (rtf.getUserName().equals(userName)) {
          parentFolder = rtf.getFolder();
          break;
        }
      }
    }
    if (parentFolder == null) {
      parentFolder = u.getRootFolder();
      parentFolder.isFolder(); // initialize
    }
    return parentFolder;
  }

  // code review finished here on 02/10/2015

  @Override
  public void unlockRecord(Record record, User user) {
    tracker.unlockRecord(record, user, SessionAttributeUtils::getSessionId);
  }

  @Override
  public void unlockRecord(Long recordId, String username) {
    unlockRecord(recordId, username, SessionAttributeUtils::getSessionId);
  }

  @Override
  public void unlockRecord(Long recordId, String username, Supplier<String> sessionIdProvider) {
    if (recordDao.exists(recordId)) {
      tracker.unlockRecord(
          recordDao.get(recordId), userDao.getUserByUsername(username), sessionIdProvider);
    }
  }

  @Override
  public StructuredDocument createTemplateFromDocument(
      Long docId, List<Long> fieldIds, User user, String templateName)
      throws DocumentAlreadyEditedException {

    saveStructuredDocument(docId, user.getUsername(), true, null);
    StructuredDocument sd = (StructuredDocument) getRecordWithFields(docId, user);
    if (sd == null) {
      log.warn("Cannot load the structured document with id={}", docId);
      return null;
    }
    List<Field> sdFields = sd.getFields();

    Folder templateRoot = folderDao.getTemplateFolderForUser(user);
    if (templateRoot == null) { // TODO temporary till all moved RSPAC-921
      templateRoot = getGallerySubFolderForUser(Folder.TEMPLATE_MEDIA_FOLDER_NAME, user);
    }

    StructuredDocument template =
        (StructuredDocument) copy(docId, templateName, user, templateRoot.getId()).getCopy(sd);
    if (template == null) {
      log.warn("ERROR: Cannot  copy structured document with id={}", docId);
      return null;
    }
    List<Field> fields = template.getFields();
    for (int j = fields.size() - 1; j >= 0; j--) {
      // this will cause an error if no fields!!
      Long fidx = sdFields.get(j).getId();
      if (!fieldIds.contains(fidx)) {
        Field fldx = fields.get(j);
        fldx.setData("");
      }
    }

    template.addType(RecordType.TEMPLATE);
    save(template, user);
    publisher.publishEvent(new RecordCreatedEvent(template, user));
    return template;
  }

  @Override
  public Folder getGallerySubFolderForUser(String folderName, User user)
      throws IllegalAddChildOperation {
    Folder systemFolder = folderDao.getSystemFolderForUserByName(user, folderName);
    if (systemFolder != null) {
      return systemFolder;
    } else {
      // create if not present
      Folder galleryRoot = folderDao.getGalleryFolderForUser(user);

      Folder newFolder = recordFactory.createSystemCreatedFolder(folderName, user);
      folderDao.save(newFolder);

      RecordToFolder rtf = galleryRoot.addChild(newFolder, user);
      if (rtf == null) {
        log.warn(
            "trying to add an item which would produce cycle in folder tree! - adding [{}] to [{}]",
            newFolder.getName(),
            galleryRoot.getName());
        return null;
      }
      folderDao.save(galleryRoot);
      folderDao.save(newFolder);

      return newFolder;
    }
  }

  @Override
  @RequiresActiveLicense
  public StructuredDocument createBasicDocument(Long parentId, User user) {
    Folder parentFolder = folderDao.get(parentId);
    assertCreatePermission(user, parentFolder);
    RSForm basicDcForm = formDao.getBasicDocumentForm();
    if (basicDcForm != null) {
      return createNewStructuredDocument(parentId, basicDcForm.getId(), user);
    }
    return null;
  }

  @Override
  @RequiresActiveLicense
  public StructuredDocument createBasicDocumentWithContent(
      Long parentId, String name, User user, String htmlContent) {
    name = sanitizeNewRecordName(name);
    Folder parentFolder = folderDao.get(parentId);
    assertCreatePermission(user, parentFolder);
    RSForm basicDcForm = formDao.getBasicDocumentForm();
    if (basicDcForm != null) {
      StructuredDocument sd = createNewStructuredDocument(parentId, basicDcForm.getId(), user);
      sd.getFields().get(0).setFieldData(htmlContent);
      sd.setName(name);
      sd = save(sd, user).asStrucDoc();
      return sd;
    }
    return null;
  }

  private void assertCreatePermission(User user, Folder parentFolder) {
    if (!permissnUtils.isPermitted(parentFolder, PermissionType.CREATE, user)) {
      throw new AuthorizationException();
    }
  }

  @Override
  public List<Long> getDescendantRecordIdsExcludeFolders(Long parentId) {
    return recordDao.getNotebookContentsExcludeFolders(parentId);
  }

  @Override
  public List<Record> getLoadableNotebookEntries(User user, Long notebookId) {
    List<Long> allRecords = getDescendantRecordIdsExcludeFolders(notebookId);
    List<Record> readableRecords = new ArrayList<>();
    for (Long recordId : allRecords) {
      Record currentRecord = get(recordId);
      if (isLoadableEntry(user, currentRecord)) {
        readableRecords.add(currentRecord);
      }
    }
    return readableRecords;
  }

  private boolean isLoadableEntry(User user, Record currentRecord) {
    return currentRecord != null
        && currentRecord.isStructuredDocument()
        && !((StructuredDocument) currentRecord).isTemporaryDoc()
        && permissnUtils.isPermitted(currentRecord, PermissionType.READ, user);
  }

  public boolean renameRecord(String newname, Long toRenameId, User user) {
    if (StringUtils.isBlank(newname)) {
      throw new IllegalArgumentException(
          String.format("New name cannot be empty but was [%s]", newname));
    }
    newname = sanitizeNewRecordName(newname);
    boolean isRecord = isRecord(toRenameId);
    BaseRecord toSave;
    if (isRecord) {
      toSave = get(toRenameId);
    } else {
      toSave = folderDao.get(toRenameId);
    }
    if (toSave.isSigned() || toSave.hasType(RecordType.SYSTEM)) {
      return false;
    }
    String oldname = toSave.getName();
    // no point continuing if newname is same as old or is a system folder
    if (newname.equals(oldname)) {
      // return success even though no further action was required (false returned only on failure)
      return true;
    }
    if (!permissnUtils.isPermitted(toSave, PermissionType.RENAME, user)) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(
              user, format(" attempted rename of %s [id=%d]", toSave.getName(), toSave.getId())));
    }
    // only get here if name OK, has permission, and is not a system folder.
    toSave.setName(newname);
    toSave.setModificationDate(new Date());
    toSave.setModifiedBy(user.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    if (isRecord) {
      toSave = save((Record) toSave, user);
    } else if (toSave.isFolder()) {
      toSave = folderDao.save((Folder) toSave);
    }
    publisher.publishEvent(new RecordRenameEvent(toSave, oldname, newname));
    return true;
  }

  /**
   * Checks provided name and modifies it to be acceptable record name by abbreviating/removing
   * newlines etc.
   *
   * @param newname proposed new record name
   * @return sanitized new record name
   */
  private String sanitizeNewRecordName(String newname) {
    // remove newlines re SUPPORT-33
    return abbreviate(trim(newname), DEFAULT_VARCHAR_LENGTH).replaceAll("\n", " ");
  }

  // convenience method to ascertain if we're looking for a record or a folder
  private boolean isRecord(Long id) {
    return recordDao.isRecord(id);
  }

  @Override
  public void removeFromEditorTracker(String sessionId) {
    tracker.removeLockedRecordInSession(sessionId);
  }

  @Override
  public List<RSpaceDocView> getAllFrom(Set<Long> dbids) {
    if (CollectionUtils.isEmpty(dbids)) {
      throw new IllegalArgumentException("List of ids to retrieve is empty!");
    }
    return recordDao.getRecordViewsById(dbids);
  }

  @Override
  public ISearchResults<BaseRecord> getGalleryItems(
      Long parentId,
      PaginationCriteria<BaseRecord> pgCrit,
      GalleryFilterCriteria galleryFilter,
      RecordTypeFilter recordTypefilter,
      User user) {

    if (galleryFilter != null && galleryFilter.isEnabled()) {
      String searchTerm = galleryFilter.getRawName();
      if (GlobalIdentifier.isValid(searchTerm) && isGalleryItem(searchTerm)) {
        GlobalIdentifier identifier = new GlobalIdentifier(searchTerm);
        BaseRecord galleryItem = getSafeNull(identifier.getDbId()).orElse(null);
        boolean permitted = permissnUtils.isPermitted(galleryItem, PermissionType.READ, user);
        if (galleryItem == null || !permitted) {
          return new SearchResultsImpl<>(Collections.emptyList(), pgCrit, 0L);
        }
        return new SearchResultsImpl<>(TransformerUtils.toList(galleryItem), pgCrit, 1L);
      } else {
        WorkspaceListingConfig input = new WorkspaceListingConfig(pgCrit, parentId, galleryFilter);
        return folderFilter.match(input);
      }
    } else {
      return listFolderRecords(parentId, pgCrit, recordTypefilter);
    }
  }

  private boolean isGalleryItem(String searchTerm) {
    GlobalIdentifier identifier = new GlobalIdentifier(searchTerm);
    return GlobalIdPrefix.GL.equals(identifier.getPrefix())
        || GlobalIdPrefix.ST.equals(identifier.getPrefix());
  }

  @Override
  public ISearchResults<BaseRecord> getFilteredRecords(
      WorkspaceFilters filters, PaginationCriteria<BaseRecord> paginationCriteria, User user) {

    List<BaseRecord> hits = getFilteredRecordsList(filters, user);
    if (paginationCriteria.getOrderBy() == null) {
      paginationCriteria.setOrderBy(SearchUtils.BASE_RECORD_ORDER_BY_LAST_MODIFIED);
    }

    hits = SearchUtils.sortList(hits, paginationCriteria);

    if (!hits.isEmpty()) {
      ISearchResults<BaseRecord> rdsx =
          new SearchResultsImpl<>(hits, paginationCriteria.getPageNumber().intValue(), hits.size());
      int totalHits = rdsx.getResults().size();
      return Repaginator.repaginateResults(paginationCriteria, rdsx, totalHits);
    }
    return SearchResultsImpl.emptyResult(paginationCriteria);
  }

  @Override
  public List<BaseRecord> getFilteredRecordsList(WorkspaceFilters filters, User user) {
    List<BaseRecord> hits = new ArrayList<>();
    boolean init = false;

    if (filters.isOntologiesFilter()) {
      List<BaseRecord> sharedOntologies =
          recordGroupSharingDao.getOntologiesFilesSharedWithUser(user);
      List<BaseRecord> ownOntologies = recordDao.getOntologyFilesOwnedByUser(user);
      hits.addAll(sharedOntologies);
      hits.addAll(ownOntologies);
      init = true;
    }

    if (filters.isSharedFilter()) {
      List<BaseRecord> sharedRecords = recordGroupSharingDao.getSharedRecordsWithUser(user);
      hits.addAll(sharedRecords);
      init = true;
    }

    if (filters.isFavoritesFilter()) {
      List<BaseRecord> favoritesRecords =
          recordUserFavoritesDao.getFavoriteRecordsByUser(user.getId());
      init = addOrRetain(hits, init, favoritesRecords);
    }

    if (filters.isViewableItemsFilter() || filters.isTemplatesFilter()) {
      List<BaseRecord> viewableItemsRecords =
          getViewableRecordsByRole(user, filters.isTemplatesFilter());
      init = addOrRetain(hits, init, viewableItemsRecords);
    }

    if (filters.isMediaFilesFilter()) {
      Set<BaseRecord> mediaFiles = recordDao.getViewableMediaFiles(List.of(user));
      init = addOrRetain(hits, init, mediaFiles);

      String mediaType = filters.getMediaFilesType();
      if (mediaType != null) {
        boolean imageFilter = "image".equalsIgnoreCase(mediaType);
        boolean documentFilter = "document".equalsIgnoreCase(mediaType);
        boolean avFilter = "av".equalsIgnoreCase(mediaType);
        boolean chemFilter = "chemistry".equalsIgnoreCase(mediaType);

        List<BaseRecord> matchingTypeHits = new ArrayList<>();
        for (BaseRecord hit : hits) {
          if (imageFilter && (hit instanceof EcatImage)
              || documentFilter && (hit instanceof EcatDocumentFile)
              || chemFilter && (hit instanceof EcatChemistryFile)
              || avFilter && (((EcatMediaFile) hit).isAV())) {
            matchingTypeHits.add(hit);
          }
        }
        hits.retainAll(matchingTypeHits);
      }
    }

    // documents filter should be combined with some other filter, as it only removes non-documents
    if (filters.isDocumentsFilter()) {
      List<BaseRecord> documentHits = new ArrayList<>();
      for (BaseRecord hit : hits) {
        if (hit.isStructuredDocument()) {
          documentHits.add(hit);
        }
      }
      hits.retainAll(documentHits);
    }
    return hits;
  }

  /* If list is not initialised, then adds all newRecords, otherwise retains newRecords */
  private boolean addOrRetain(
      List<BaseRecord> list, boolean init, Collection<BaseRecord> newRecords) {
    if (init) {
      list.retainAll(newRecords);
    } else {
      list.addAll(newRecords);
    }
    return true;
  }

  private Folder getFolderOrRecordParent(Long parentId, User user, Record original) {
    Folder currparent;
    if (parentId == null) {
      currparent = getRecordParentPreferNonShared(user, original);
    } else {
      currparent = folderDao.get(parentId);
    }
    return currparent;
  }

  /**
   * Retrieve all viewable documents by specific user (Role).
   *
   * @param subject the user whose viewable records we are retrieving
   */
  private List<BaseRecord> getViewableRecordsByRole(User subject, boolean onlyTemplates) {
    Set<BaseRecord> viewableRecords = new HashSet<>();
    if (subject.hasRole(Role.SYSTEM_ROLE)) {
      /*
       * Role.SYSTEM_ROLE. Get all users in the system and retrieve
       * all the documents/items which SysAdmin can see/read
       * regardless of the permission.
       */
      List<User> users = userDao.getUsers();
      Set<BaseRecord> allUsersRecords =
          new HashSet<>(getViewableRecordsByUsers(users, onlyTemplates));
      viewableRecords.addAll(allUsersRecords);
    } else if (subject.hasRole(Role.ADMIN_ROLE)
        || subject.hasRole(Role.PI_ROLE)
        || subject.hasRole(Role.USER_ROLE)) {

      /*
       * userDao.getViewableUsersByRole(user):
       *
       * Role.ADMIN_ROLE (Community admin) Get all users in the community
       * and retrieve all the documents/items which the community admin
       * can see regardless to the permissions. + shared documents with
       * specific user.
       *
       * Role.PI_ROLE (Group PI) Get all group members where user has PI.
       * Retrieve all the documents which user is owner. + shared
       * documents with specific user.
       *
       * Role.USER_ROLE Retrieve documents which user owns + shared
       * documents with specific user.
       *
       */
      List<User> viewableUsers = userDao.getViewableUsersByRole(subject);

      // records shared with the subject
      Set<BaseRecord> subjectsViewableRecords =
          new HashSet<>(getRecordsSharedWithUser(subject, onlyTemplates, subject));

      // records created by each of the subjects viewable users
      for (User viewableUser : viewableUsers) {
        subjectsViewableRecords.addAll(
            getViewableRecordsByUsers(List.of(viewableUser), onlyTemplates));
        if (viewableUser.hasRole(Role.PI_ROLE)) {
          // if the subject is a PI of group A, which contains another PI as a group member, they
          // can only view the docs of the other PI which has been shared with group A (whereas the
          // subject PI can view ALL records created by a non-PI), so filter the other PIs records
          // by their permissions to ensure correct visibility
          subjectsViewableRecords =
              subjectsViewableRecords.stream()
                  .filter(br -> permissnUtils.isPermitted(br, PermissionType.READ, subject))
                  .collect(toCollection(HashSet::new));
        }
      }
      viewableRecords.addAll(subjectsViewableRecords);
    }
    return new ArrayList<>(viewableRecords);
  }

  private List<BaseRecord> getRecordsSharedWithUser(User u, boolean onlyTemplates, User subject) {
    if (onlyTemplates) {
      return recordGroupSharingDao.getSharedTemplatesWithUser(u);
    }
    return permissnUtils.filter(
        recordGroupSharingDao.getSharedRecordsWithUser(u), PermissionType.READ, subject);
  }

  private Set<BaseRecord> getViewableRecordsByUsers(List<User> users, boolean onlyTemplates) {
    if (onlyTemplates) {
      return recordDao.getViewableTemplatesForUsers(users);
    }
    return recordDao.getViewableRecordsForUsers(users);
  }

  @Override
  public Set<BaseRecord> getViewableTemplates(List<User> users) {
    return recordDao.getViewableTemplatesForUsers(users);
  }

  @Override
  public RecordCopyResult copy(
      long originalId, String newname, User user, Long targetFolderId, RecordContext context) {
    Record original = recordDao.get(originalId);
    if (original.isTemplate()) {
      permissnUtils.assertIsPermitted(
          original, PermissionType.READ, user, "Attempt to read record");
    } else {
      permissnUtils.assertIsPermitted(
          original, PermissionType.COPY, user, "Attempt to copy record");
    }

    Folder targetFolder = getFolderOrRecordParent(targetFolderId, user, original);
    RecordCopyResult result = copyMgr.copy(original, newname, user, targetFolder);
    // if this is just a single create-doc operation, we want to publish its event.
    // if it's part of a bigger operation (e.g. importing) we want to only publish
    // when its completed.
    // see rspac-2126
    if (!context.isRecursiveFolderOperation()) {
      publisher.publishEvent(new RecordCopyEvent(result, user));
    }
    return result;
  }

  @Override
  public RecordCopyResult copy(long originalId, String newname, User user, Long targetFolderId) {
    DefaultRecordContext context = new DefaultRecordContext(false);
    return copy(originalId, newname, user, targetFolderId, context);
  }

  @Override
  public String copySnippetIntoField(Long snippetId, Long fieldId, User user) {
    Snippet snippet = getAsSubclass(snippetId, Snippet.class);
    permissnUtils.assertIsPermitted(snippet, PermissionType.COPY, user, "use snippet");
    return copyRSpaceContentIntoField(snippet.getContent(), fieldId, user);
  }

  @Override
  public String copyRSpaceContentIntoField(String content, Long fieldId, User user) {
    StructuredDocument document = fieldDao.get(fieldId).getStructuredDocument();
    permissnUtils.assertIsPermitted(
        document, PermissionType.WRITE, user, "copy content into field");
    return copyMgr.copyElementsInContent(fieldId, document, content, user);
  }

  @Override
  public EcatImage getEcatImage(Long id, boolean loadImageBytes) {
    EcatImage image = imageDao.get(id);
    if (loadImageBytes) {
      if (image.getImageThumbnailed() != null) {
        image.getImageThumbnailed().getData();
      }
      if (image.getWorkingImage() != null) {
        image.getWorkingImage().getData();
      }
    }
    return image;
  }

  @Override
  public List<Record> getAuthorisedRecordsById(
      List<Long> ids, User subject, PermissionType permissionType) {
    return recordDao.getRecordsById(ids).stream()
        .filter(record -> permissnUtils.isRecordAccessPermitted(subject, record, permissionType))
        .collect(Collectors.toList());
  }

  @Override
  public Long getRecordCountForUser(RecordTypeFilter recordTypes, User user) {
    return recordDao.getRecordCountForUser(recordTypes, user);
  }

  @Override
  public List<BaseRecord> getOntologyTagsFilesForUserCalled(
      User user, String userTagsontologyDocument) {
    return recordDao.getOntologyTagsFilesForUserCalled(user, userTagsontologyDocument);
  }

  @Override
  public List<StructuredDocument> getontologyDocumentsCreatedInPastThirtyMinutesByCurrentUser(
      String uName) {
    return recordDao.getontologyDocumentsCreatedInPastThirtyMinutesByCurrentUser(uName);
  }
}
