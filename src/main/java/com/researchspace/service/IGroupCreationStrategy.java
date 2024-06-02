package com.researchspace.service;

import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for automated group creation for batch or single registration.
 *
 * <p>All users must be created and saved on the system, and be initialised by an {@link
 * IContentInitializer}.
 */
public interface IGroupCreationStrategy {

  /**
   * As for <code>createAndSaveGroup(String initialGrpName, User pi,
   * User groupCreator, GroupType grpType, User ... members)</code>, except that this method will
   * generate a default display name for the group.
   *
   * @param pi
   * @param grpCreator
   * @param groupType
   * @param members
   * @return The newly created and persisted group
   * @see IGroupCreationStrategy#createAndSaveGroup(String, User, User, GroupType, User...)
   */
  Group createAndSaveGroup(User pi, User grpCreator, GroupType groupType, User... members);

  /**
   * Performs all operations to create and persist a group, including setup of group folders,
   * permissions, and population with initial members.<br>
   * All users should be previously have been saved and their folders initialised using an {@link
   * IContentInitializer}.
   *
   * @param initialGrpName A display name for the group.
   * @param pi The user who will be PI of the group
   * @param groupCreator The logged-in user who is creating the group. This user doesn't need to be
   *     group member, they could be an admin user, for example.
   * @param groupType The type of group
   * @param members A full list of all members to be added to the group
   * @return The newly created and persisted group
   */
  Group createAndSaveGroup(
      String initialGrpName, User pi, User groupCreator, GroupType groupType, User... members);

  Group createAndSaveGroup(
      boolean isSelfService,
      String initialGrpName,
      User pi,
      User grpCreator,
      GroupType groupType,
      User... members);

  /**
   * As for other methods, but also enables assignment of LabAdmin users
   *
   * @param initialGrpName A display name for the group.
   * @param pi The user who will be PI of the group
   * @param groupCreator The logged-in user who is creating the group. This user doesn't need to be
   *     group member, they could be an admin user, for example.
   * @param groupType The type of group
   * @param members A full list of all members to be added to the group, along with their role.
   * @return The newly created and persisted group
   * @see #Group createAndSaveGroup(String initialGrpName, User pi, User groupCreator, GroupType
   *     groupType, User ... members);
   */
  Group createAndSaveGroup(
      String initialGrpName,
      User pi,
      User groupCreator,
      GroupType groupType,
      Map<User, RoleInGroup> members);

  Group createAndSaveGroup(
      boolean isSelfService,
      String initialGrpName,
      User pi,
      User groupCreator,
      GroupType groupType,
      Map<User, RoleInGroup> members);

  Group createAndSaveGroup(Group group, User groupCreator, List<User> users);
}
