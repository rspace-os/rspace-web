package com.researchspace.service.impl;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.UserDao;
import com.researchspace.dao.UserDeletionDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.UserDeletionManager;
import com.researchspace.service.UserDeletionPolicy;
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service("userDeletionManager")
public class UserDeletionManagerImpl implements UserDeletionManager {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired UserDao userDao;
  private @Autowired MessageSource msgSource;
  private @Autowired GroupManager grpMgr;
  private @Autowired CommunityServiceManager communityMgr;
  private @Autowired CommunityServiceManager communityDao;
  private @Autowired FormDao formDao;
  private @Autowired IPermissionUtils permUtils;
  private @Autowired UserDeletionDao deletionDao;
  private @Autowired FileMetadataDao fileMetadataDao;

  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;

  private @Autowired DeletedUserResourcesListHelper deletedUserResourcesHelper;

  @Override
  public ServiceOperationResult<User> removeUser(
      Long userId, UserDeletionPolicy policy, User subject) {
    Optional<String> errors = validate(userId, policy, subject);
    if (errors.isPresent()) {
      return new ServiceOperationResult<>(null, false, errors.get());
    }
    User toDelete = userDao.get(userId);

    if (formDao.hasUserPublishedFormsUsedinOtherRecords(toDelete)) {
      formDao.transferOwnershipOfFormsToSysAdmin(toDelete, subject);
    }

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
      // only remove from groups if all group-leaving-criteria are satisfied.
      for (Group group : toDelete.getGroups()) {
        grpMgr.removeUserFromGroup(toDelete.getUsername(), group.getId(), subject);
      }

      if (toDelete.hasRole(Role.ADMIN_ROLE)) {
        communityDao
            .listCommunitiesForAdmin(toDelete.getId())
            .forEach(comm -> communityDao.removeAdminFromCommunity(toDelete.getId(), comm.getId()));
      }
    }

    ServiceOperationResult<User> deletionResult = deletionDao.deleteUser(userId, policy);
    updateTempResourcesListAfterDatabaseDeletion(userId, deletionResult);

    return deletionResult;
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
      String errorMsg = "Only user with sysadmin role can delete filestore resources";
      log.warn(errorMsg);
      return new ServiceOperationResult<>(-1, false, errorMsg);
    }

    Optional<List<File>> resourcesOpt =
        deletedUserResourcesHelper.retrieveUserResourcesList(deletedUserId);
    if (resourcesOpt.isEmpty()) {
      String errorMsg = "Resource list file not readable";
      log.warn(errorMsg);
      return new ServiceOperationResult<>(-1, false, errorMsg);
    }

    Optional<Integer> deletedResourcesCountOpt =
        fileStore.removeUserFilestoreFiles(resourcesOpt.get());
    if (deletedResourcesCountOpt.isEmpty()) {
      String errorMsg = "Problem during filestore removal";
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
      return Optional.of(msgSource.getMessage("errors.deleteuser.nonself", null, null));
    }
    if (toDelete.hasRole(Role.ADMIN_ROLE)) {
      if (communityMgr.isUserUniqueAdminInAnyCommunity(toDelete)) {
        log.warn(
            "This user  [userid={}] is the only administrator of a Community. Please replace"
                + " the administrator so this user can be deleted",
            userToDeleteId);
        return Optional.of(msgSource.getMessage("errors.deleteadminuser", null, null));
      }
    }
    if (toDelete.hasSysadminRole()) {
      if (!isValidSysadminDeletion(toDelete)) {
        log.warn(" invalid attempt to delete an sysadmin user! [userid={}]", userToDeleteId);
        return Optional.of(msgSource.getMessage("errors.deletesysadminuser", null, null));
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
      failureMsg = msgSource.getMessage("group.edit.mustbe1.admin.error.msg", null, null);
    } else if (group.getOwner().equals(toDelete)) {
      failureMsg = msgSource.getMessage("group.edit.nogroupownerdelete.error.msg", null, null);
    } else if (!isAllGroupCapableOfDeletion(policy, group)) {
      failureMsg = msgSource.getMessage("group.edit.emptygrouprequired.error.msg", null, null);
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
  void setMessageSource(MessageSource msgSource) {
    this.msgSource = msgSource;
  }
}
