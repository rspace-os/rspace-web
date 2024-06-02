package com.researchspace.service.impl;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.SystemPropertyDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.CommunityServiceManager;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@CacheConfig(cacheNames = "com.researchspace.model.Community")
@Service("communityService")
public class CommunityServiceManagerImpl implements CommunityServiceManager {

  Logger log = LoggerFactory.getLogger(CommunityServiceManagerImpl.class);

  @Autowired IPermissionUtils permUtils;

  private @Autowired UserDao userDao;
  private @Autowired PermissionFactory permFac;
  private @Autowired GroupDao groupDao;
  private @Autowired CommunityDao communityDao;
  private @Autowired SystemPropertyDao sysPropertDao;

  @Override
  public Community save(Community community) {
    return communityDao.save(community);
  }

  @Override
  public Community saveNewCommunity(Community community, User subject) {
    Validate.notEmpty(community.getAdmins(), "Community's admin list can't be empty");
    communityDao.save(community);
    if (community.getGroupIds() != null) {
      for (Long id : community.getGroupIds()) {
        addGroupToCommunity(id, community.getId(), subject);
      }
    }
    return community;
  }

  @Override
  public ISearchResults<Community> listCommunities(
      User subject, PaginationCriteria<Community> pgCrit) {
    return communityDao.listAll(subject, pgCrit);
  }

  @Override
  public List<Community> listCommunitiesForAdmin(Long id) {
    return communityDao.listCommunitiesForAdmin(id);
  }

  @Override
  @Cacheable(key = "#id")
  public List<Community> listCommunitiesForUser(Long id) {
    return communityDao.listCommunitiesForUser(id);
  }

  @Override
  public Community getCommunityWithAdminsAndGroups(Long communityId) {
    return communityDao.getCommunityWithGroupsAndAdmins(communityId);
  }

  @Override
  public Community addGroupToCommunity(Long grpId, Long communityId, User subject) {
    Community community = communityDao.get(communityId);
    if (!permUtils.isPermitted(community, PermissionType.WRITE, subject)) {
      throw new AuthorizationException("Unauthorized attempt to add group to community");
    }
    Group grp = groupDao.get(grpId);
    if (!grp.isLabGroup()) {
      log.warn("Could not add group [{}] as is not a LabGroup", grp.getId());
      return community;
    }
    Community existingCommunitty = grp.getCommunity();
    if (existingCommunitty != null) {
      removeGroupFromCommunity(grp, existingCommunitty);
    }
    community.addLabGroup(grp);
    grp.setCommunityId(communityId);
    groupDao.save(grp);
    communityDao.save(community);
    return community;
  }

  public void removeGroupFromCommunity(Group grp, Community existingCommunitty) {
    existingCommunitty.removeLabGroup(grp);
    communityDao.save(existingCommunitty);
  }

  @Override
  public Community get(Long id) {
    return communityDao.get(id);
  }

  @Override
  public Community getWithAdmins(Long id) {
    return communityDao.getWithAdmins(id);
  }

  @Override
  public Community addAdminsToCommunity(Long[] adminIds, Long communityId) {
    Community comm = getWithAdmins(communityId);
    for (Long adminId : adminIds) {
      User admin = userDao.get(adminId);
      if (!admin.hasRole(Role.ADMIN_ROLE)) {
        continue;
      }
      if (!communityDao.listCommunitiesForAdmin(adminId).isEmpty()) {
        continue;
      }
      comm.addAdmin(admin);
      admin.addPermissions(permFac.createCommunityPermissionsForAdmin(admin, comm));
      permUtils.notifyUserOrGroupToRefreshCache(admin);
      userDao.save(admin);
      communityDao.save(comm);
    }
    return comm;
  }

  @Override
  public ServiceOperationResult<Community> removeAdminFromCommunity(
      Long adminId, Long communityId) {
    Community comm = getWithAdmins(communityId);
    User admin = userDao.get(adminId);
    boolean removed = comm.removeAdmin(admin);
    if (removed) {
      Set<ConstraintBasedPermission> cbps = permFac.createCommunityPermissionsForAdmin(admin, comm);
      admin.removePermissions(cbps);
      userDao.save(admin);
      permUtils.notifyUserOrGroupToRefreshCache(admin);
      communityDao.save(comm);
    }
    return new ServiceOperationResult<Community>(comm, removed);
  }

  @Override
  public ServiceOperationResult<Community> removeCommunity(Long commId) {
    Community comm = get(commId);
    if (Community.DEFAULT_COMMUNITY_ID.equals(commId)) {
      return new ServiceOperationResult<Community>(comm, false);
    }
    sysPropertDao.deleteSystemPropertyValueByCommunityId(commId);
    communityDao.remove(commId);
    return new ServiceOperationResult<Community>(comm, true);
  }

  @Override
  public boolean hasCommunity(User admin) {
    return communityDao.hasCommunity(admin);
  }

  @Override
  public ISearchResults<User> listUsers(
      Long communityId, User subject, PaginationCriteria<User> pgCrit) {
    return userDao.listUsersInCommunity(communityId, pgCrit);
  }

  @Override
  public boolean isUserUniqueAdminInAnyCommunity(User admin) {
    if (hasCommunity(admin)) {
      List<Community> commsCommunities = communityDao.listCommunitiesForAdmin(admin.getId());
      return commsCommunities.stream().anyMatch(community -> community.getAdmins().size() == 1);
    }
    return false;
  }
}
