package com.researchspace.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Community;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.views.ServiceOperationResult;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;

/** Service interface for operations to do with Communities */
public interface CommunityServiceManager {

  /**
   * Basic save operation
   *
   * @param community
   * @return
   */
  Community save(Community community);

  /**
   * Create new community and add the groups
   *
   * @param community valid community with optional groups listed in 'groupIds' field
   * @param subject current user
   * @return
   */
  Community saveNewCommunity(Community community, User subject);

  /**
   * Paginated listing of communities
   *
   * @param subject
   * @param pgCrit
   * @return
   */
  ISearchResults<Community> listCommunities(User subject, PaginationCriteria<Community> pgCrit);

  /**
   * Gets a community list depending of user id (Used by MyRSpaceAdmin).
   *
   * @param id
   * @return
   */
  List<Community> listCommunitiesForAdmin(Long id);

  /**
   * Finds every Community to which the user belongs. This means that there is at least one lab
   * group in a community to which the user belongs.
   *
   * @param userId
   * @return list of communities
   */
  List<Community> listCommunitiesForUser(Long userId);

  /**
   * Gets single community with pre-fetched collections of Admins and Groups
   *
   * @return A community database identifier
   */
  Community getCommunityWithAdminsAndGroups(Long communityid);

  /**
   * Adds a group to the given community. If the group already belongs to another community, it will
   * be removed from that community and added to the new community. <br>
   * In this way, a LabGRoup can only belong to one community.
   *
   * <p>If the group id is that of a collaboration group,then it will be ignored - collaboration
   * groups exist outside of the Community structure.
   *
   * @param grpId The Id of a LabGroup
   * @param communityId
   * @param subject the logged-in user
   * @return the edited community
   * @throws AuthorizationException if <code>subject</code> lacks write permission on community.
   */
  Community addGroupToCommunity(Long grpId, Long communityId, User subject);

  /**
   * Loads the community with given id, without pre-fetched Admin/Group collection
   *
   * @param id
   * @return
   */
  Community get(Long id);

  /**
   * Adds 1 or more admins to a community. If the admins to be added are:
   *
   * <ul>
   *   <li>Already associated with this or another community, or
   *   <li>Do not have a {@link Constants#ADMIN_ROLE} role
   * </ul>
   *
   * they will be ignored.
   *
   * <p>I.e., only unattached admins will be added.
   *
   * @param adminIds
   * @param communityId
   * @return the modified community
   */
  Community addAdminsToCommunity(Long[] adminIds, Long communityId);

  /**
   * Removes an admin from a community. If this is the only admin of this community, they won't be
   * removed and this method will return <code>false</code>.
   *
   * @param adminId id of the admin to be removed
   * @param communityId the id of the community.
   * @return <code>true</code> if the admin was removed, <code>false</code> otherwise
   */
  ServiceOperationResult<Community> removeAdminFromCommunity(Long adminId, Long communityId);

  /**
   * Gets a community by its id, with its collection of admin users loaded for use outside the
   * database session.
   *
   * @param id
   * @return a {@link Community}
   */
  Community getWithAdmins(Long id);

  /**
   * Completely deletes a community from the database; transferring all LabGroups to default
   * community. The default community cannot be deleted.<br>
   * All the admins for this community become available for assignment to other communities.
   *
   * @param commId the id of the community to be removed.
   * @return A ServiceOperationResult with isSucceeded() <code>true</code> if community was removed;
   *     <code>false</code> otherwise.
   */
  ServiceOperationResult<Community> removeCommunity(Long commId);

  /**
   * Boolean test for whether an RSpace admin is a community admin or not
   *
   * @param admin
   * @return
   */
  boolean hasCommunity(User admin);

  /**
   * Lists users in this community.
   *
   * @param communityId
   * @param subject
   * @param pgCrit
   * @return An {@link ISearchResults} of users in the specified community.
   */
  ISearchResults<User> listUsers(Long communityId, User subject, PaginationCriteria<User> pgCrit);

  /**
   * Returns <code>true</code> if admin user is a unique community admin in any community
   *
   * @param admin a CommunityAdmin user
   * @return
   */
  boolean isUserUniqueAdminInAnyCommunity(User admin);
}
