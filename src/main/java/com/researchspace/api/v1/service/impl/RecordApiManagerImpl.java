package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiField;
import com.researchspace.api.v1.service.ApiFieldContent;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.api.v1.service.RecordApiManager;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.audittrail.RenameAuditEvent;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.FolderRecordPair;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.impl.RecordEditorTracker;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/*
 * Ideally methods should run in a single transaction for efficiency
 * But, in this case, when creating a document with content, a temporary document is created
 *  and not removed, which breaks the document listing
 */
@Component
public class RecordApiManagerImpl implements RecordApiManager {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private @Autowired RecordManager recordManager;
  private @Autowired FolderManager folderManager;
  private @Autowired RecordEditorTracker recordEditorTracker;
  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired ApiFieldsHelper apiFieldsHelper;
  private @Autowired @Setter AuditTrailService auditTrailService;
  @Autowired private DocumentTagManager documentTagManager;

  @Override
  public Long createNewDocument(ApiDocument apiDocument, RSForm docForm, User user) {

    Folder targetFolder =
        folderManager.getApiUploadTargetFolder("", user, apiDocument.getParentFolderId());
    StructuredDocument result =
        createNewDocumentInTargetLocation(
            user, docForm, apiDocument.getName(), targetFolder.getId());

    try {
      saveApiDocumentChangesToStructuredDocument(apiDocument, user, result);
    } catch (DocumentAlreadyEditedException e) {
      // should never happen for a new document
      throw new IllegalStateException("new document, but being edited?", e);
    }

    return result.getId();
  }

  /**
   * Creates document in target location, or throws exception if user can't access target location
   * or chosen form.
   */
  private StructuredDocument createNewDocumentInTargetLocation(
      User user, RSForm docForm, String name, Long targetFolderId) {
    StructuredDocument createdDoc = null;
    try {
      createdDoc =
          recordManager.createNewStructuredDocument(
              targetFolderId, docForm.getId(), name, user, new DefaultRecordContext());
      if (createdDoc == null) {
        throw new IllegalArgumentException(createNotFoundMessage("Form", docForm.getId()));
      }
    } catch (AuthorizationException ae) {
      SECURITY_LOG.warn(
          "Unauthorised API call by user {} to create resource in {}",
          user.getUsername(),
          targetFolderId);
      throw new IllegalArgumentException(createNotFoundMessage("Folder", targetFolderId));
    }
    publisher.publishEvent(createGenericEvent(user, createdDoc, AuditAction.CREATE));
    return createdDoc;
  }

  public void saveApiDocumentChangesToStructuredDocument(
      ApiDocument apiDoc, User user, StructuredDocument doc) throws DocumentAlreadyEditedException {

    String oldName = doc.getName();
    String newName = apiDoc.getName();
    if (StringUtils.isNotBlank(newName) && !newName.equals(oldName)) {
      boolean isRenamed = recordManager.renameRecord(newName, doc.getId(), user);
      if (isRenamed) {
        auditTrailService.notify(new RenameAuditEvent(user, doc, oldName, newName));
      }
    }
    String tags = apiDoc.getTags();
    documentTagManager.apiSaveTagForDocument(doc.getId(), tags, user);
    boolean fieldsUpdated = saveDocFields(apiDoc, doc, user);
    if (fieldsUpdated) {
      saveDocAfterFieldsUpdated(doc, user);
    }
  }

  private boolean saveDocFields(ApiDocument apiDoc, StructuredDocument doc, User user) {
    boolean fieldsUpdated = false;
    if (apiDoc.getFields() != null) {
      for (int i = 0; i < apiDoc.getFields().size(); i++) {
        ApiField apiField = apiDoc.getFields().get(i);
        String apiFieldContent = apiField.getContent();
        if (apiFieldContent != null) {
          Field field = doc.getFields().get(i);
          ApiFieldContent processedContent =
              apiFieldsHelper.getContentToSaveForIncomingApiContent(
                  apiField.getContent(), field, user);
          recordManager.saveTemporaryDocument(field, user, processedContent.getContent());
          fieldsUpdated = true;
        }
      }
    }
    return fieldsUpdated;
  }

  private void saveDocAfterFieldsUpdated(StructuredDocument doc, User user)
      throws DocumentAlreadyEditedException {
    FolderRecordPair fldRecPair =
        recordManager.saveStructuredDocument(doc.getId(), user.getUsername(), true, null);
    publisher.publishEvent(createGenericEvent(user, fldRecPair.getRecord(), AuditAction.WRITE));
  }

  private HistoricalEvent createGenericEvent(User user, Object object, AuditAction create) {
    return new GenericEvent(user, object, create);
  }

  @Override
  public void createNewRevision(StructuredDocument doc, ApiDocument apiDocument, User user)
      throws DocumentAlreadyEditedException {
    assertDocumentNotAlreadyEdited(doc);
    saveApiDocumentChangesToStructuredDocument(apiDocument, user, doc);
  }

  private void assertDocumentNotAlreadyEdited(StructuredDocument doc)
      throws DocumentAlreadyEditedException {
    String editor = recordEditorTracker.getEditingUserForRecord(doc.getId());
    if (editor != null) {
      throwDocumentEditConflictException(doc, editor);
    }
  }

  //  private @Autowired MessageSourceAccessor messages;
  //
  private void throwDocumentEditConflictException(StructuredDocument doc, String editor)
      throws DocumentAlreadyEditedException {
    String msg =
        String.format(
            "Document %d is currently edited %s",
            doc.getId(), (editor != null) ? ("by " + editor) : "");
    throw new DocumentAlreadyEditedException(msg);
  }

  private String createNotFoundMessage(String resourceType, Long id) {
    return "test"; // FIXME messages.getMessage("record.inaccessible", new Object []{resourceType,
    // id});
  }
}
