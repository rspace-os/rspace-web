package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.views.UserView;
import java.util.Collection;
import java.util.List;

public interface GroupDao extends GenericDao<Group, Long> {

  ISearchResults<Group> list(PaginationCriteria<Group> pagCrit);

  /**
   * Gets potential members of a collaboration group, which are users who belong to LabGroups where
   * the PI is already a member of the collaboration group. This method does not include existing
   * collaboration group members.
   *
   * @param id A CollaborationGroup id
   * @return
   */
  List<UserView> getCandidateMembersOfCollabGroup(Long id);

  /**
   * Gets group by ID with preloaded community
   *
   * @param groupId
   * @return
   */
  Group getGroupWithCommunities(Long groupId);

  /**
   * Gets group by unique name, or <code>null</code> if not found.
   *
   * @param uniqueName
   * @return
   */
  Group getByUniqueName(String uniqueName);

  Group getByCommunalGroupFolderId(Long folderId);

  List<Group> getForOwner(User owner);

  /**
   * @param term
   * @return
   */
  List<Group> searchGroups(String term);

  /**
   * Gets groups, possibly not in the same order as specified IDs
   *
   * @param groupIds
   * @return a
   */
  List<Group> getGroups(Collection<Long> groupIds);

  /**
   * Gets a group and locks its row until the transaction ends, so concurrent membership changes to
   * the same group run one after another.
   *
   * @param id a group id
   * @return the group
   * @throws org.springframework.orm.ObjectRetrievalFailureException if not found
   */
  Group getForUpdate(Long id);

  /**
   * Whether a membership row exists for this group and user. Uses a locking read, so it sees rows
   * committed by a transaction this one waited for.
   *
   * @param groupId a group id
   * @param userId a user id
   * @return <code>true</code> if the user has a membership row in the group
   */
  boolean hasMembership(Long groupId, Long userId);
}
