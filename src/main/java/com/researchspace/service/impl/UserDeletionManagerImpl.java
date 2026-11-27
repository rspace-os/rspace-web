package com.researchspace.service.impl;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.dao.UserDeletionDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.FormsAndTemplatesUsage;
import com.researchspace.service.GroupManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.TransferService;
import com.researchspace.service.UserDeletionManager;
import com.researchspace.service.UserDeletionPolicy;
import com.researchspace.service.UserFolderCreator;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("userDeletionManager")
public class UserDeletionManagerImpl implements UserDeletionManager {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired UserDao userDao;
  private @Autowired MessageSourceUtils msgSource;
  private @Autowired GroupManager grpMgr;
  private @Autowired CommunityServiceManager communityMgr;
  private @Autowired CommunityServiceManager communityDao;
  private @Autowired FormDao formDao;
  private @Autowired RecordDao recordDao;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired UserDeletionDao deletionDao;
  private @Autowired FileMetadataDao fileMetadataDao;
  private @Autowired RecordSharingManager sharingMgr;
  private @Autowired FolderDao folderDao;
  private @Autowired FolderManager folderMgr;

  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;

  private @Autowired DeletedUserResourcesListHelper deletedUserResourcesHelper;

  private @Autowired @Qualifier("formTransferService") TransferService formTransferService;
  private @Autowired @Qualifier("templateTransferService") TransferService templateTransferService;

  @Override
  public FormsAndTemplatesUsage getFormsAndTemplatesUsage(Long userToDeleteId) {
    if (!permUtils.isPermitted("USER:DELETE")) {
      throw new AuthorizationException("Not permitted to check usage before deleting a user");
    }
    User toDelete = userDao.get(userToDeleteId);
    return new FormsAndTemplatesUsage(
        formDao.hasUserPublishedFormsUsedInOtherRecords(toDelete),
        recordDao.hasUserSharedTemplatesUsedByOtherUsers(toDelete));
  }

  @Override
  public ServiceOperationResult<User> removeUser(
      Long userId, UserDeletionPolicy policy, User subject) {
    Optional<String> errors = validate(userId, policy, subject);
    if (errors.isPresent()) {
      return new ServiceOperationResult<>(null, false, errors.get());
    }
    User toDelete = userDao.get(userId);

    if (recordDao.hasUserSharedTemplatesUsedByOtherUsers(toDelete)) {
      templateTransferService.transferOwnership(toDelete, subject);
    }

    if (formDao.hasUserPublishedFormsUsedInOtherRecords(toDelete)) {
      formTransferService.transferOwnership(toDelete, subject);
    }

    // Collect filestore resources after transfers so that any gallery items whose FileProperty
    // owner was updated to the new owner are excluded from the deletion list.
    Optional<String> saveResourcesListError = saveFilestoreResourcesTempList(toDelete);
    if (saveResourcesListError.isPresent()) {
      return new ServiceOperationResult<>(null, false, saveResourcesListError.get());
    }

    if (policy.isForceDelete()) {
      for (Group group : toDelete.getGroups()) {
        String msg;
        if ((msg = validateGroupMembershipCriteria(toDelete, group, policy)) != null) {
          return new ServiceOperationResult<>(null, false, msg);
        }
      }
      revokeIncomingIndividualShares(toDelete);
      // only remove from groups if all group-leaving-criteria are satisfied.
      for (Group group : toDelete.getGroups()) {
        grpMgr.removeUserFromGroup(toDelete.getUsername(), group.getId(), subject);
      }
      unlinkEmptyIndividualSharedFolders(toDelete);
      removeRemainingAclEntries(toDelete);

      if (toDelete.hasRole(Role.ADMIN_ROLE)) {
        communityDao
            .listCommunitiesForAdmin(toDelete.getId())
            .forEach(comm -> communityDao.removeAdminFromCommunity(toDelete.getId(), comm.getId()));
      }
    }

    if (policy.isTempUserOnlyDelete() && toDelete.isTempAccount()) {
      revokeIncomingIndividualShares(toDelete);
      unlinkEmptyIndividualSharedFolders(toDelete);
      removeRemainingAclEntries(toDelete);
    }

    ServiceOperationResult<User> deletionResult = deletionDao.deleteUser(userId, policy);
    updateTempResourcesListAfterDatabaseDeletion(userId, deletionResult);

    return deletionResult;
  }

  /**
   * Revokes the shares other users made with the user being deleted, so no access survives for a
   * later account with the same username.
   */
  private void revokeIncomingIndividualShares(User toDelete) {
    List<RecordGroupSharing> shares = sharingMgr.getIndividualSharesWithUser(toDelete);
    int revoked = 0;
    for (RecordGroupSharing share : shares) {
      if (sharingMgr.revokeIndividualShare(share, false)) {
        revoked++;
      }
    }
    log.info(
        "Revoked {} of {} shares with deleted user [{}]", revoked, shares.size(), toDelete.getId());
  }

  // Unlinks the folders other users own for sharing with the deleted user, once they hold nothing
  // but the deleted user's own records. Folders the deleted user owns are deleted with the user.
  private void unlinkEmptyIndividualSharedFolders(User toDelete) {
    List<Folder> shareItemsFolders = new ArrayList<>();
    shareItemsFolders.add(folderDao.getIndividualSharedItemsFolderForUser(toDelete));
    shareItemsFolders.add(
        folderDao.getSystemFolderForUserByName(
            toDelete,
            UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX
                + Folder.INDIVIDUAL_SHARE_ITEMS_FLDER_NAME));
    for (Folder shareItemsFolder : shareItemsFolders) {
      if (shareItemsFolder == null) {
        continue;
      }
      for (BaseRecord child : folderDao.getChildren(shareItemsFolder)) {
        if (!child.isFolder()
            || !child.hasType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT)
            || child.getOwner().equals(toDelete)) {
          continue;
        }
        Folder pairFolder = (Folder) child;
        boolean onlyDeletedUsersItems =
            folderDao.getChildren(pairFolder).stream()
                .allMatch(item -> item.getOwner().equals(toDelete));
        if (!onlyDeletedUsersItems) {
          log.warn("Shared folder [{}] still holds other users' items, kept", pairFolder.getId());
          continue;
        }
        for (Folder parent : new ArrayList<>(pairFolder.getParentFolders())) {
          folderMgr.removeBaseRecordFromFolder(
              pairFolder, parent.getId(), ACLPropagationPolicy.NULL_POLICY);
        }
      }
    }
  }

  // Removes access-list entries naming the deleted user that no share removal reached, such as the
  // entry for both users on a shared folder or residue of older versions.
  private void removeRemainingAclEntries(User toDelete) {
    List<Long> changed = new ArrayList<>();
    for (BaseRecord item : deletionDao.getOtherUsersRecordsNamingUserInAcl(toDelete)) {
      if (item.getSharingACL().removeACLsforUserOrGroup(toDelete).isEmpty()) {
        continue;
      }
      if (item.isFolder()) {
        folderDao.save((Folder) item);
      } else {
        recordDao.save((Record) item);
      }
      changed.add(item.getId());
    }
    if (!changed.isEmpty()) {
      log.info("Removed access entries of deleted user [{}] from {}", toDelete.getId(), changed);
    }
  }

  /**
   * @return error message if there was a problem with saving the list.
   */
  private Optional<String> saveFilestoreResourcesTempList(User toDelete) {
    List<File> deletedUserResources = fileMetadataDao.collectUserFilestoreResources(toDelete);
    boolean resourcesListCorrect = fileStore.verifyUserFilestoreFiles(deletedUserResources);
    if (!resourcesListCorrect) {
      return Optional.of("List of filestore resource marked for deletion seem incorrect");
    }
    boolean resourcesListSaved =
        deletedUserResourcesHelper.saveUserResourcesListToTemporaryFile(
            toDelete.getId(), deletedUserResources);
    if (!resourcesListSaved) {
      return Optional.of("Couldn't save list of filestore resource marked for deletion");
    }
    return Optional.empty();
  }

  private void updateTempResourcesListAfterDatabaseDeletion(
      Long userId, ServiceOperationResult<User> databaseDeletionResult) {
    if (databaseDeletionResult.isSucceeded()) {
      deletedUserResourcesHelper.markTempUserResourcesListAsFinal(userId);
    } else {
      deletedUserResourcesHelper.removeResourcesListFile(userId, true);
    }
  }

  /**
   * @return file storing list of filestore resource paths belonging to the user, if exists
   */
  @Override
  public ServiceOperationResult<Integer> deleteRemovedUserFilestoreResources(
      Long deletedUserId, boolean removeListingFile, User subject) {
    if (!subject.hasSysadminRole()) {
      String errorMsg = msgSource.getMessage("userDeletion.errors.sysadminRequired");
      log.warn(errorMsg);
      return new ServiceOperationResult<>(-1, false, errorMsg);
    }

    Optional<List<File>> resourcesOpt =
        deletedUserResourcesHelper.retrieveUserResourcesList(deletedUserId);
    if (resourcesOpt.isEmpty()) {
      String errorMsg = msgSource.getMessage("userDeletion.errors.resourceListUnreadable");
      log.warn(errorMsg);
      return new ServiceOperationResult<>(-1, false, errorMsg);
    }

    Optional<Integer> deletedResourcesCountOpt =
        fileStore.removeUserFilestoreFiles(resourcesOpt.get());
    if (deletedResourcesCountOpt.isEmpty()) {
      String errorMsg = msgSource.getMessage("userDeletion.errors.filestoreRemovalFailed");
      log.warn(errorMsg);
      return new ServiceOperationResult<>(-1, false, errorMsg);
    }
    int deletedResourcesCount = deletedResourcesCountOpt.get();
    log.info(
        "successfully deleted {} filestore files belonging to user {}",
        deletedResourcesCount,
        deletedUserId);

    if (removeListingFile) {
      boolean removeListingResult =
          deletedUserResourcesHelper.removeResourcesListFile(deletedUserId, false);
      if (removeListingResult) {
        deletedResourcesCount++;
      }
    }
    return new ServiceOperationResult<>(deletedResourcesCount, true);
  }

  @Override
  public ServiceOperationResult<User> isUserRemovable(
      Long userToDeleteId, UserDeletionPolicy policy, User subject) {
    Optional<String> optionalErrMsg = validate(userToDeleteId, policy, subject);
    User toDelete = userDao.get(userToDeleteId);
    if (optionalErrMsg.isPresent()) {
      return new ServiceOperationResult<>(toDelete, false, optionalErrMsg.get());
    } else {
      return new ServiceOperationResult<>(toDelete, true, "");
    }
  }

  private Optional<String> validate(Long userToDeleteId, UserDeletionPolicy policy, User subject) {
    if (!deletedUserResourcesHelper.isUserResourcesListWriteable()) {
      return Optional.of(
          "sysadmin.delete.user.resourceList.folder points to "
              + deletedUserResourcesHelper.getDeletedUserResourcesListFolderLocation()
              + ", which is not a writeable location");
    }
    if (!permUtils.isPermitted("USER:DELETE")) {
      throw new AuthorizationException(subject + " cannot delete a user!");
    }
    // can't delete yourself
    User toDelete = userDao.get(userToDeleteId);
    if (toDelete.equals(subject)) {
      log.warn("{} attempted to self-delete!", userToDeleteId);
      return Optional.of(msgSource.getMessage("errors.deleteUser.nonSelf"));
    }
    if (toDelete.hasRole(Role.ADMIN_ROLE)) {
      if (communityMgr.isUserUniqueAdminInAnyCommunity(toDelete)) {
        log.warn(
            "This user  [userid={}] is the only administrator of a Community. Please replace"
                + " the administrator so this user can be deleted",
            userToDeleteId);
        return Optional.of(msgSource.getMessage("errors.deleteAdminUser"));
      }
    }
    if (toDelete.hasSysadminRole()) {
      if (!isValidSysadminDeletion(toDelete)) {
        log.warn(" invalid attempt to delete an sysadmin user! [userid={}]", userToDeleteId);
        return Optional.of(msgSource.getMessage("errors.deleteSysadminUser"));
      }
    }
    if (policy.isForceDelete()) {
      for (Group group : toDelete.getGroups()) {
        String msg;
        if ((msg = validateGroupMembershipCriteria(toDelete, group, policy)) != null) {
          return Optional.of(msg);
        }
      }
    }
    return Optional.empty();
  }

  private String validateGroupMembershipCriteria(
      User toDelete, Group group, UserDeletionPolicy policy) {
    String failureMsg = null;
    if (group.isOnlyGroupPi(toDelete.getUsername())) {
      failureMsg = msgSource.getMessage("groups.edit.errors.cannotRemoveLastAdminOrPi");
    } else if (group.getOwner().equals(toDelete)) {
      failureMsg = msgSource.getMessage("groups.edit.errors.noGroupOwnerDelete");
    } else if (!isAllGroupCapableOfDeletion(policy, group)) {
      failureMsg = msgSource.getMessage("groups.userDeletion.errors.activeMembersPreventDeletion");
    }
    return failureMsg;
  }

  private boolean isAllGroupCapableOfDeletion(UserDeletionPolicy policy, Group group) {
    if (policy.isStrictPreserveDataForGroup()) {
      for (User user : group.getMembers()) {
        if (user.getLastLogin() != null
            && user.getLastLogin().after(policy.getLastLoginCutOffForGroup())) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean isValidSysadminDeletion(User toDelete) {
    if (!toDelete.hasSysadminRole() || toDelete.getUsername().equals(Constants.SYSADMIN_UNAME)) {
      return false;
    }
    PaginationCriteria<User> pg = PaginationCriteria.createDefaultForClass(User.class);
    pg.setGetAllResults();
    ISearchResults<User> sysadmins = userDao.listUsersByRole(Role.SYSTEM_ROLE, pg);
    // ensure that at least 1 valid sysadmin remains
    return sysadmins.getTotalHits() >= 2
        && sysadmins.getResults().stream()
            .filter(u -> !u.equals(toDelete))
            .filter(User::isEnabled)
            .anyMatch(User::isAccountNonLocked);
  }

  /*
   *  for testing
   */
  void setMessageSource(MessageSourceUtils msgSource) {
    this.msgSource = msgSource;
  }
}
