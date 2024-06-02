package com.researchspace.service.impl;

import static com.researchspace.Constants.SYSADMIN_UNAME;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.researchspace.model.*;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.OperationFailedMessageGenerator;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/** This class is not transactional: each call to groupManager occurs in own transaction */
@Service("defaultGroupCreationStrategy")
@Slf4j
public class DefaultGroupCreationStrategy implements IGroupCreationStrategy {
  @Autowired private AuditTrailService auditTrail;
  private GroupManager grpManager;

  @Autowired private CommunityServiceManager communityServiceManager;

  @Autowired private OperationFailedMessageGenerator authGenerator;

  @Autowired
  public void setGrpManager(GroupManager grpManager) {
    this.grpManager = grpManager;
  }

  private Validator groupValidator = null;

  @Autowired
  @Qualifier(value = "grpValidator")
  public void setGroupValidator(Validator groupvalidator) {
    this.groupValidator = groupvalidator;
  }

  @Override
  public Group createAndSaveGroup(User pi, User grpCreator, GroupType groupType, User... members) {
    return createAndSaveGroup(pi.getLastName() + "Group", pi, grpCreator, groupType, members);
  }

  @Override
  public Group createAndSaveGroup(
      String initialGrpName, User pi, User grpCreator, GroupType groupType, User... members) {
    Map<User, RoleInGroup> toAddMap =
        Arrays.stream(members).collect(toMap(u -> u, u -> RoleInGroup.DEFAULT));
    return createAndSaveGroup(initialGrpName, pi, grpCreator, groupType, toAddMap);
  }

  @Override
  public Group createAndSaveGroup(
      boolean isSelfService,
      String initialGrpName,
      User pi,
      User grpCreator,
      GroupType groupType,
      User... members) {
    Map<User, RoleInGroup> toAddMap =
        Arrays.stream(members).collect(toMap(u -> u, u -> RoleInGroup.DEFAULT));
    return createAndSaveGroup(isSelfService, initialGrpName, pi, grpCreator, groupType, toAddMap);
  }

  static final int GROUP_SUFFIX_LENGTH = 4;

  @Override
  public Group createAndSaveGroup(
      String initialGrpName,
      User pi,
      User groupCreator,
      GroupType groupType,
      Map<User, RoleInGroup> members) {
    return createAndSaveGroup(false, initialGrpName, pi, groupCreator, groupType, members);
  }

  @Override
  public Group createAndSaveGroup(
      boolean isSelfService,
      String initialGrpName,
      User pi,
      User groupCreator,
      GroupType groupType,
      Map<User, RoleInGroup> members) {

    if (!GroupType.PROJECT_GROUP.equals(groupType)) {
      Validate.notNull(pi, "Group PI can't be null");
      Validate.isTrue(
          pi.hasRole(Role.PI_ROLE), "User [" + pi.getUsername() + "] doesn't have PI role.");
    }

    Group group = new Group();
    User owner = GroupType.PROJECT_GROUP.equals((groupType)) ? groupCreator : pi;
    group.setOwner(owner);

    if (GroupType.PROJECT_GROUP.equals(groupType)) {
      group.setGroupOwners(groupCreator.getUsername());
    } else {
      group.setPis(pi.getUsername());
    }

    group.setSelfService(isSelfService);
    group.setGroupType(groupType);

    group.setDisplayName(initialGrpName);
    group.createAndSetUniqueGroupName();
    PermissionFactory factory = new DefaultPermissionFactory();
    for (ConstraintBasedPermission cbp : factory.createDefaultGlobalGroupPermissions(group)) {
      group.addPermission(cbp);
    }

    String adminsString =
        members.entrySet().stream()
            .filter(entry -> RoleInGroup.RS_LAB_ADMIN.equals(entry.getValue()))
            .map(entry -> entry.getKey().getUsername())
            .collect(joining(","));
    group.setAdmins(adminsString);

    List<User> grpmembers = members.keySet().stream().collect(toList());
    if (!GroupType.PROJECT_GROUP.equals(groupType)) {
      grpmembers.add(pi);
    }
    List<String> unames = members.keySet().stream().map(User::getUsername).collect(toList());

    group.setMemberString(unames);
    Errors errors = new BeanPropertyBindingResult(group, "MyObject");
    ValidationUtils.invokeValidator(groupValidator, group, errors);
    // will be due to duplicate group name
    if (errors.hasErrors()) {
      String newGrpName =
          group.getUniqueName() + RandomStringUtils.randomNumeric(GROUP_SUFFIX_LENGTH);
      group.setUniqueName(newGrpName);
    }
    Group saved = grpManager.saveGroup(group, pi);

    if (GroupType.PROJECT_GROUP.equals(groupType)) {
      group =
          grpManager.addMembersToProjectGroup(
              saved.getId(), grpmembers, owner.getUsername(), groupCreator);
    } else {
      grpManager.addMembersToGroup(
          saved.getId(), grpmembers, pi.getUsername(), adminsString, groupCreator);
    }
    auditTrail.notify(new GenericEvent(groupCreator, group, AuditAction.WRITE));
    try {
      grpManager.createSharedCommunalGroupFolders(saved.getId(), groupCreator.getUsername());
    } catch (IllegalAddChildOperation e) {
      log.error(e.getMessage());
    }
    return grpManager.getGroup(group.getId());
  }

  @Override
  public Group createAndSaveGroup(Group group, User groupCreator, List<User> users) {
    group.createAndSetUniqueGroupName();

    PermissionFactory permissionFactory = new DefaultPermissionFactory();
    for (ConstraintBasedPermission cbp :
        permissionFactory.createDefaultGlobalGroupPermissions(group)) {
      group.addPermission(cbp);
    }

    String pis = group.getPis();
    String admins = group.getAdmins();
    String groupOwners = group.getGroupOwners();

    if (groupCreator.hasRole(Role.ADMIN_ROLE)) {
      List<Community> comms = communityServiceManager.listCommunitiesForAdmin(groupCreator.getId());
      if (!comms.isEmpty()) {
        group = grpManager.saveGroup(group, groupCreator);
        communityServiceManager.addGroupToCommunity(
            group.getId(), comms.get(0).getId(), groupCreator);
      } else {
        throw new AuthorizationException(
            authGenerator.getFailedMessage(
                groupCreator, "create group - please ask to be assigned to a community"));
      }
    } else {
      group = grpManager.saveGroup(group, groupCreator);
    }
    auditTrail.notify(new GenericEvent(groupCreator, group, AuditAction.CREATE));

    if (!group.isProjectGroup()) {
      group = grpManager.addMembersToGroup(group.getId(), users, pis, admins, groupCreator);
    } else {
      group = grpManager.addMembersToProjectGroup(group.getId(), users, groupOwners, groupCreator);
    }
    auditTrail.notify(new GenericEvent(groupCreator, group, AuditAction.WRITE));
    grpManager.createSharedCommunalGroupFolders(group.getId(), SYSADMIN_UNAME);
    return group;
  }
}
