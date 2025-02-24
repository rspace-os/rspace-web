package com.researchspace.service.impl;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.AuditDao;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.dtos.RevisionSearchCriteria;
import com.researchspace.model.events.RestoreDeletedEvent;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RestoreDeletedItemResult;
import com.researchspace.service.UserManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;

@Service("auditManager")
public class AuditManagerImpl implements AuditManager {

  private @Autowired AuditDao auditDao;
  private @Autowired RecordDao recordDao;
  private @Autowired FieldDao fieldDao;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired OperationFailedMessageGenerator messages;
  private @Autowired FolderDao folderDao;
  private @Autowired UserManager userMgr;
  private @Autowired RichTextUpdater richTextUpdater;
  private @Autowired BaseRecordAdaptable baseRecordAdapter;
  private @Autowired ApplicationEventPublisher publisher;

  private final Logger log = LoggerFactory.getLogger(getClass());

  public ISearchResults<AuditedRecord> getDeletedDocuments(
      User user, String searchTerm, PaginationCriteria<AuditedRecord> pgCrit) {
    ISearchResults<AuditedRecord> res =
        auditDao.getRestorableDeletedRecords(user, searchTerm, pgCrit);
    sortResults(pgCrit, res.getResults());
    return res;
  }

  private void sortResults(PaginationCriteria<AuditedRecord> pgCrit, List<AuditedRecord> rc) {
    Comparator<AuditedRecord> cmp = null;
    if (pgCrit != null) {
      if ("modificationDate".equals(pgCrit.getOrderBy())) {
        cmp = AuditedRecord.MODIFICATION_COMPARATOR;
      }
      if ("creationDate".equals(pgCrit.getOrderBy())) {
        cmp = AuditedRecord.CREATION_COMPARATOR;
      }
      if ("name".equals(pgCrit.getOrderBy())) {
        cmp = AuditedRecord.NAME_COMPARATOR;
      }
      if ("deletedDate".equals(pgCrit.getOrderBy())) {
        cmp = AuditedRecord.DELETED_COMPARATOR;
      }
    }
    if (cmp != null) {
      if (SortOrder.DESC.equals(pgCrit.getSortOrder())) {
        Collections.sort(rc, Collections.reverseOrder(cmp));
      } else {
        Collections.sort(rc, cmp);
      }
    }
  }

  @Override
  public List<AuditedRecord> getHistory(
      StructuredDocument current, PaginationCriteria<AuditedRecord> pgCrit) {
    return auditDao.getRevisionsForDocument(current, pgCrit);
  }

  @Override
  public AuditedRecord getDocumentRevisionOrVersion(
      StructuredDocument current, Number revision, Number userVersion) {
    if (revision == null && userVersion == null) {
      throw new IllegalArgumentException("Either revision or version must be provided");
    }
    Number requestedRevision = revision;
    if (revision == null) {
      requestedRevision = getRevisionNumberForDocumentVersion(current.getId(), userVersion);
    }
    AuditedRecord auditRecord = auditDao.getDocumentForRevision(current, requestedRevision);
    StructuredDocument revisionDoc = auditRecord.getRecordAsDocument();
    revisionDoc.setParents(current.getParents()); // RSPAC-1304
    // need to get fields from current doc so can set in field forms, RSPAC-1623
    loadUpLazyLoadedFields(revisionDoc, recordDao.get(current.getId()).asStrucDoc());
    return auditRecord;
  }

  @Override
  public Number getRevisionNumberForDocumentVersion(Long docId, Number userVersion) {
    List<AuditedEntity<StructuredDocument>> docRevisionList =
        auditDao.getRevisionsForDocumentVersion(docId, userVersion);
    if (docRevisionList.isEmpty()) {
      throw new IllegalArgumentException(
          "Version " + userVersion + " not found for record " + docId);
    }
    return docRevisionList.get(0).getRevision().intValue();
  }

  @Override
  public AuditedRecord getMediaFileVersion(EcatMediaFile media, Number version) {
    if (version == null) {
      throw new IllegalArgumentException("Version must be provided");
    }
    List<AuditedEntity<EcatMediaFile>> entities =
        auditDao.getRevisionsForMediaFileVersion(media.getId(), version);
    if (entities.isEmpty()) {
      return null;
    }
    AuditedEntity<EcatMediaFile> auditedEntity = entities.get(0);
    AuditedRecord auditedRecord =
        new AuditedRecord(auditedEntity.getEntity(), auditedEntity.getRevision());
    auditedRecord.getEntity().setParents(media.getParents()); // RSPAC-1304
    return auditedRecord;
  }

  @Override
  public Number getRevisionNumberForMediaFileVersion(Long mediaId, Number version) {
    List<AuditedEntity<EcatMediaFile>> docRevisionList =
        auditDao.getRevisionsForMediaFileVersion(mediaId, version);
    if (docRevisionList.isEmpty()) {
      throw new IllegalArgumentException(
          "Version " + version + " not found for media file " + mediaId);
    }
    return docRevisionList.get(0).getRevision().intValue();
  }

  private void loadUpLazyLoadedFields(StructuredDocument revision, StructuredDocument current) {
    revision.getOwner().getUsername();
    // all revisions of a doc should have same field count  RSPAC-1623
    // even if form is subsequently edited to have more or less forms.
    for (int i = 0; i < revision.getFieldCount(); i++) {
      Field f = revision.getFields().get(i);
      f.setFieldForm(current.getFields().get(i).getFieldForm());
    }
  }

  @Override
  public AuditedEntity<EcatMediaFile> getRevisionForMediaFile(
      EcatMediaFile mediaFile, Number revision) {

    AuditedEntity<EcatMediaFile> auditedMedia =
        auditDao.getObjectForRevision(EcatMediaFile.class, mediaFile.getId(), revision);

    if (auditedMedia != null) {
      // initialise lazy fields needed for audited media file
      EcatMediaFile auditedMediaFile = auditedMedia.getEntity();
      auditedMediaFile.getFileProperty().getAbsolutePathUri();
      auditedMediaFile.getOwner().getUsername();

      if (auditedMediaFile.isEcatDocument()) {
        EcatDocumentFile doc = (EcatDocumentFile) auditedMediaFile;
        if (doc.getThumbNail() != null) {
          doc.getThumbNail().getId();
        }
      }
    }
    return auditedMedia;
  }

  public StructuredDocument restoreRevisionAsCurrent(Number revision, Long currentDocId) {
    StructuredDocument currentDoc = (StructuredDocument) recordDao.get(currentDocId);
    if (currentDoc.isSigned()) {
      User subject = userMgr.getAuthenticatedUserInSession();
      String msg =
          messages.getFailedMessage(
              subject.getUsername(), "restore an earlier revision of a signed document.");
      throw new AuthorizationException(msg);
    }
    AuditedRecord asd = auditDao.getDocumentForRevision(currentDoc, revision);

    StructuredDocument toRestore = asd.getRecordAsDocument();
    // clear previous delta - this must be BEFORE we notify a new delta.
    toRestore.clearDelta();
    // now, we create our own delta, since a restored document is a notifiable change.
    toRestore.setModificationDate(Instant.now().toEpochMilli());
    toRestore.setUserVersion(currentDoc.getUserVersion());
    toRestore.notifyDelta(DeltaType.RESTORED);
    // PRT-385 added to prevent access of temp record ids that don't exist in the db
    if (toRestore.getTempRecord() != null) {
      toRestore.setTempRecord(null);
    }

    List<Field> restored = toRestore.getFields();
    List<Field> original = currentDoc.getFields();

    int indx = 0;
    for (Field field : restored) {
      // forms aren't handle well by envers due to inheritance
      // this is ok since all versions of a document will use the same form.
      field.setFieldForm(original.get(indx).getFieldForm());
      // try updating field links with revisions....
      if (field.isTextField()
          && richTextUpdater.updateLinksWithRevisions(field, revision.intValue())) {
        fieldDao.getFieldAttachments(field.getId()).forEach(fa -> fa.setDeleted(false));
        fieldDao.save(field);
      }
      indx++;
    }

    // now replace current with revision;
    return (StructuredDocument) recordDao.save(toRestore);
  }

  @Override
  public Integer getNumRevisionsForDocument(
      Long currentDocId, RevisionSearchCriteria searchCriteria) {
    StructuredDocument currentDoc = (StructuredDocument) recordDao.get(currentDocId);
    return auditDao.getRevisionCountForDocument(currentDoc, searchCriteria);
  }

  @Override
  public AuditedRecord restoredDeletedForView(Long deletedId) {
    StructuredDocument asd = (StructuredDocument) recordDao.get(deletedId);
    loadUpLazyLoadedFields(asd, asd);
    return new AuditedRecord(asd, null);
  }

  BaseRecord doRestoreRecord(BaseRecord asd, User u, RestoreDeletedItemResult result) {
    // first of all we find child records and undelete those if we're the owner
    if (!asd.isFolder()) {
      // this is a runtime exception that will rollback the transaction.
      if (!permUtils.isPermitted((Record) asd, PermissionType.READ, u)) {
        throw new AuthorizationException(
            "Could not restore record - either it doesn't exist or you lack access permissions");
      }
    }
    List<Long> ids = new ArrayList<Long>();

    // now undelete the record too
    ids.add(asd.getId());
    asd.setRecordDeleted(false);
    asd.setModifiedBy(u.getUsername(), IActiveUserStrategy.CHECK_OPERATE_AS);
    // if we're a top level record:

    markRecordUndeleted(asd, u);
    if (asd.isFolder()) {
      folderDao.save((Folder) asd);
    } else {
      recordDao.save((Record) asd);
    }
    result.addItem(asd);
    return asd;
  }

  protected void markRecordUndeleted(BaseRecord record, User user) {
    boolean isOwner = user.isOwnerOfRecord(record);
    // now we undelete in any shared records.
    for (RecordToFolder r2f : record.getParents()) {
      // if user is owner, we want to restore view of everyone who can see it.
      if (isOwner) {
        r2f.markRecordInFolderDeleted(false);
      }
      // else if user has just deleted the view, then only container needs undeleting.
      else if (r2f.getFolder().getOwner().equals(user)) {
        r2f.markRecordInFolderDeleted(false);
      }
    }
  }

  @Override
  public RestoreDeletedItemResult fullRestore(Long deletedId, User user) {
    RestoreDeletedItemResult result = null;
    try {
      Record asd = recordDao.get(deletedId);
      result = new RestoreDeletedItemResult(asd);
      // to do check ofr authorisation
      doRestoreRecord(asd, user, result);
    } catch (ObjectRetrievalFailureException e) {
      log.info("Could not retrieve record [{}], assuming is folder", deletedId);
      Folder folder = folderDao.get(deletedId);
      result = new RestoreDeletedItemResult(folder);
      doRestore(folder, user, result);
    }
    publisher.publishEvent(new RestoreDeletedEvent(result, user));
    return result;
  }

  private BaseRecord doRestore(Folder folder, User user, RestoreDeletedItemResult result) {

    folder.setRecordDeleted(false);
    markRecordUndeleted(folder, user);

    for (RecordToFolder rtf : folder.getChildren()) {
      doRestoreRecord(rtf.getRecord(), user, result);
      if (rtf.getRecord().isFolder()) {
        doRestore((Folder) rtf.getRecord(), user, result);
      }
    }

    folderDao.save(folder);
    auditDao.updateDeletedFolderAsRestored(folder.getId());
    result.addItem(folder);
    return folder;
  }

  @Override
  public <T> List<AuditedEntity<T>> getRevisionsForEntity(Class<T> cls, Long primaryKey) {
    return auditDao.getRevisionsForObject(cls, primaryKey);
  }

  @Override
  public <T> AuditedEntity<T> getObjectForRevision(Class<T> cls, Long primaryKey, Number revision) {
    return auditDao.getObjectForRevision(cls, primaryKey, revision);
  }

  @Override
  public <T extends IFieldLinkableElement> AuditedEntity<T> getLinkableElementForRevision(
      Class<T> cls, Long primaryKey, Number revision) {
    AuditedEntity<T> element = auditDao.getObjectForRevision(cls, primaryKey, revision);
    baseRecordAdapter.getAsBaseRecord(element.getEntity());
    return element;
  }

  @Override
  public List<EcatCommentItem> getCommentItemsForCommentAtDocumentRevision(
      Long commentId, Integer revision) {
    return auditDao.getCommentItemsForCommentAtDocumentRevision(commentId, revision);
  }

  @Override
  public <T> AuditedEntity<T> getNewestRevisionForEntity(Class<T> clazz, Long objectId) {
    return auditDao.getNewestRevisionForEntity(clazz, objectId);
  }
}
