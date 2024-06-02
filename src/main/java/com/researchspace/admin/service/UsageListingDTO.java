package com.researchspace.admin.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.views.UserStatistics;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsageListingDTO {

  @Data
  @NoArgsConstructor
  public static class UserStatisticsDTO {

    private int totalUsers;
    private int totalEnabledRSpaceAdmins;
    private int totalEnabledSysAdmins;
    private int usedLicenseSeats;
    private String availableSeats;

    public UserStatisticsDTO(UserStatistics userStats) {
      totalUsers = userStats.getTotalUsers();
      totalEnabledRSpaceAdmins = userStats.getTotalEnabledRSpaceAdmins();
      totalEnabledSysAdmins = userStats.getTotalEnabledSysAdmins();
      usedLicenseSeats = userStats.getUsedLicenseSeats();
    }
  }

  @Data
  @NoArgsConstructor
  public static class PaginationCriteriaDTO {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SearchCriteriaDTO {

      private String allFields;
    }

    private Integer resultsPerPage;
    private String orderBy;
    private String sortOrder;
    private PaginationCriteriaDTO.SearchCriteriaDTO searchCriteria;

    public PaginationCriteriaDTO(PaginationCriteria<User> paginationCriteria) {
      resultsPerPage = paginationCriteria.getResultsPerPage();
      orderBy = paginationCriteria.getOrderBy();
      sortOrder = paginationCriteria.getSortOrder().toString();
      UserSearchCriteria userSearchCriteria =
          ((UserSearchCriteria) paginationCriteria.getSearchCriteria());
      searchCriteria =
          new PaginationCriteriaDTO.SearchCriteriaDTO(
              userSearchCriteria == null ? null : userSearchCriteria.getAllFields());
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class UserInfoListDTO {

    private Long totalHits;
    private Integer pageNumber;
    private Integer hitsPerPage;
    private List<UserUsageInfo> results;

    public UserInfoListDTO(ISearchResults<UserUsageInfo> searchResults) {
      totalHits = searchResults.getTotalHits();
      pageNumber = searchResults.getPageNumber();
      hitsPerPage = searchResults.getHitsPerPage();
      results = searchResults.getResults();
    }
  }

  UserStatisticsDTO userStats;
  PaginationCriteriaDTO pgCrit;
  UserInfoListDTO userInfo;
  List<PaginationObject> pagination;
}
