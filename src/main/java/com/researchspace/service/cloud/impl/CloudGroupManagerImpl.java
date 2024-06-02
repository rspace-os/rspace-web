package com.researchspace.service.cloud.impl;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.cloud.CloudGroupManager;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("cloudGroupManager")
public class CloudGroupManagerImpl implements CloudGroupManager {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired UserManager userManager;
  private @Autowired RoleManager roleManager;
  private @Autowired GroupManager groupManager;
  private @Autowired IContentInitializer initializer;
  private @Autowired IGroupCreationStrategy grpStrategy;
  private @Autowired IPermissionUtils permUtils;

  @Override
  public User promoteUserToPI(User toPromote, User subject) {

    if (!toPromote.isContentInitialized()) {
      initializer.init(toPromote.getId());
    }

    for (Group grp : toPromote.getGroups()) {
      if (grp.isCollaborationGroup()) {
        // Don't remove from collabGroup
        groupManager.setRoleForUser(grp.getId(), toPromote.getId(), RoleInGroup.PI.name(), subject);
      }
    }

    /** In cloud environment, we just promote an user to PI by just updating the ROLE. */
    toPromote = userManager.get(toPromote.getId());
    toPromote.addRole(roleManager.getRole(Constants.PI_ROLE));
    toPromote = userManager.save(toPromote);
    permUtils.notifyUserOrGroupToRefreshCache(toPromote);
    permUtils.refreshCacheIfNotified();
    return toPromote;
  }

  @Override
  public Group createAndSaveGroup(
      String initialGrpName, User pi, User grpCreator, GroupType groupType, User... members) {
    return createAndSaveGroup(false, initialGrpName, pi, grpCreator, groupType, members);
  }

  @Override
  public Group createAndSaveGroup(
      boolean isSelfService,
      String initialGrpName,
      User pi,
      User grpCreator,
      GroupType groupType,
      User... members) {
    initialGrpName = StringUtils.replaceChars(initialGrpName, "<>", "__");
    // Creating new LabGroup for new PI
    return grpStrategy.createAndSaveGroup(
        isSelfService, initialGrpName, pi, grpCreator, groupType, members);
  }

  @Override
  public Group createAndSaveProjectGroup(String initialGrpName, User grpCreator, User... members) {
    initialGrpName = StringUtils.replaceChars(initialGrpName, "<>", "__");
    // Creating new LabGroup for new PI
    return grpStrategy.createAndSaveGroup(
        false, initialGrpName, null, grpCreator, GroupType.PROJECT_GROUP, members);
  }

  @Override
  public Group addAdminToGroup(User admin, Group group) {
    return groupManager.addUserToGroup(
        admin.getUsername(), group.getId(), RoleInGroup.RS_LAB_ADMIN);
  }

  @Override
  public Group getCloudGroup(Long id) {
    log.info("Getting cloud group" + id);
    return groupManager.getGroup(id);
  }

  @Override
  public List<Group> searchGroups(String term) {
    log.info("Searching cloud group");
    final int minLength = 3;
    if (StringUtils.isBlank(term) || term.trim().length() < minLength) {
      throw new IllegalArgumentException(
          "Search term [" + term + "] must be at least 3 characters");
    }
    return groupManager.searchGroups(term);
  }
}
