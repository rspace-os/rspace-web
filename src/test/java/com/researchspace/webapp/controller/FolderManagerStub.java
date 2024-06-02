package com.researchspace.webapp.controller;

import com.researchspace.core.util.CollectionFilter;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.record.*;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.TreeViewItem;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordContext;
import java.util.List;
import java.util.Optional;

public class FolderManagerStub implements FolderManager {
  public static boolean noteBooksArePublished;
  public static User anonymousUser;
  private User notebookOwner;

  @Override
  public Folder getFolder(Long folderid, User user) {
    Folder rc = TestFactory.createAFolder("any", user);
    rc.setSharingACL(RecordSharingACL.createACLForUserOrGroup(user, PermissionType.CREATE));
    rc.setId(1L);
    return rc;
  }

  @Override
  public Notebook getNotebook(Long folderid) {
    return getNotebook(folderid, notebookOwner);
  }

  public Notebook getNotebook(Long folderid, User user) {
    Notebook nb = TestFactory.createANotebook("any", user);
    ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
    ConstraintBasedPermission cbp = parser.resolvePermission("RECORD:READ:");
    if (noteBooksArePublished) {
      nb.getSharingACL().addACLElement(anonymousUser, cbp);
    }
    return nb;
  }

  @Override
  public Folder save(Folder f, User user) {
    return null;
  }

  @Override
  public Folder setRecordFromFolderDeleted(Long toDeleteID, Long folderid, User user) {
    return null;
  }

  @Override
  public Folder removeRecordFromFolder(Long toDeleteID, Long folderid, User user) {
    return null;
  }

  @Override
  public Folder createNewFolder(long parentId, String name, User u) {
    return null;
  }

  @Override
  public RecordCopyResult copy(Long toCopyFolderid, User u, String newname) {
    return null;
  }

  @Override
  public List<Long> getRecordIds(Folder fd) {
    return null;
  }

  @Override
  public Folder getFolder(Long folderid, User user, SearchDepth depth) {
    return null;
  }

  @Override
  public Folder addChild(Long folderId, BaseRecord child, User owner) {
    return null;
  }

  @Override
  public Folder addChild(
      Long folderId, BaseRecord child, User owner, ACLPropagationPolicy aclPolicy) {
    return null;
  }

  @Override
  public Notebook createNewNotebook(
      long parentId, String notebookName, RecordContext context, User u) {
    return null;
  }

  @Override
  public ServiceOperationResult<Folder> move(
      Long toMoveId, Long targetFolderId, Long sourceFolderId, User user) {
    return null;
  }

  @Override
  public Folder getGalleryRootFolderForUser(User user) {
    return null;
  }

  @Override
  public Folder removeBaseRecordFromFolder(BaseRecord toDelete, Long parentfolderid) {
    return null;
  }

  @Override
  public Optional<Folder> getGroupOrIndividualShrdFolderRootFromSharedSubfolder(
      Long srcRecordId, User user) {
    return null;
  }

  @Override
  public Folder getRootRecordForUser(User subject, User user) {
    return null;
  }

  @Override
  public Folder getRootFolderForUser(User user) {
    Folder f = new Folder();
    f.setId(1L);
    return f;
  }

  @Override
  public Folder getLabGroupsFolderForUser(User user) {
    return null;
  }

  @Override
  public Long getLabGroupFolderIdForUser(User user) {
    return null;
  }

  @Override
  public Folder getMediaFolderFromURLPath(String path, User user) {
    return null;
  }

  @Override
  public Folder getFromURLPath(String path, User user, CollectionFilter<BaseRecord> filter) {
    Folder f = TestFactory.createAFolder("f1", user);
    try {
      Thread.sleep(1);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    f.setId(1L);
    Folder f2 = TestFactory.createAFolder("f2", user);
    f2.setId(2L);
    f2.addChild(f, TestFactory.createAnyUser("XXXX"));
    return f;
  }

  @Override
  public Folder getInitialisedFolder(Long fId, User user, CollectionFilter<BaseRecord> filter) {
    return null;
  }

  @Override
  public Folder getTemplateFolderForUser(User user) {
    return null;
  }

  @Override
  public Folder removeBaseRecordFromFolder(
      BaseRecord toDelete, Long parentfolderid, ACLPropagationPolicy aclPolicy) {
    return null;
  }

  @Override
  public Folder getApiUploadTargetFolder(String contentType, User subject, Long folderId) {
    return null;
  }

  @Override
  public ISearchResults<TreeViewItem> getFolderListingForTreeView(
      Long folderId, PaginationCriteria<TreeViewItem> pgCrit, User subject) {
    return null;
  }

  @Override
  public Optional<Folder> getFolderSafe(Long folderid, User user) {
    return null;
  }

  @Override
  public Folder getImportsFolder(User subject) {
    return null;
  }

  @Override
  public Folder getFolder(Long folderid, User user, boolean includeDeleted) {
    return null;
  }

  @Override
  public Folder createGallerySubfolder(String name, String mediaFolderName, User user) {
    return null;
  }

  @Override
  public ServiceOperationResult<Folder> addChild(
      Long folderId,
      BaseRecord child,
      User owner,
      ACLPropagationPolicy aclPolicy,
      boolean suppressIllegalAddChild) {
    return null;
  }

  @Override
  public Folder createNewFolder(Long id, String name, User owner, ImportOverride override) {
    return null;
  }

  @Override
  public Notebook createNewNotebook(
      long parentId,
      String notebookName,
      RecordContext context,
      User subject,
      ImportOverride override) {
    return null;
  }

  public void setNotebookOwner(User notebookOwner) {
    this.notebookOwner = notebookOwner;
  }
}
