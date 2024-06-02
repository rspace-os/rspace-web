package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Community;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import java.util.List;

/** DAO methods for querying communities */
public interface CommunityDao extends GenericDao<Community, Long> {

  /**
   * Paginated list of all communities.
   *
   * @param subject
   * @param pgCrit A non-null {@link PaginationCriteria}
   * @return An {@link ISearchResults} of all the communities
   * @throws IllegalArgumentException if pgCrit == null
   */
  ISearchResults<Community> listAll(User subject, PaginationCriteria<Community> pgCrit);

  /**
   * Gets list of admins for a community
   *
   * @param communityId
   * @return A possibly empty but non-null {@link List} of users.
   */
  List<User> listAdminsForCommunity(Long communityId);

  /**
   * Gets the communities administered by a particular admin. This should almost always contain a
   * single Community - an admin can only administer a single community
   *
   * @param adminId the id of a user with admin role
   * @return A possibly empty but non-null {@link List} of communities.
   */
  List<Community> listCommunitiesForAdmin(Long adminId);

  /**
   * Finds every Community to which the user belongs. This means that there is at least one lab
   * group in a community to which the user belongs.
   *
   * @param userId
   * @return list of communities
   */
  List<Community> listCommunitiesForUser(Long userId);

  /**
   * Returns a community with pre-loaded groups
   *
   * @param communityId
   * @return a Community where getGroups() will get all groups in the collection
   */
  Community getCommunityWithGroupsAndAdmins(Long communityId);

  Community getCommunityForGroup(Long groupId);

  Community getWithAdmins(Long id);

  boolean hasCommunity(User admin);
}
