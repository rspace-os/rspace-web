package com.researchspace.webapp.controller;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GalleryFilterCriteria;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.DocumentInitializationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.FolderRecordPair;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.RecordContext;
import com.researchspace.service.RecordManager;
import com.researchspace.service.impl.RecordEditorTracker;
import com.researchspace.session.UserSessionTracker;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Test stub for controller tests */
public class RecordManagerStub implements RecordManager {

  private RecordEditorTracker ret = new RecordEditorTracker();

  /** Gets a parented <code>Structured Document</code> */
  @Override
  public Record get(long id) {
    StructuredDocument sd = new StructuredDocument(TestFactory.createAnyForm());
    sd.setId(1L);
    return sd;
  }

  @Override
  public boolean exists(long id) {
    return true;
  }

  @Override
  public Record save(Record record, User user) {
    return record;
  }

  @Override
  public ServiceOperationResult<BaseRecord> move(
      Long id, Long targetParent, Long currPArentId, User user) {
    return null;
  }

  @Override
  public EditStatus requestRecordEdit(final Long id, User editor, UserSessionTracker activeUsers) {
    return ret.attemptToEdit(id, editor, activeUsers, () -> "");
  }

  @Override
  public EditStatus requestRecordView(Long recordId, User user, UserSessionTracker activeUsers) {
    return null;
  }

  @Override
  public ISearchResults<BaseRecord> listFolderRecords(
      Long parentId,
      PaginationCriteria<? extends BaseRecord> filter,
      RecordTypeFilter recordTypefilter) {
    return new ISearchResults<BaseRecord>() {

      @Override
      public List<BaseRecord> getResults() {
        try {
          return Arrays.asList(new BaseRecord[] {get(1L)});
        } catch (Exception e) {
          e.printStackTrace();
        }
        return Collections.emptyList();
      }

      @Override
      public Long getTotalHits() {
        return 0L;
      }

      @Override
      public Integer getHits() {
        return 0;
      }

      @Override
      public Integer getPageNumber() {
        return 0;
      }

      @Override
      public Integer getTotalPages() {
        return 0;
      }

      @Override
      public int getHitsPerPage() {
        return 10;
      }

      @Override
      public List<PaginationObject> getLinkPages() {
        return null;
      }

      @Override
      public void setLinkPages(List<PaginationObject> linkPages) {}

      @Override
      public BaseRecord getFirstResult() {
        return null;
      }

      @Override
      public BaseRecord getLastResult() {
        return null;
      }

      @Override
      public PaginationCriteria<?> getPaginationCriteria() {
        return null;
      }
    };
  }

  @Override
  public ISearchResults<BaseRecord> listFolderRecords(
      Long parentId, PaginationCriteria<? extends BaseRecord> filter) {
    return listFolderRecords(parentId, filter, null);
  }

  @Override
  public StructuredDocument createNewStructuredDocument(Long parentId, Long templateId, User user) {
    StructuredDocument sd = new StructuredDocument(TestFactory.createAnyForm());
    sd.setId(200L);
    return sd;
  }

  @Override
  public StructuredDocument createNewStructuredDocument(
      Long parentId, Long formId, User user, boolean skipAddingToChildren) {
    return createNewStructuredDocument(parentId, formId, user);
  }

  @Override
  public StructuredDocument createNewStructuredDocument(
      Long parentId, Long formID, String name, User user, RecordContext context, boolean b) {
    StructuredDocument sd = new StructuredDocument(TestFactory.createAnyForm());
    sd.setId(200L);
    return sd;
  }

  @Override
  public Snippet createSnippet(String name, String content, User u) {
    return null;
  }

  @Override
  public String copySnippetIntoField(Long snippetId, Long fieldId, User user) {
    return null;
  }

  @Override
  public String copyRSpaceContentIntoField(String content, Long fieldId, User user) {
    return null;
  }

  @Override
  public BaseRecord getRecordWithLazyLoadedProperties(
      long id, User user, DocumentInitializationPolicy initPolicy, boolean ignorePermissions) {
    return get(id);
  }

  @Override
  public BaseRecord getRecordWithFields(long recordId, User user) {
    return get(recordId);
  }

  @Override
  public FolderRecordPair saveStructuredDocument(
      long structuredDocumentId, String uname, boolean toUnlock, ErrorList warningsList) {
    return null;
  }

  @Override
  public StructuredDocument saveTemporaryDocument(Field field, User user, String data) {
    return null;
  }

  @Override
  public void unlockRecord(Record r, User u) {
    ret.unlockRecord(r, u, () -> "");
  }

  @Override
  public void unlockRecord(Long recordId, String username) {}

  @Override
  public StructuredDocument createTemplateFromDocument(
      Long recordId, List<Long> field_ids, User usr, String templateName) {
    try {
      return createBasicDocument(recordId + 1, usr);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public StructuredDocument createBasicDocument(Long parentId, User u) {
    StructuredDocument sd = new StructuredDocument(TestFactory.createAnyForm());
    sd.setId(1L);
    return sd;
  }

  public List<Long> getDescendantRecordIdsExcludeFolders(Long parentId) {
    return Collections.singletonList(1L);
  }

  @Override
  public List<Record> getLoadableNotebookEntries(User user, Long notebookId) {
    return null;
  }

  @Override
  public RecordCopyResult copy(long id, String newname, User user, Long targetFolderId) {
    return null;
  }

  @Override
  public Folder getRecordParentPreferNonShared(User user, BaseRecord original) {
    return null;
  }

  @Override
  public void forceVersionUpdate(Long fieldId, DeltaType sketch, String optionalMsg, User user) {}

  @Override
  public <T extends Record> T getAsSubclass(long id, Class<T> clazz) {
    return null;
  }

  @Override
  public boolean renameRecord(String newname, Long toRenameId, User user) {
    return false;
  }

  @Override
  public BaseRecord cancelStructuredDocumentAutosavedEdits(
      long structuredDocumentId, String uname) {
    return null;
  }

  @Override
  public Folder getParentFolderOfRecordOwner(Long recordId, User user) {
    return null;
  }

  @Override
  public Folder getGallerySubFolderForUser(String folderName, User user) {
    return null;
  }

  @Override
  public void removeFromEditorTracker(String sessionId) {}

  @Override
  public List<RSpaceDocView> getAllFrom(Set<Long> dbids) {
    return null;
  }

  @Override
  public RecordCopyResult createFromTemplate(
      long id, String newname, User user, Long targetFolderId) {
    return null;
  }

  @Override
  public boolean canMove(BaseRecord original, Folder targetParent, User user) {
    return false;
  }

  @Override
  public Long getModificationDate(long recordId, User user) {
    return null;
  }

  @Override
  public ISearchResults<BaseRecord> getGalleryItems(
      Long parentId,
      PaginationCriteria<BaseRecord> pgCrit,
      GalleryFilterCriteria galleryFilter,
      RecordTypeFilter recordTypefilter,
      User user) {
    return null;
  }

  @Override
  public ISearchResults<BaseRecord> getFilteredRecords(
      WorkspaceFilters filters, PaginationCriteria<BaseRecord> paginationCriteria, User user) {
    return null;
  }

  @Override
  public List<BaseRecord> getFilteredRecordsList(WorkspaceFilters filters, User user) {
    return null;
  }

  @Override
  public Set<BaseRecord> getViewableTemplates(Set<Long> userIds) {
    return null;
  }

  @Override
  public EcatImage getEcatImage(Long id, boolean loadImageBytes) {
    return null;
  }

  @Override
  public Optional<Record> getSafeNull(long id) {
    return null;
  }

  @Override
  public StructuredDocument createBasicDocumentWithContent(
      Long parentId, String name, User user, String htmlContent) {
    return null;
  }

  @Override
  public StructuredDocument saveTemporaryDocument(Long currFieldId, User subject, String data) {
    return null;
  }

  @Override
  public List<Record> getAuthorisedRecordsById(
      List<Long> ids, User subject, PermissionType permissionType) {
    return null;
  }

  @Override
  public Optional<Record> getOptRecordWithLazyLoadedProperties(
      long id,
      User user,
      DocumentInitializationPolicy initializationPolicy,
      boolean ignorePermissions) {
    return null;
  }

  @Override
  public EditStatus requestRecordEdit(
      Long recordId,
      User user,
      UserSessionTracker activeUsers,
      Supplier<String> sessionIDProvider) {
    return null;
  }

  @Override
  public void unlockRecord(Long recordId, String username, Supplier<String> sessionIdProvider) {}

  @Override
  public RecordCopyResult copy(
      long originalId, String newname, User user, Long targetFolderId, RecordContext context) {
    return null;
  }

  @Override
  public Long getRecordCountForUser(RecordTypeFilter recordTypes, User user) {
    return null;
  }

  @Override
  public List<Long> getAllNonTemplateNonTemporaryStrucDocIdsOwnedByUser(User user) {
    return null;
  }

  @Override
  public List<BaseRecord> getOntologyTagsFilesForUserCalled(
      User user, String userTagsontologyDocument) {
    return null;
  }

  @Override
  public List<StructuredDocument> getontologyDocumentsCreatedInPastThirtyMinutesByCurrentUser(
      String uName) {
    return null;
  }

  @Override
  public boolean forceMoveDocumentToOwnerWorkspace(StructuredDocument userDoc) {
    return false;
  }

  @Override
  public StructuredDocument createNewStructuredDocument(
      Long parentId, Long formId, User user, RecordContext context, ImportOverride override) {
    return null;
  }
}
