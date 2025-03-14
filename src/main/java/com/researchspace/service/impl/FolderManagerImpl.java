package com.researchspace.service.impl;

import static com.researchspace.core.util.MediaUtils.GALLERY_MEDIA_FOLDERS;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.join;
import static org.apache.commons.lang3.ArrayUtils.contains;

import com.researchspace.core.util.CollectionFilter;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.events.RecordCopyEvent;
import com.researchspace.model.events.RecordCreatedEvent;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.ChildAddPolicy;
import com.researchspace.model.record.FilterDeletedRecords;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.TreeViewItem;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.FolderManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RecordContext;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RequiresActiveLicense;
import com.researchspace.service.UserManager;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class FolderManagerImpl implements FolderManager {
  private Logger log = LoggerFactory.getLogger(getClass());

  private FolderDao folderDao;
  private RecordManager recordManager;
  private RecordFactory recordFactory;
  private RecordDao recordDao;
  private UserManager userManager;
  private IPermissionUtils permissionUtils;
  private OperationFailedMessageGenerator messages;
  private ApplicationEventPublisher publisher;
  private CommunityServiceManager communityServiceManager;
  private PermissionFactory permFac = new DefaultPermissionFactory();

  @Autowired
  public FolderManagerImpl(
      FolderDao folderDao,
      RecordManager recordManager,
      RecordFactory recordFactory,
      RecordDao recordDao,
      UserManager userManager,
      IPermissionUtils permissionUtils,
      OperationFailedMessageGenerator messages,
      ApplicationEventPublisher publisher,
      CommunityServiceManager communityServiceManager) {
    this.folderDao = folderDao;
    this.recordManager = recordManager;
    this.recordFactory = recordFactory;
    this.recordDao = recordDao;
    this.userManager = userManager;
    this.permissionUtils = permissionUtils;
    this.messages = messages;
    this.publisher = publisher;
    this.communityServiceManager = communityServiceManager;
  }

  public FolderManagerImpl(
      RecordFactory rfactory,
      FolderDao folderDao,
      IPermissionUtils permissionUtils,
      OperationFailedMessageGenerator messages) {
    this.recordFactory = rfactory;
    this.folderDao = folderDao;
    this.permissionUtils = permissionUtils;
    this.messages = messages;
  }

  public FolderManagerImpl() {}

  @Override
  public Folder getFolder(Long folderid, User user, SearchDepth depth) {
    Folder folder = folderDao.get(folderid);
    if (!SearchDepth.ZERO.equals(depth)) {
      initSubFolderTree(folder, depth);
    }
    return folder;
  }

  @Override
  public Folder getFolder(Long folderid, User user) {
    return getFolder(folderid, user, false);
  }

  @Override
  public Folder getFolder(Long folderid, User user, boolean includeDeleted) {
    Folder folder = folderDao.get(folderid);
    assertUserHasReadPermission(user, folder);
    if (!includeDeleted) {
      assertNotDeleted(user, folder);
    }
    return folder;
  }

  @Override
  public Optional<Folder> getFolderSafe(Long folderid, User user) {
    Optional<Folder> optFolder = folderDao.getSafeNull(folderid);
    if (optFolder.isPresent()) {
      assertUserHasReadPermission(user, optFolder.get());
      assertNotDeleted(user, optFolder.get());
    }
    return optFolder;
  }

  @Override
  public RSPath getShortestPathToSharedRootFolder(Long sharedSubfolderId, User user) {
    Folder sharedSubfolder = folderDao.get(sharedSubfolderId);
    Folder sharedRootfolder = folderDao.getUserSharedFolder(user);
    return sharedSubfolder.getShortestPathToParent(sharedRootfolder);
  }

  @Override
  public Optional<Folder> getGroupOrIndividualShrdFolderRootFromSharedSubfolder(
      Long srcRecordId, User user) {
    Folder src = folderDao.get(srcRecordId);
    Folder target = folderDao.getUserSharedFolder(user);
    RSPath path =
        src.getShortestPathToParent(
            f -> f.equals(target), (current, parent) -> isGroupOrIndividFolder(current));

    return path.getFirstElement().map(this::getGroupOrIndividFolder);
  }

  private Folder getGroupOrIndividFolder(BaseRecord br) {
    return isGroupOrIndividFolder(br) ? (Folder) br : null;
  }

  private boolean isGroupOrIndividFolder(BaseRecord br) {
    return br.hasType(RecordType.SHARED_GROUP_FOLDER_ROOT)
        || br.hasType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT);
  }

  public Notebook getNotebook(Long notebookid) {
    Notebook notebook = (Notebook) folderDao.get(notebookid);
    long count =
        recordDao.getChildRecordCount(
            notebookid, new RecordTypeFilter(EnumSet.of(RecordType.NORMAL), true));
    notebook.setEntryCount(count);
    return notebook;
  }

  private void initSubFolderTree(Folder rc, final SearchDepth depth) {
    for (RecordToFolder subFlder : rc.getChildren()) {
      subFlder.getClass(); // init;
      if (subFlder.getRecord().isFolder() && SearchDepth.INFINITE.equals(depth)) {
        initSubFolderTree((Folder) subFlder.getRecord(), depth); // recurse
      }
    }
  }

  @Override
  public Folder save(Folder f, User user) {
    return folderDao.save(f);
  }

  @RequiresActiveLicense
  public RecordCopyResult copy(Long toCopyFolderid, User user, String newName) {
    Folder original = getFolder(toCopyFolderid, user);
    Folder parent = recordManager.getRecordParentPreferNonShared(user, original);
    RecordCopyResult result = new RecordCopyResult(parent, true);
    Folder copy = copy(original, user, newName, true, parent, result);
    result.add(original, copy);
    result.setParentCopy(copy);
    publisher.publishEvent(new RecordCopyEvent(result, user));
    return result;
  }

  private Folder copy(
      Folder original,
      User u,
      String newName,
      boolean isTopLevelFolder,
      Folder targetFolder,
      RecordCopyResult result) {

    // just copy top level folder basic info - a shallow copy
    Folder copiedFolder = original.copy(u, false);
    setBasicAclPermissionsOnFolder(copiedFolder);
    result.add(original, copiedFolder);
    sleep();
    copiedFolder.setOwner(u);

    // we only want to rename the top folder, all nested records keep same name
    if (isTopLevelFolder) {
      String newTopLevelName =
          StringUtils.isBlank(newName) ? original.getName() + "_copy" : newName;
      copiedFolder.setName(newTopLevelName);
    }

    addChild(targetFolder.getId(), copiedFolder, u);

    // we copy the children, after sorting them by creation date and filtering out deleted records
    // (RSPAC-916)
    Set<BaseRecord> childrenSet = original.getChildrens(new FilterDeletedRecords(u));
    List<BaseRecord> childrenList = new ArrayList<>(childrenSet);
    childrenList.sort(BaseRecord.CREATION_DATE_COMPARATOR);

    for (BaseRecord br : childrenList) {
      if (!br.isFolder()) {
        sleep();
        // this does full copy, including images, sketches etc. we copy record into the new folder
        RecordCopyResult childResult =
            recordManager.copy(
                br.getId(), br.getName(), u, copiedFolder.getId(), new DefaultRecordContext(true));
        result.addAll(childResult.getOriginalToCopy());
      } else {
        // recurse, copying this folder's contents,
        copy((Folder) br, u, null, false, copiedFolder, result);
      }
    }
    Folder copied = folderDao.get(copiedFolder.getId());
    return copied;
  }

  private void setBasicAclPermissionsOnFolder(Folder copiedFolder) {
    String username = copiedFolder.getCreatedBy();
    RecordSharingACL acl = new RecordSharingACL();
    acl.addACLElement(
        new ACLElement(
            username,
            new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE)));
    acl.addACLElement(
        new ACLElement(
            username,
            new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.CREATE)));
    acl.addACLElement(
        new ACLElement(
            username,
            new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.CREATE_FOLDER)));
    acl.addACLElement(
        new ACLElement(
            username, new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.SEND)));
    acl.addACLElement(
        new ACLElement(
            username,
            new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.FOLDER_RECEIVE)));
    acl.addACLElement(
        new ACLElement(
            username,
            new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.RENAME)));
    acl.addACLElement(
        new ACLElement(
            username, new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.COPY)));
    copiedFolder.setSharingACL(acl);
  }

  private void sleep() {
    try {
      Thread.sleep(1);
    } catch (InterruptedException e) {
      log.warn("Interrupted while sleeping.", e);
    } // ensure all copied objects are unique
  }

  @Override
  public Folder setRecordFromFolderDeleted(Long toDeleteID, Long folderid, User user) {
    Record r = recordDao.get(toDeleteID);
    Folder parent = folderDao.get(folderid);
    for (RecordToFolder rtf : parent.getChildren()) {
      if (rtf.getRecord().equals(r)) {
        rtf.markRecordInFolderDeleted(true);
        folderDao.save(parent);
        break;
      }
    }
    return parent;
  }

  public Folder removeRecordFromFolder(Long toDeleteID, Long folderid, User user) {
    Record r = recordDao.get(toDeleteID);
    Folder parent = folderDao.get(folderid);
    parent.removeChild(r);
    folderDao.save(parent);
    recordDao.save(r);
    return parent;
  }

  public Folder removeBaseRecordFromFolder(BaseRecord toDelete, Long parentfolderid) {
    return removeBaseRecordFromFolder(
        toDelete, parentfolderid, ACLPropagationPolicy.DEFAULT_POLICY);
  }

  @Override
  public Folder removeBaseRecordFromFolder(
      BaseRecord toDelete, Long parentfolderid, ACLPropagationPolicy aclPolicy) {
    Folder parent = folderDao.get(parentfolderid);
    parent.removeChild(toDelete, aclPolicy);
    folderDao.save(parent);
    saveChild(toDelete);
    return parent;
  }

  @RequiresActiveLicense
  public Folder createNewFolder(long parentId, String fName, User subject) {
    return createNewFolder(parentId, fName, subject, null);
  }

  public Folder createNewFolder(
      Long parentId, String fName, User subject, ImportOverride override) {
    Folder parent = folderDao.get(parentId);
    assertUserHasCreatePermission(subject, parent);

    if (StringUtils.isBlank(fName)) {
      fName = Folder.DEFAULT_FOLDER_NAME;
    }
    Folder rc =
        override == null
            ? recordFactory.createFolder(fName, subject)
            : recordFactory.createFolder(fName, subject, override);
    addChild(parent.getId(), rc, subject);
    return rc;
  }

  @RequiresActiveLicense()
  @Override
  public Notebook createNewNotebook(
      long parentId,
      String notebookName,
      RecordContext context,
      User subject,
      ImportOverride override) {
    Folder parent = folderDao.get(parentId);
    assertUserHasCreatePermission(subject, parent);
    if (StringUtils.isBlank(notebookName)) {
      notebookName = Notebook.DEFAULT_NOTEBOOK_NAME;
    }
    Notebook n =
        override == null
            ? recordFactory.createNotebook(notebookName, subject)
            : recordFactory.createNotebook(notebookName, subject, override);
    addChild(parentId, n, subject);
    if (!context.isRecursiveFolderOperation()) {
      publisher.publishEvent(new RecordCreatedEvent(n, subject));
    }
    return n;
  }

  @RequiresActiveLicense()
  public Notebook createNewNotebook(
      long parentId, String notebookName, RecordContext context, User subject) {
    return createNewNotebook(parentId, notebookName, context, subject, null);
  }

  @Override
  public ServiceOperationResult<Folder> move(Long toMove, Long target, Long srcFolderId, User user)
      throws IllegalAddChildOperation {
    Folder newparent = folderDao.get(target);
    Folder oldParent = folderDao.get(srcFolderId);
    Folder original = getFolder(toMove, user);
    boolean moved = original.move(oldParent, newparent, user);
    recursiveSave(newparent);
    folderDao.save(oldParent);
    return new ServiceOperationResult<Folder>(original, moved);
  }

  /**
   * Drilldown method that recursively saves all children so their lineage will be correct after a
   * move action occurs
   *
   * @param doc
   */
  private void recursiveSave(BaseRecord parent) {
    if (parent.isFolder()) {
      Folder parentFolder = (Folder) parent;
      folderDao.save(parentFolder);
      for (BaseRecord child : parentFolder.getChildrens()) {
        recursiveSave(child);
      }
    } else {
      // must be a record save it now
      recordDao.save((Record) parent);
    }
  }

  @Override
  public List<Long> getRecordIds(Folder fd) {
    return folderDao.getRecordIds(fd);
  }

  public Folder addChild(Long folderId, BaseRecord child, User owner) {
    return addChild(folderId, child, owner, ACLPropagationPolicy.DEFAULT_POLICY);
  }

  @Override
  public ServiceOperationResult<Folder> addChild(
      Long folderId,
      BaseRecord child,
      User owner,
      ACLPropagationPolicy aclPolicy,
      boolean suppressIllegalAddChild) {
    Folder parentFolder = null;
    try {
      parentFolder = addChild(folderId, child, owner, aclPolicy);
      return new ServiceOperationResult<Folder>(parentFolder, true);
    } catch (IllegalAddChildOperation e) {
      if (suppressIllegalAddChild) {
        parentFolder = folderDao.get(folderId);
        return new ServiceOperationResult<Folder>(parentFolder, false, e.getMessage());
      } else {
        throw e;
      }
    }
  }

  @Override
  public Folder addChild(
      Long folderId, BaseRecord child, User owner, ACLPropagationPolicy aclPolicy) {
    Folder f = folderDao.get(folderId);
    saveChild(child);
    RecordToFolder rtf = f.addChild(child, ChildAddPolicy.DEFAULT, owner, aclPolicy);
    if (rtf == null) {
      log.warn(
          "trying to add an item which would produce cycle in folder tree! - adding ["
              + "{}] to [{}]",
          child.getName(),
          f.getName());
      return f;
    }
    folderDao.save(f);
    saveChild(child);
    return f;
  }

  private void saveChild(BaseRecord child) {
    if (child.isFolder()) {
      folderDao.save((Folder) child);
    } else {
      recordDao.save((Record) child);
    }
  }

  @Override
  public Folder getGalleryRootFolderForUser(User user) {
    return folderDao.getGalleryFolderForUser(user);
  }

  @Override
  public Folder getTemplateFolderForUser(User user) {
    return folderDao.getTemplateFolderForUser(user);
  }

  @Override
  public Folder getRootRecordForUser(User subject, User user) {
    Folder rc = folderDao.getRootRecordForUser(user);
    assertUserHasReadPermission(subject, rc);
    return rc;
  }

  private void assertUserHasReadPermission(User user, Folder folder) {
    boolean isPermitted =
        permissionUtils.isPermitted(
            folder, PermissionType.READ, getUserWithRefreshedPermissions(user));
    if (!isPermitted) {
      // community admin can read shared group folder where group is part of community they are
      // admin of
      boolean canReadViaCommunity = isAdminOfCommunityGroupFolder(user, folder);
      if (!canReadViaCommunity) {
        throw new AuthorizationException(unauthorisedMsg(user));
      }
    }
  }

  private boolean isAdminOfCommunityGroupFolder(User user, Folder folder) {
    if (!user.hasAdminRole()) {
      return false;
    }
    List<Community> communities = communityServiceManager.listCommunitiesForAdmin(user.getId());

    List<String> adminCommunityGroupNames =
        communities.stream()
            .map(
                community ->
                    community.getLabGroups().stream()
                        .map(Group::getUniqueName)
                        .collect(Collectors.toList()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    return folder.getSharingACL().getAclElements().stream()
        .anyMatch(aclElement -> hasRecordReadPermission(adminCommunityGroupNames, aclElement));
  }

  private boolean hasRecordReadPermission(
      List<String> adminCommunityGroupNames, ACLElement aclPermission) {
    // format is USER_OR_GROUP_NAME=DOMAIN:ACTION
    String[] domainAndAction = aclPermission.getAsString().split("=")[1].split(":");
    String domain = domainAndAction[0];
    String actions = domainAndAction[1];
    return adminCommunityGroupNames.contains(aclPermission.getUserOrGrpUniqueName())
        && domain.equals("RECORD")
        && actions.contains("READ");
  }

  private void assertNotDeleted(User user, Folder folder) {
    if (folder.isDeleted()
        || folder.isDeletedForUser(folder.getOwner())
        || folder.isDeletedForUser(user)) {
      throw new AuthorizationException(deletedMsg(user));
    }
  }

  private String deletedMsg(User user) {
    return messages.getFailedMessage(user.getUsername(), "open a deleted folder.");
  }

  private String unauthorisedMsg(User user) {
    String msg = messages.getFailedMessage(user.getUsername(), "open an unauthorised folder.");
    return msg;
  }

  private void assertUserHasCreatePermission(User user, Folder parent) {
    boolean canCreateFolder =
        permissionUtils.isPermitted(
            parent, PermissionType.CREATE_FOLDER, getUserWithRefreshedPermissions(user));
    if (!canCreateFolder) {
      String msg =
          messages.getFailedMessage(
              user.getUsername(), "create folder in [" + parent.getId() + "]");
      throw new AuthorizationException(msg);
    }
  }

  private User getUserWithRefreshedPermissions(User user) {
    if (permissionUtils.refreshCacheIfNotified()) {
      return userManager.getUserByUsername(user.getUsername(), true);
    }
    return user;
  }

  @Override
  public Folder getRootFolderForUser(User user) {
    return folderDao.getRootRecordForUser(user);
  }

  @Override
  public Folder getLabGroupsFolderForUser(User user) {
    return folderDao.getLabGroupFolderForUser(user);
  }

  public Long getLabGroupFolderIdForUser(User user) {
    return folderDao.getLabGroupFolderIdForUser(user);
  }

  @Override
  public Folder getFromURLPath(String path, User user, CollectionFilter<BaseRecord> filter) {
    Folder targetRecord = getRootFolderForUser(user);
    if (targetRecord == null) {
      throw new IllegalStateException(format("No root folder for user %s", user.getUsername()));
    }

    targetRecord = getTargetFolderFromPath(path, targetRecord, filter);
    Folder target = (Folder) targetRecord;
    // initialize collections within session
    initFolderChildren(target, filter);

    return targetRecord;
  }

  @Override
  public Folder getInitialisedFolder(Long fId, User user, CollectionFilter<BaseRecord> filter) {
    Folder f = getFolder(fId, user);
    initFolderChildren(f, filter);
    return f;
  }

  @Override
  public Folder getMediaFolderFromURLPath(String path, User user) {
    Folder targetRecord = folderDao.getGalleryFolderForUser(user);
    if (targetRecord == null) {
      throw new IllegalStateException(format("No root folder for user %s", user.getUsername()));
    }

    targetRecord = getTargetFolderFromPath(path, targetRecord, null);
    Folder target = (Folder) targetRecord;
    // initialize collections within session
    initFolderChildren(target, null);

    return targetRecord;
  }

  private void initFolderChildren(Folder target, CollectionFilter<BaseRecord> filter) {
    if (target != null) {
      // this runs 1 query per child, i.e an n+1 selects problem.
      // we should do a join fetch here just to get the data we need from a single query, then
      // filter.
      for (RecordToFolder r2f : target.getChildren()) {
        if (filter != null && filter.filter(r2f.getRecord())) {
          r2f.getClass();
        } else if (filter == null) {
          r2f.getClass();
        }
      }
    }
  }

  private Folder getTargetFolderFromPath(
      String path, Folder targetRecord, CollectionFilter<BaseRecord> filter) {
    // else return root record
    if (!"/".equals(path)) {
      String[] pathItems = path.split("/");
      for (int i = 0; i < pathItems.length; i++) {
        if (pathItems[i] != null && !"".equals(pathItems[i].trim())) {
          Set<BaseRecord> children = targetRecord.getChildrens(filter);
          targetRecord = null;
          if (children != null && !children.isEmpty()) {
            for (BaseRecord record : children) {
              if (record.isFolder() && pathItems[i].equals(record.getName())) {
                targetRecord = (Folder) record;
                break;
              }
            }
            if (targetRecord == null) {
              throw new IllegalStateException(
                  "The record named '"
                      + pathItems[i]
                      + "' does not exist in the path, or cannot be accessed. "
                      + path);
            }
          }
        }
      }
    }
    return targetRecord;
  }

  @Override
  public Folder getImportsFolder(User subject) {
    Optional<Folder> apiInbox = folderDao.getImportFolder(subject);
    if (apiInbox.isPresent()) {
      return apiInbox.get();
    }
    Folder importFolder = createAndSaveImportInbox(subject);
    Folder rootFolder = folderDao.getRootRecordForUser(subject);
    // we don't want to propagate usual permissions
    addAdditionalContentFolderToParent(subject, importFolder, rootFolder);
    return importFolder;
  }

  @Value("${api.beta.useTimeRotatedGalleryApiInboxSubFolders:false}")
  private boolean useTimeRotatedGalleryApiInboxSubFolders;

  @SneakyThrows
  @Override
  public Folder getApiUploadTargetFolder(String contentType, User subject, Long folderId) {
    Validate.isTrue(
        isValidContentType(contentType),
        format(
            "contentType must be empty string (for workspace) or one of (%s) for files",
            join(GALLERY_MEDIA_FOLDERS, ",")));
    boolean isDestinationWorkspace = isDestinationWorkspace(contentType);
    boolean isDestinationGallery = !isDestinationWorkspace;
    String parentFolderName = isDestinationWorkspace ? subject.getUsername() : contentType;

    // we return or create default ApiInbox folder
    Optional<Folder> targetFolderOpt = Optional.empty();
    if (folderId != null) {
      targetFolderOpt = folderDao.getSafeNull(folderId);
      // folder id was not existing
      if (targetFolderOpt.isEmpty()) {
        throw new AuthorizationException(unauthorisedMsg(subject));
      }
    }
    if (targetFolderOpt.isEmpty() || targetFolderOpt.get().isSharedFolder()) {
      Optional<Folder> apiInboxOpt =
          folderDao.getApiFolderForContentType(parentFolderName, subject);
      Folder apiInbox;
      if (apiInboxOpt.isPresent()) {
        apiInbox = apiInboxOpt.get();
      } else {
        apiInbox = createAndSaveApiInbox(subject);
        Folder apiFolderParent;
        if (isDestinationGallery) {
          apiFolderParent = folderDao.getSystemFolderForUserByName(subject, contentType);
        } else {
          apiFolderParent = folderDao.getRootRecordForUser(subject);
        }
        // we don't want to propagate usual permissions
        addAdditionalContentFolderToParent(subject, apiInbox, apiFolderParent);
      }
      if (isDestinationGallery && useTimeRotatedGalleryApiInboxSubFolders) {
        apiInbox = getOrCreateTimeRotatedGalleryApiInboxSubFolder(apiInbox, subject);
      }
      return apiInbox;
    }

    Folder targetFolder = targetFolderOpt.get();
    assertUserHasReadPermission(subject, targetFolder);
    Validate.isTrue(!targetFolder.isSharedFolder(), "Can't add API content into a shared folder");
    if (targetFolder.isSharedFolder()) {
      targetFolder = folderDao.getRootRecordForUser(subject); // save to root folder
    }
    if (isDestinationGallery) {
      Validate.isTrue(
          targetFolder.hasAncestorMatchingPredicate(
              Folder.targetFolderIsCorrectTypeForMedia(parentFolderName), true),
          String.format(
              "Target folder (id=%d) must be either Gallery folder %s or a subfolder of %s",
              folderId, parentFolderName, parentFolderName));
    } else if (isDestinationWorkspace) {
      Validate.isTrue(
          targetFolder.isInWorkspace() || targetFolder.isApiInboxFolder(),
          String.format(
              "Workspace Target folder (id=%d) must be in the workspace, not in the Gallery,"
                  + " Templates or Shared folders",
              folderId));
    }
    return targetFolder;
  }

  private Folder getOrCreateTimeRotatedGalleryApiInboxSubFolder(Folder apiInbox, User subject) {
    String timeRotatedSubFolderName =
        ZonedDateTime.now()
            .truncatedTo(ChronoUnit.MINUTES)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    Optional<Folder> apiInboxSubFolderOpt =
        folderDao.getApiInboxSubFolderByName(apiInbox, timeRotatedSubFolderName, subject);
    if (apiInboxSubFolderOpt.isPresent()) {
      return apiInboxSubFolderOpt.get();
    }
    Folder apiInboxSubFolder =
        recordFactory.createSystemCreatedFolder(timeRotatedSubFolderName, subject);
    folderDao.save(apiInboxSubFolder);
    apiInbox.addChild(apiInboxSubFolder, subject);
    folderDao.save(apiInbox);
    folderDao.save(apiInboxSubFolder);
    return apiInboxSubFolder;
  }

  private boolean isDestinationWorkspace(String folderName) {
    return StringUtils.isBlank(folderName);
  }

  private void addAdditionalContentFolderToParent(
      User subject, Folder newContentFolder, Folder parent) {
    parent.addChild(
        newContentFolder, ChildAddPolicy.DEFAULT, subject, ACLPropagationPolicy.NULL_POLICY);
    folderDao.save(parent);
  }

  private Folder createAndSaveApiInbox(User subject) {
    Folder newApiFolder = recordFactory.createApiInboxFolder(subject);
    return setupAdditionalContentFolder(subject, newApiFolder);
  }

  private Folder createAndSaveImportInbox(User subject) {
    Folder newApiFolder = recordFactory.createImportsFolder(subject);
    return setupAdditionalContentFolder(subject, newApiFolder);
  }

  private Folder setupAdditionalContentFolder(User subject, Folder newApiFolder) {
    permFac.setUpAclForIndividualInboxFolder(newApiFolder, subject);
    folderDao.save(newApiFolder);
    return newApiFolder;
  }

  private boolean isValidContentType(String contentType) {
    return isBlank(contentType) || contains(MediaUtils.GALLERY_MEDIA_FOLDERS, contentType);
  }

  @Override
  public ISearchResults<TreeViewItem> getFolderListingForTreeView(
      Long folderId, PaginationCriteria<TreeViewItem> pgCrit, User subject) {
    return folderDao.getFolderListingForTreeView(folderId, pgCrit);
  }

  @Override
  public Folder createGallerySubfolder(String folderName, String mediaFolderName, User user) {
    Validate.isTrue(
        ArrayUtils.contains(MediaUtils.GALLERY_MEDIA_FOLDERS, mediaFolderName),
        "Invalid mediaFolderName " + mediaFolderName);

    Folder imagGalleryFolder = recordManager.getGallerySubFolderForUser(mediaFolderName, user);
    Folder targetFolder = createNewFolder(imagGalleryFolder.getId(), folderName, user);
    return targetFolder;
  }
}
