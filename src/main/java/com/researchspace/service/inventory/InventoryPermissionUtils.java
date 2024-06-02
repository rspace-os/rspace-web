package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.dao.ListOfMaterialsDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.inventory.MovableInventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("inventoryPermissionUtils")
public class InventoryPermissionUtils {

  private @Autowired SampleDao sampleDao;
  private @Autowired ListOfMaterialsDao lomDao;

  private @Autowired UserManager userMgr;
  private @Autowired IPermissionUtils elnPermissionUtils;

  private @Autowired MessageSourceUtils messages;
  private @Autowired InventoryRecordRetriever invRecRetriever;

  /**
   * @return distinct list that contains username of the current user, plus usernames of all users
   *     belonging to one of current user's group
   */
  public List<String> getUsernameOfUserAndAllMembersOfTheirGroups(User user) {
    return Stream.concat(
            Stream.of(user.getUsername()),
            user.getGroups().stream().flatMap(g -> g.getMembers().stream()).map(User::getUsername))
        .distinct()
        .collect(Collectors.toList());
  }

  private List<String> getUsernamesOfGroupMembersWhereUserIsPIorLA(User user) {
    return user.getUserGroups().stream()
        .filter(ug -> ug.isPIRole() || (ug.isAdminRole() && ug.isAdminViewDocsEnabled()))
        .map(UserGroup::getGroup)
        .flatMap(g -> g.getMembers().stream())
        .map(User::getUsername)
        .distinct()
        .collect(Collectors.toList());
  }

  public boolean isInventoryOwnerReadableByUser(String owner, User user) {
    if (user.hasSysadminRole()) {
      return true;
    }
    if (user.hasRole(Role.ADMIN_ROLE)) {
      return isItemOwnerWithinAdminCommunity(owner, user);
    }
    // FIXME another case is PI who can access group members' items, and that's it - for regular
    // users access needs to be decided by sharingMode, not by being group member with owner
    return getUsernameOfUserAndAllMembersOfTheirGroups(user).contains(owner);
  }

  public List<String> getOwnersVisibleWithUserRole(User user) {
    if (user.hasRole(Role.ADMIN_ROLE)) {
      return getCommunityMemberUnamesForCommunityAdmin(user);
    }
    return getUsernamesOfGroupMembersWhereUserIsPIorLA(user);
  }

  public boolean canUserReadInventoryRecord(GlobalIdentifier invRecOid, User user) {
    InventoryRecord invRec = getInvRecByGlobalIdOrThrowNotFoundException(invRecOid);
    return canUserReadInventoryRecord(invRec, user);
  }

  public InventoryRecord getInvRecByGlobalIdOrThrowNotFoundException(GlobalIdentifier invRecOid) {
    InventoryRecord invRec = invRecRetriever.getInvRecordByGlobalId(invRecOid);
    if (invRec == null) {
      throwNotFoundException(invRecOid.getDbId());
    }
    return invRec;
  }

  public boolean canUserReadInventoryRecord(InventoryRecord record, User user) {
    if (isUserAnItemOwner(record, user)) {
      return true; // owner has full access
    }
    return canAccessDirectlyWithSharingPermission(record, user)
        || canViewWithUserRole(record, user)
        || isContainerRelatedReadAccessAllowed(record, user)
        || isDefaultSampleTemplate(record)
        || canAccessAsSystemOrCommunityAdmin(record.getOwner().getUsername(), user);
  }

  private boolean canAccessDirectlyWithSharingPermission(InventoryRecord record, User user) {
    InventoryRecord recordForPermissionCheck = record;
    if (record.isSubSample()) {
      // subsamples don't have own permissions yet, delegate to sample's permissions
      recordForPermissionCheck = ((SubSample) record).getSample();
    }
    if (InventorySharingMode.OWNER_ONLY.equals(recordForPermissionCheck.getSharingMode())) {
      return isUserAnItemOwner(record, user);
    }

    boolean result = false;
    if (InventorySharingMode.OWNER_GROUPS.equals(recordForPermissionCheck.getSharingMode())) {
      result =
          getUsernameOfUserAndAllMembersOfTheirGroups(user)
              .contains(recordForPermissionCheck.getOwner().getUsername());
    } else {
      result = isUserInWhitelistedGroupForRecord(recordForPermissionCheck, user);
    }
    return result;
  }

  private boolean canViewWithUserRole(InventoryRecord record, User user) {
    return getOwnersVisibleWithUserRole(user).contains(record.getOwner().getUsername());
  }

  private boolean canAccessAsSystemOrCommunityAdmin(String userToAccess, User currentUser) {
    if (currentUser.hasSysadminRole()) {
      return true;
    }
    if (currentUser.hasAdminRole()) {
      return isItemOwnerWithinAdminCommunity(userToAccess, currentUser);
    }
    return false;
  }

  private boolean isItemOwnerWithinAdminCommunity(String ownerUsername, User commAdmin) {
    List<String> communityMemberUnames = getCommunityMemberUnamesForCommunityAdmin(commAdmin);
    return communityMemberUnames.contains(ownerUsername);
  }

  private List<String> getCommunityMemberUnamesForCommunityAdmin(User commAdmin) {
    return Stream.concat(
            Stream.of(commAdmin.getUsername()),
            userMgr.getAllUsersInAdminsCommunity(commAdmin.getUsername()).stream()
                .map(User::getUsername))
        .distinct()
        .collect(Collectors.toList());
  }

  private boolean isContainerRelatedReadAccessAllowed(InventoryRecord record, User user) {
    // if the item is a container, and user has access to item in that container, they can read the
    // container
    if (record.isContainer()) {
      Container container = (Container) record;
      for (SubSample ss : container.getStoredSubSamples()) {
        if (canAccessDirectlyWithSharingPermission(ss, user)) {
          return true;
        }
      }
      for (Container cont : container.getStoredContainers()) {
        if (canAccessDirectlyWithSharingPermission(cont, user)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isDefaultSampleTemplate(InventoryRecord record) {
    if (record.isSample()) {
      Sample sample = (Sample) record;
      return sample.isTemplate()
          && sample.getOwner().getUsername().equals(sampleDao.getDefaultTemplatesOwner());
    }
    return false;
  }

  public void assertUserCanReadOrLimitedReadInventoryRecord(InventoryRecord invRec, User user) {
    boolean canRead = canUserReadOrLimitedReadInventoryRecord(invRec, user);
    if (!canRead) {
      throwNotFoundException(invRec.getId());
    }
  }

  public InventoryRecord assertUserCanReadOrLimitedReadInventoryRecord(
      GlobalIdentifier invRecGlobalId, User user) {
    InventoryRecord invRec = getInvRecByGlobalIdOrThrowNotFoundException(invRecGlobalId);
    assertUserCanReadOrLimitedReadInventoryRecord(invRec, user);
    return invRec;
  }

  private void throwNotFoundException(Long recId) {
    String msg = messages.getResourceNotFoundMessage("Inventory record", recId);
    throw new NotFoundException(msg);
  }

  public boolean canUserEditInventoryRecord(InventoryRecord record, User user) {
    if (isUserAnItemOwner(record, user)) {
      return true; // owner has full access
    }
    return canAccessDirectlyWithSharingPermission(record, user);
  }

  private boolean isUserAnItemOwner(InventoryRecord item, User user) {
    return item.getOwner().getUsername().equals(user.getUsername());
  }

  private boolean isUserInWhitelistedGroupForRecord(InventoryRecord record, User user) {
    List<String> sharedWith = record.getSharedWithUniqueNames();
    return user.getGroups().stream().map(Group::getUniqueName).anyMatch(sharedWith::contains);
  }

  public void assertUserCanEditInventoryRecord(InventoryRecord record, User user) {
    boolean canEdit = canUserEditInventoryRecord(record, user);
    if (!canEdit) {
      if (canUserReadInventoryRecord(record, user)) {
        // can read, just can't edit. we can show more useful message
        throw new IllegalArgumentException(
            record.getType()
                + " with id ["
                + record.getId()
                + "] cannot be edited by current User");
      }
      throwNotFoundException(record.getId());
    }
    if (record.isDeleted()) {
      throw new IllegalArgumentException(
          record.getType() + " with id [" + record.getId() + "] is deleted and cannot be edited");
    }
  }

  public InventoryRecord assertUserCanEditInventoryRecord(
      GlobalIdentifier invRecGlobalId, User user) {
    InventoryRecord invRec = getInvRecByGlobalIdOrThrowNotFoundException(invRecGlobalId);
    assertUserCanEditInventoryRecord(invRec, user);
    return invRec;
  }

  public void assertUserCanDeleteInventoryRecord(InventoryRecord record, User user) {
    boolean canEdit = canUserEditInventoryRecord(record, user);
    if (!canEdit) {
      if (canUserReadInventoryRecord(record, user)) {
        // can read, just can't edit. we can show more useful message
        throw new IllegalArgumentException(
            record.getType()
                + " with id ["
                + record.getId()
                + "] cannot be restored by current User");
      }
      throwNotFoundException(record.getId());
    }
  }

  public void assertUserCanTransferInventoryRecord(InventoryRecord record, User user) {
    boolean canRead = canUserReadInventoryRecord(record, user);
    if (!canRead) {
      throwNotFoundException(record.getId());
    }
    // can read, just can't transfer. we can show more useful message
    String invRecOwner = record.getOwner().getUsername();
    if (!user.getUsername().equals(invRecOwner)
        && !isPiOrAdminOfRecordOwnerGroup(user, invRecOwner)) {
      throw new IllegalArgumentException(
          record.getType()
              + " with id ["
              + record.getId()
              + "] cannot be transferred by current User");
    }
    if (record.isDeleted()) {
      throw new IllegalArgumentException(
          record.getType() + " with id [" + record.getId() + "] is deleted and cannot be edited");
    }
  }

  // user is a PI(or Lab admin) of the group
  private boolean isPiOrAdminOfRecordOwnerGroup(User user, String invRecOwner) {
    for (Group group : user.getGroups()) {
      if (user.isPiOrLabAdminOfGroup(group)
          && group.getMembers().stream()
              .map(User::getUsername)
              .collect(Collectors.toSet())
              .contains(invRecOwner)) {
        return true;
      }
    }
    return false;
  }

  public void setPermissionsInApiInventoryRecord(
      ApiInventoryRecordInfo apiInvRec, InventoryRecord invRec, User user) {
    String invRecOwner = apiInvRec.getOwner().getUsername();
    if (canUserEditInventoryRecord(invRec, user)) {
      apiInvRec.addPermittedAction(ApiInventoryRecordPermittedAction.READ);
      apiInvRec.addPermittedAction(ApiInventoryRecordPermittedAction.UPDATE);
    } else if (canUserReadInventoryRecord(invRec, user)) {
      apiInvRec.addPermittedAction(ApiInventoryRecordPermittedAction.READ);
    } else if (canUserLimitedReadInventoryRecord(invRec, user)) {
      apiInvRec.addPermittedAction(ApiInventoryRecordPermittedAction.LIMITED_READ);
    }
    if (user.getUsername().equals(invRecOwner)
        || isPiOrAdminOfRecordOwnerGroup(user, invRecOwner)
        || user.hasSysadminRole()) {
      // FIXME community admin should also have TRANSFER permission
      apiInvRec.addPermittedAction(ApiInventoryRecordPermittedAction.CHANGE_OWNER);
    }
  }

  public boolean canUserLimitedReadInventoryRecord(GlobalIdentifier invRecOid, User user) {
    InventoryRecord invRec = getInvRecByGlobalIdOrThrowNotFoundException(invRecOid);
    return canUserLimitedReadInventoryRecord(invRec, user);
  }

  public boolean canUserLimitedReadInventoryRecord(InventoryRecord invRec, User user) {
    return isContainerRelatedLimitedReadAllowed(invRec, user)
        || isSampleLimitedReadAllowedThroughSubsampleLimitedRead(invRec, user)
        || isLimitedReadAllowedThroughLom(invRec, user)
        || isTemplateLimitedReadAllowedThroughSample(invRec, user);
  }

  private boolean isContainerRelatedLimitedReadAllowed(InventoryRecord record, User user) {
    // user can limited-read content of the container they can read
    if (record instanceof MovableInventoryRecord) {
      MovableInventoryRecord movableRecord = (MovableInventoryRecord) record;
      if (movableRecord.getParentContainer() != null
          && canUserReadInventoryRecord(movableRecord.getParentContainer(), user)) {
        return true;
      }
    }
    return false;
  }

  private boolean isSampleLimitedReadAllowedThroughSubsampleLimitedRead(
      InventoryRecord invRec, User user) {
    if (invRec.isSample()) {
      Sample sample = (Sample) invRec;
      for (SubSample ss : sample.getSubSamples()) {
        if (canUserLimitedReadInventoryRecord(ss, user)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isLimitedReadAllowedThroughLom(InventoryRecord invRec, User user) {
    List<ListOfMaterials> lomsWithInvRec = lomDao.findLomsByInvRecGlobalId(invRec.getOid());
    for (ListOfMaterials lom : lomsWithInvRec) {
      boolean docReadPermitted =
          elnPermissionUtils.isPermitted(
              lom.getElnField().getStructuredDocument(), PermissionType.READ, user);
      if (docReadPermitted) {
        return true;
      }
    }
    return false;
  }

  private boolean isTemplateLimitedReadAllowedThroughSample(InventoryRecord invRec, User user) {
    if (invRec.isSample()) {
      Sample recordAsSample = (Sample) invRec;
      if (recordAsSample.isTemplate()) {
        PaginationCriteria<Sample> allSamplesPgCrit =
            PaginationCriteria.createDefaultForClass(Sample.class);
        allSamplesPgCrit.setResultsPerPage(Integer.MAX_VALUE);
        List<Sample> dbSamplesFromTemplate =
            sampleDao
                .getSamplesForUser(allSamplesPgCrit, recordAsSample.getId(), null, null, user)
                .getResults();
        for (Sample sa : dbSamplesFromTemplate) {
          if (canUserReadInventoryRecord(sa, user)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public boolean canUserReadOrLimitedReadInventoryRecord(InventoryRecord invRec, User user) {
    return canUserReadInventoryRecord(invRec, user)
        || canUserLimitedReadInventoryRecord(invRec, user);
  }

  public boolean canUserReadOrLimitedReadInventoryRecord(GlobalIdentifier invRecOid, User user) {
    InventoryRecord invRec = getInvRecByGlobalIdOrThrowNotFoundException(invRecOid);
    return canUserReadOrLimitedReadInventoryRecord(invRec, user);
  }
}
