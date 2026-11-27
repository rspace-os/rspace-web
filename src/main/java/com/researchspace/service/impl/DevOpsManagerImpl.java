package com.researchspace.service.impl;

import static com.researchspace.core.util.FieldParserConstants.DATA_TYPE_ANNOTATION;

import com.researchspace.auth.ShiroRealm;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.dao.UserDao;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.EcatImage;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.Group;
import com.researchspace.model.RecordAttachment;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.AnnotatedImageRelink;
import com.researchspace.service.AnnotatedImageRelink.LinkState;
import com.researchspace.service.AnnotatedImageRelink.Outcome;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.DevOpsManager;
import com.researchspace.service.FolderManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service("devOpsManager")
public class DevOpsManagerImpl implements DevOpsManager {

  private static final String ANNOTATED_IMAGE_MARKUP = "data-type=\"" + DATA_TYPE_ANNOTATION + "\"";

  static final String SKIP_TEMPLATE = "template document";
  static final String SKIP_IMAGE_DELETED = "Gallery image is deleted";
  static final String SKIP_OWNER_CANNOT_READ = "document owner cannot read the Gallery image";
  static final String SKIP_DOCUMENT_PUBLISHED =
      "document is published, so the link would let anyone download the Gallery image";
  static final String SKIP_OWNERS_GROUPS_WOULD_GAIN_ACCESS =
      "Gallery image belongs to another user, so the link could let the document owner's PI,"
          + " lab admins or community admins download it";
  static final String SKIP_READER_WOULD_GAIN_ACCESS =
      "the link would let a document reader who cannot read the Gallery image download it: ";
  static final String SKIP_UNKNOWN_READER = "document is shared with an unknown user or group: ";

  @Autowired private FolderManager folderMgr;

  @Autowired private BaseRecordManager baseRecordMgr;

  @Autowired private RecordGroupSharingDao recordGroupSharingDao;

  @Autowired private FieldDao fieldDao;

  @Autowired private EcatImageDao ecatImageDao;

  @Autowired private FieldParser fieldParser;

  @Autowired private IPermissionUtils permUtils;

  @Autowired private UserDao userDao;

  @Autowired private GroupDao groupDao;

  @Override
  public List<Long> findFieldIdsWithAnnotatedImages() {
    return fieldDao.getCurrentTextFieldIdsContaining(ANNOTATED_IMAGE_MARKUP);
  }

  @Override
  public List<AnnotatedImageRelink> reportAnnotatedImagesInField(Long fieldId) {
    return checkAnnotatedImagesInField(fieldId, false);
  }

  @Override
  public List<AnnotatedImageRelink> relinkAnnotatedImagesInField(Long fieldId) {
    return checkAnnotatedImagesInField(fieldId, true);
  }

  private List<AnnotatedImageRelink> checkAnnotatedImagesInField(Long fieldId, boolean runFix) {
    // A repair locks the field with its first statement, so a concurrent repair of the same field
    // waits here until this one commits. Under REPEATABLE READ, InnoDB takes the read snapshot at
    // the first non-locking read, which comes after the lock, so the waiting repair then sees the
    // links this one restored and doesn't insert a duplicate (there is no unique key to stop it).
    Optional<Field> locked = runFix ? fieldDao.getForUpdate(fieldId) : Optional.empty();
    // the field may have changed since the candidate scan
    if (!fieldDao.isCurrentTextField(fieldId)) {
      return List.of();
    }
    Field field = locked.orElseGet(() -> fieldDao.get(fieldId));
    StructuredDocument doc = field.getStructuredDocument();
    User owner = doc.getOwner();
    Map<String, Subject> subjects = new HashMap<>();
    List<AnnotatedImageRelink> results = new ArrayList<>();
    for (Long imageId : fieldParser.findAnnotatedImageIds(field.getFieldData())) {
      List<FieldAttachment> links =
          field.getLinkedMediaFiles().stream()
              .filter(link -> imageId.equals(link.getMediaFile().getId()))
              .toList();
      if (links.stream().anyMatch(link -> !link.isDeleted())) {
        continue;
      }
      EcatImage image = ecatImageDao.getSafeNull(imageId).orElse(null);
      if (image == null) {
        continue; // nothing to link to
      }
      LinkState linkState = links.isEmpty() ? LinkState.MISSING_ROW : LinkState.DELETED_ROW;
      String skipReason = null;
      if (doc.isTemplate()) {
        // FieldManager#addMediaFileLink also shares a template's images with everyone the
        // template is shared with; a repair must not grant that access, so templates are left
        skipReason = SKIP_TEMPLATE;
      } else if (image.isDeleted()) {
        skipReason = SKIP_IMAGE_DELETED;
      } else {
        if (!canReadImage(subjects, owner, image, fieldId)) {
          skipReason = SKIP_OWNER_CANNOT_READ;
        } else {
          skipReason = accessWideningReason(subjects, doc, image, fieldId);
        }
      }
      Outcome outcome;
      if (skipReason != null) {
        outcome = Outcome.SKIPPED;
        log.info(
            "Not relinking annotated image {} in field {} of {}: {}",
            image.getGlobalIdentifier(),
            fieldId,
            doc.getGlobalIdentifier(),
            skipReason);
      } else if (runFix) {
        // signed and witnessed documents are repaired too: the repair writes audit (Envers) rows
        // for the field and the attachment, but the field content and signature hashes are
        // unchanged
        field.addMediaFileLink(image);
        fieldDao.save(field);
        outcome = Outcome.REPAIRED;
        log.info(
            "Relinked annotated image {} to field {} of {}",
            image.getGlobalIdentifier(),
            fieldId,
            doc.getGlobalIdentifier());
      } else {
        outcome = Outcome.TO_REPAIR;
      }
      results.add(
          new AnnotatedImageRelink(
              doc.getGlobalIdentifier(),
              owner.getUsername(),
              fieldId,
              image.getGlobalIdentifier(),
              linkState,
              outcome,
              skipReason));
    }
    return results;
  }

  /*
   * A link lets anyone who can read the document download the Gallery image itself
   * (PermissionUtils#isPermittedViaMediaLinksToRecords), not just see the annotated copy. Returns
   * why restoring this link could give someone that access, or null if every reader already has
   * it.
   */
  private String accessWideningReason(
      Map<String, Subject> subjects, StructuredDocument doc, EcatImage image, Long fieldId) {
    if (isPublished(doc)
        || doc.getOwnerParent().map(DevOpsManagerImpl::isPublished).orElse(false)) {
      return SKIP_DOCUMENT_PUBLISHED;
    }
    // Role-based access (PIs, lab admins, community admins, sysadmins) depends only on the record
    // owner, so it is the same for the document and for any record of the same owner.
    User owner = doc.getOwner();
    if (!ownedBy(image, owner) && !linkedToAnotherRecordOf(owner, image, fieldId)) {
      return SKIP_OWNERS_GROUPS_WOULD_GAIN_ACCESS;
    }
    // The document's ACL holds everyone it is shared with, including through a shared notebook.
    for (ACLElement element : doc.getSharingACL().getAclElements()) {
      String name = element.getUserOrGrpUniqueName();
      if (owner.getUsername().equals(name)) {
        continue;
      }
      Optional<Set<User>> readers = resolveReaders(name);
      if (readers.isEmpty()) {
        return SKIP_UNKNOWN_READER + name;
      }
      for (User reader : readers.get()) {
        if (!owner.getUsername().equals(reader.getUsername())
            && !canReadImage(subjects, reader, image, fieldId)) {
          return SKIP_READER_WOULD_GAIN_ACCESS + reader.getUsername();
        }
      }
    }
    return null;
  }

  private static boolean isPublished(BaseRecord record) {
    return record.getSharingACL().getAclElements().stream()
        .anyMatch(
            element -> RecordGroupSharing.ANONYMOUS_USER.equals(element.getUserOrGrpUniqueName()));
  }

  private static boolean ownedBy(BaseRecord record, User user) {
    return record != null && user.getUsername().equals(record.getOwner().getUsername());
  }

  private static boolean linkedToAnotherRecordOf(User owner, EcatImage image, Long fieldId) {
    Stream<BaseRecord> viaFields =
        image.getLinkedFields().stream()
            .filter(link -> !link.isDeleted() && !fieldId.equals(link.getField().getId()))
            .map(link -> link.getField().getStructuredDocument());
    Stream<BaseRecord> viaRecords =
        image.getLinkedRecords().stream().map(RecordAttachment::getRecord);
    return Stream.concat(viaFields, viaRecords)
        .anyMatch(linked -> ownedBy(linked, owner) && !linked.isDeleted());
  }

  /*
   * An ACL entry names a user, or a group optionally restricted to roles ("group[PI]").
   * RecordSharingACL#isPermitted matches groups by prefix, so members of every group whose unique
   * name starts the entry can read the document, whatever the role restriction.
   */
  private Optional<Set<User>> resolveReaders(String userOrGroupName) {
    Set<User> readers = new LinkedHashSet<>();
    userDao.getOptionalUserByUsername(userOrGroupName).ifPresent(readers::add);
    for (int end = 1; end <= userOrGroupName.length(); end++) {
      Group group = groupDao.getByUniqueName(userOrGroupName.substring(0, end));
      if (group != null) {
        readers.addAll(group.getMembers());
      }
    }
    return readers.isEmpty() ? Optional.empty() : Optional.of(readers);
  }

  /*
   * The editor's rule (FieldManager#subjectCanReadMediaFile), evaluated as the given user rather
   * than the sysadmin running the repair. Links of the field being repaired don't count, nor do
   * deleted links anywhere: a lost link must not vouch for itself.
   */
  private boolean canReadImage(
      Map<String, Subject> subjects, User user, EcatImage image, Long fieldId) {
    Subject subject = subjects.computeIfAbsent(user.getUsername(), name -> subjectFor(user));
    return subject.execute(
        () ->
            permUtils.isPermitted(image, PermissionType.READ, user)
                || readableViaOtherLinks(user, image, fieldId));
  }

  private boolean readableViaOtherLinks(User user, EcatImage image, Long fieldId) {
    Stream<BaseRecord> viaFields =
        image.getLinkedFields().stream()
            .filter(link -> !link.isDeleted() && !fieldId.equals(link.getField().getId()))
            .map(link -> link.getField().getStructuredDocument());
    Stream<BaseRecord> viaRecords =
        image.getLinkedRecords().stream().map(RecordAttachment::getRecord);
    return Stream.concat(viaFields, viaRecords)
        .filter(Objects::nonNull)
        .anyMatch(linked -> permUtils.isPermitted(linked, PermissionType.READ, user));
  }

  /*
   * Permission checks read the thread's Shiro subject, which here is the sysadmin. This builds a
   * subject for the user instead, from a security manager over the same realms that never stores
   * anything in a session, so the sysadmin's session is left untouched. Subject#execute binds it
   * only for the duration of each check.
   */
  private Subject subjectFor(User user) {
    RealmSecurityManager current = (RealmSecurityManager) SecurityUtils.getSecurityManager();
    DefaultSecurityManager securityManager = new DefaultSecurityManager(current.getRealms());
    DefaultSubjectDAO subjectDao = (DefaultSubjectDAO) securityManager.getSubjectDAO();
    ((DefaultSessionStorageEvaluator) subjectDao.getSessionStorageEvaluator())
        .setSessionStorageEnabled(false);
    PrincipalCollection principals =
        new SimplePrincipalCollection(user.getUsername(), currentRealmName());
    return new Subject.Builder(securityManager)
        .principals(principals)
        .authenticated(true)
        .sessionCreationEnabled(false)
        .buildSubject();
  }

  /* Same choice of realm as PermissionUtils#doRunAs. */
  private String currentRealmName() {
    PrincipalCollection principals = SecurityUtils.getSubject().getPrincipals();
    if (principals == null || principals.getRealmNames().isEmpty()) {
      return ShiroRealm.DEFAULT_USER_PASSWD_REALM;
    }
    return principals.getRealmNames().iterator().next();
  }

  @Override
  public String fixRecord(GlobalIdentifier oid, User subject, boolean runFix) {
    String result;
    if (GlobalIdPrefix.FL.equals(oid.getPrefix())) {
      result = checkFolderFixes(oid, subject, runFix);
    } else {
      result =
          String.format(
              "No checks nor fixes currently implemented for records of type '%s'.",
              oid.getPrefix());
    }
    return result;
  }

  private String checkFolderFixes(GlobalIdentifier oid, User subject, boolean runFix) {
    Folder folder = folderMgr.getFolder(oid.getDbId(), subject);
    String result =
        String.format(
            "Found a folder '%s' (type: %s).<br/>\n",
            HtmlUtils.htmlEscape(folder.getName()), folder.getType());
    if (isSharedFolderMovedIntoWorkspace(folder)) {
      result +=
          "This is a shared folder, but is outside of 'Shared' hierarchy, which seems"
              + " wrong.<br/>\n";
      result += runSharedFolderMovedIntoWorkspaceFix(runFix, folder);
    } else {
      result += "It seems fine.<br/>\n";
    }
    return result;
  }

  private boolean isSharedFolderMovedIntoWorkspace(Folder folder) {
    return folder.hasType(RecordType.SHARED_FOLDER) && !folder.getParent().isSharedFolder();
  }

  private String runSharedFolderMovedIntoWorkspaceFix(boolean runFix, Folder folder) {
    String result = "";

    /* check if user who now has the shared folder on their Workspace has access to any better location */
    User parentFolderOwner = folder.getParent().getOwner();
    List<Folder> correctTargetFolders =
        findAcceptableTargetFoldersForUser(parentFolderOwner, folder);

    /* as a precaution, automatic fix will run only if there is exactly one possible move target */
    Folder correctTarget = null;
    if (correctTargetFolders.isEmpty()) {
      result += "Couldn't find any good new location for the folder.<br/>\n";
    } else if (correctTargetFolders.size() > 1) {
      result += "Seems like there is more than one possible group to move the folder back.<br/>\n";
    } else {
      correctTarget = correctTargetFolders.get(0);
      result +=
          String.format(
              "Found one possible target for moving the folder: '%s' (%s).<br/>\n",
              HtmlUtils.htmlEscape(correctTarget.getName()), correctTarget.getGlobalIdentifier());
    }

    if (correctTarget != null) {
      if (runFix) {
        result += "Attempting to apply the fix... ";
        ServiceOperationResult<Folder> moveResult =
            folderMgr.move(
                folder.getId(),
                correctTarget.getId(),
                folder.getParent().getId(),
                parentFolderOwner);
        if (moveResult.isSucceeded()) {
          result += "the folder moved successfully.<br/>\n";
        } else {
          result += "move attempt was unsuccessful, for unknown reason.<br/>\n";
        }
      } else {
        result +=
            String.format(
                "To move current folder %s into folder %s call the current URL "
                    + "with '?update=true' suffix.<br/>\n",
                folder.getGlobalIdentifier(), correctTarget.getGlobalIdentifier());
      }
    }
    return result;
  }

  private List<Folder> findAcceptableTargetFoldersForUser(User user, Folder folder) {
    List<Folder> results = new ArrayList<>();

    /* for each of the user's groups, check if group's folder is a good location */
    Set<Group> ownerGroups = user.getGroups();
    for (Group currentGroup : ownerGroups) {
      List<BaseRecord> recordsSharedWithGroup =
          recordGroupSharingDao.getRecordsSharedByGroup(currentGroup.getId());
      if (isSharedFolderOrRecordSharedWithGroup(folder, recordsSharedWithGroup, user)) {
        results.add(folderMgr.getFolder(currentGroup.getCommunalGroupFolderId(), user));
      }
    }
    return results;
  }

  private boolean isSharedFolderOrRecordSharedWithGroup(
      BaseRecord recordToCheck, List<BaseRecord> recordsSharedWithGroup, User user) {
    if (recordToCheck.isFolder() && !recordToCheck.isNotebook()) {
      // for folders, confirm it's a shared folder & trigger the check for subfolders
      Folder folderToCheck = (Folder) recordToCheck;
      if (!folderToCheck.isSharedFolder()) {
        return false;
      }
      List<Long> childrenIds = folderMgr.getFolderChildrenIds(folderToCheck);
      for (Long childId : childrenIds) {
        BaseRecord child = baseRecordMgr.get(childId, user);
        if (!isSharedFolderOrRecordSharedWithGroup(child, recordsSharedWithGroup, user)) {
          return false;
        }
      }
    } else {
      // for record/notebook, confirm it's among records shared with group
      if (!recordsSharedWithGroup.contains(recordToCheck)) {
        return false;
      }
    }
    return true;
  }
}
