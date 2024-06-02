package com.researchspace.model.dtos;

import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.UISearchTerm;
import com.researchspace.model.GroupType;
import org.apache.commons.lang.StringUtils;

/** Used to configure restrictions on group Listings */
public class GroupSearchCriteria extends FilterCriteria {

  /** */
  private static final long serialVersionUID = 1L;

  private boolean filterByUniqueName;
  private boolean loadCommunity;
  private boolean filterByDisplayNameLike;
  private boolean filterByCommunity;
  private boolean filterByGroupType;
  private boolean onlyPublicProfiles;

  @UISearchTerm private GroupType groupType;

  @UISearchTerm private String displayName = "";

  @UISearchTerm private String uniqueName = "";

  @UISearchTerm private Long communityId;

  public GroupSearchCriteria() {
    reset();
  }

  /** Resets filter to initial state */
  public void reset() {
    filterByUniqueName = false;
    loadCommunity = false;
    filterByDisplayNameLike = false;
    filterByCommunity = false;
    filterByGroupType = false;
    onlyPublicProfiles = false;

    groupType = null;
    displayName = "";
    uniqueName = "";
    communityId = null;
  }

  /**
   * Restrict group search to groups in specified community
   *
   * @return
   */
  public Long getCommunityId() {
    return communityId;
  }

  public void setCommunityId(Long communityId) {
    this.communityId = communityId;
  }

  /**
   * Is the community of a group in a result set to be pre-loaded from DB
   *
   * @return
   */
  public boolean isLoadCommunity() {
    return loadCommunity;
  }

  public void setLoadCommunity(boolean loadCommunity) {
    this.loadCommunity = loadCommunity;
  }

  public String getUniqueName() {
    return uniqueName;
  }

  public void setUniqueName(String uniqueName) {
    if (StringUtils.isEmpty(uniqueName)) {
      return;
    }
    this.uniqueName = StringUtils.abbreviate(uniqueName, MAX_SEARCH_LENGTH);
    filterByUniqueName = true;
  }

  public boolean isFilterByUniqueName() {
    return filterByUniqueName;
  }

  public GroupType getGroupType() {
    return groupType;
  }

  public void setGroupType(GroupType groupType) {
    this.groupType = groupType;
    filterByGroupType = true;
  }

  public boolean isFilterByGroupType() {
    return filterByGroupType;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayNameLike) {
    if (StringUtils.isEmpty(displayNameLike)) {
      return;
    }
    this.displayName = StringUtils.abbreviate(displayNameLike, MAX_SEARCH_LENGTH);
    filterByDisplayNameLike = true;
  }

  public boolean isFilterByDisplayNameLike() {
    return filterByDisplayNameLike;
  }

  /**
   * Configure to filter groups by community, for an admin user.
   *
   * @param filterByCommunity
   */
  public void setFilterByCommunity(boolean filterByCommunity) {
    this.filterByCommunity = filterByCommunity;
  }

  public boolean isFilterByCommunity() {
    return filterByCommunity;
  }

  /**
   * Boolean test for whether the set community id is valid
   *
   * @return
   */
  public boolean isCommunityIdSet() {
    return getCommunityId() != null && getCommunityId() >= -1;
  }

  /** Should the results include Groups with private profiles */
  public boolean isOnlyPublicProfiles() {
    return onlyPublicProfiles;
  }

  public void setOnlyPublicProfiles(boolean onlyPublicProfiles) {
    this.onlyPublicProfiles = onlyPublicProfiles;
  }
}
