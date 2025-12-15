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
}
