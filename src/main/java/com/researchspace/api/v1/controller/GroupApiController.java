package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.GroupApi;
import com.researchspace.api.v1.model.ApiGroupInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.service.GroupManager;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/** Gets users groups */
@ApiController
public class GroupApiController extends BaseApiController implements GroupApi {

  protected @Autowired GroupManager groupManager;

  /** Gets groups for API client ordered by display name */
  @Override
  public List<ApiGroupInfo> listCurrentUserGroups(@RequestAttribute(name = "user") User user) {
    return user.getGroups().stream()
        .map(ApiGroupInfo::new)
        .sorted(this::orderByName)
        .collect(Collectors.toList());
  }

  @Override
  public List<ApiGroupInfo> searchGroups(
      @RequestParam(value = "query", required = false) String query,
      @RequestAttribute(name = "user") User user) {

    GroupSearchCriteria searchCriteria = new GroupSearchCriteria();
    searchCriteria.setGroupType(GroupType.LAB_GROUP);
    searchCriteria.setLoadCommunity(true);
    searchCriteria.setOnlyPublicProfiles(true);
    if (query != null) {
      searchCriteria.setDisplayName(query);
    }
    PaginationCriteria<Group> pgCrit = new PaginationCriteria<>();
    pgCrit.setSearchCriteria(searchCriteria);
    pgCrit.setGetAllResults();
    ISearchResults<Group> groupSearchResult = groupManager.list(user, pgCrit);

    return groupSearchResult.getResults().stream()
        .map(ApiGroupInfo::new)
        .collect(Collectors.toList());
  }

  /**
   * Groups ordered by display name
   *
   * @param i1
   * @param i2
   * @return
   */
  int orderByName(ApiGroupInfo i1, ApiGroupInfo i2) {
    return i1.getName().compareTo(i2.getName());
  }
}
