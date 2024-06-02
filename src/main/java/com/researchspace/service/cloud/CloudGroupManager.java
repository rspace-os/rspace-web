package com.researchspace.service.cloud;

import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.User;
import java.util.List;

public interface CloudGroupManager {

  /**
   * Promote a user to PI.
   *
   * @param user
   * @return the promoted User
   */
  User promoteUserToPI(User user, User subject);

  /**
   * Create a new lab group.
   *
   * @param initialGrpName
   * @param pi
   * @param grpCreator
   * @param groupType
   * @param members
   * @return
   */
  Group createAndSaveGroup(
      String initialGrpName, User pi, User grpCreator, GroupType groupType, User... members);

  Group createAndSaveGroup(
      boolean isSelfService,
      String initialGrpName,
      User pi,
      User grpCreator,
      GroupType groupType,
      User... members);

  Group createAndSaveProjectGroup(String initialGrpName, User grpCreator, User... members);

  /**
   * Add user to the group as a new Lab administrator.
   *
   * @param admin
   * @param group
   * @return
   */
  Group addAdminToGroup(User admin, Group group);

  /**
   * @param id
   * @return
   */
  Group getCloudGroup(Long id);

  /**
   * @param term
   * @return
   */
  List<Group> searchGroups(String term);
}
