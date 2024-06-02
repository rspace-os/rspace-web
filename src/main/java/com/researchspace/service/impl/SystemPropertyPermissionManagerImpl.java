package com.researchspace.service.impl;

import com.google.common.collect.ImmutableList;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.SystemPropertyDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class SystemPropertyPermissionManagerImpl implements SystemPropertyPermissionManager {

  // The process of determining whether the integration is available for a user is
  // described in detail in RSPAC-1185.

  private @Autowired CommunityDao communityDao;
  private @Autowired SystemPropertyDao syspropdao;

  /* (non-Javadoc)
   * @see com.researchspace.service.impl.SystemPropertyPermissionManager#isPropertyAllowed(com.researchspace.model.User, java.lang.String)
   */
  @Override
  public boolean isPropertyAllowed(User user, String systemPropertyName) {
    List<Community> communities;

    if (user != null) {
      // listCommunitiesForUser returns communities that have at least one lab group
      // to which this user belongs.
      communities = communityDao.listCommunitiesForUser(user.getId());
      // Also, consider communities for which the user is an admin.
      communities.addAll(communityDao.listCommunitiesForAdmin(user.getId()));
    } else {
      communities = ImmutableList.of();
    }

    return isPropertyAllowed(communities, systemPropertyName);
  }

  /* (non-Javadoc)
   * @see com.researchspace.service.impl.SystemPropertyPermissionManager#isPropertyAllowed(com.researchspace.model.Group, java.lang.String)
   */
  @Override
  public boolean isPropertyAllowed(Group group, String systemPropertyName) {
    List<Community> communities =
        (group.getCommunity() != null)
            ? ImmutableList.of(group.getCommunity())
            : ImmutableList.of();
    return isPropertyAllowed(communities, systemPropertyName);
  }

  private boolean isPropertyAllowed(List<Community> communities, String systemPropertyName) {
    boolean propertyAllowed = false;

    // First, system property by system administrator is accessed.
    SystemPropertyValue property = findByName(systemPropertyName);

    HierarchicalPermission propertyEnumValue;
    if (property != null) {
      propertyEnumValue =
          HierarchicalPermission.valueOf(
              HierarchicalPermission.toPermissionEnumString(property.getValue()));
    } else {
      propertyEnumValue = HierarchicalPermission.DEFAULT_SYS_ADMIN_PERMISSION;
    }

    if (propertyEnumValue.equals(HierarchicalPermission.ALLOWED)) {
      propertyAllowed = true;
    } else if (propertyEnumValue.equals(HierarchicalPermission.DENIED_BY_DEFAULT)) {
      propertyAllowed = false;
    } else if (propertyEnumValue.equals(HierarchicalPermission.DENIED)) {
      return false;
    }

    // Secondly, system properties set by community administrators are accessed.
    for (Community community : communities) {
      // Default community's settings are ignored
      if (Community.DEFAULT_COMMUNITY_ID.equals(community.getId())) {
        continue;
      }

      property = findByNameAndCommunity(systemPropertyName, community.getId());

      if (property != null) {
        propertyEnumValue = HierarchicalPermission.valueOf(property.getValue());
      } else {
        // If the system property value is not set for this community, it does not
        // change anything.
        continue;
      }

      if (propertyEnumValue.equals(HierarchicalPermission.ALLOWED)) {
        propertyAllowed = true;
      } else if (propertyEnumValue.equals(HierarchicalPermission.DENIED)) {
        return false;
      } else if (propertyEnumValue.equals(HierarchicalPermission.DENIED_BY_DEFAULT)) {
        return false;
      }
    }

    return propertyAllowed;
  }

  private SystemPropertyValue findByName(String name) {
    return syspropdao.findByPropertyNameAndCommunity(name, null);
  }

  private SystemPropertyValue findByNameAndCommunity(String name, Long communityId) {
    return syspropdao.findByPropertyNameAndCommunity(name, communityId);
  }
}
