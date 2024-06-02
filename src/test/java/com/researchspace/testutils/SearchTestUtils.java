package com.researchspace.testutils;

import com.axiope.search.SearchConstants;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.dtos.WorkspaceSettings;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;

/** Convenience class to create {@link WorkspaceListingConfig} for common test search scenarios */
public class SearchTestUtils {

  /**
   * Search by 'ALL' option
   *
   * @param term
   * @return
   */
  public static WorkspaceListingConfig createSimpleGeneralSearchCfg(String term) {
    return createSearchCfgByType(SearchConstants.ALL_SEARCH_OPTION, term);
  }

  public static WorkspaceListingConfig createSimpleOwnerSearchCfg(String term) {
    return createSearchCfgByType(SearchConstants.OWNER_SEARCH_OPTION, term);
  }

  /**
   * Term must be valid syntax, i.e a range or with ; as prefix or suffix for from/to searches
   *
   * @param term
   * @return
   */
  public static WorkspaceListingConfig createSimpleCreationDateSearchCfg(String term) {
    return createSearchCfgByType(SearchConstants.CREATION_DATE_SEARCH_OPTION, term);
  }

  /**
   * Term must be valid syntax, i.e a range or with ; as prefix or suffix for from/to searches
   *
   * @param term
   * @return
   */
  public static WorkspaceListingConfig createSimpleModificationDateSearchCfg(String term) {
    return createSearchCfgByType(SearchConstants.MODIFICATION_DATE_SEARCH_OPTION, term);
  }

  public static WorkspaceListingConfig createSimpleFullTextSearchCfg(String term) {
    return createSearchCfgByType(SearchConstants.FULL_TEXT_SEARCH_OPTION, term);
  }

  public static WorkspaceListingConfig createSimpleTagSearchCfg(String term) {
    return createSearchCfgByType(SearchConstants.TAG_SEARCH_OPTION, term);
  }

  public static WorkspaceListingConfig createSimpleFormSearchCfg(String term) {
    return createSearchCfgByType(SearchConstants.FORM_SEARCH_OPTION, term);
  }

  public static WorkspaceListingConfig createByNameAndOwner(String term, User owner) {
    PaginationCriteria<BaseRecord> pg = getPaginationCriteria();
    WorkspaceListingConfig cfg =
        new WorkspaceListingConfig(
            pg,
            new String[] {SearchConstants.NAME_SEARCH_OPTION, SearchConstants.OWNER_SEARCH_OPTION},
            new String[] {term, owner.getUsername()},
            -1L,
            true);
    return cfg;
  }

  public static WorkspaceListingConfig createSimpleNameSearchCfg(String term) {
    return createSearchCfgByType(SearchConstants.NAME_SEARCH_OPTION, term);
  }

  public static WorkspaceListingConfig createSearchByTemplateCfg(String term) {
    return createSearchCfgByType(SearchConstants.FROM_TEMPLATE_SEARCH_OPTION, term);
  }

  public static WorkspaceListingConfig createSearchCfgByType(String option, String srchTerm) {
    PaginationCriteria<BaseRecord> pg = getPaginationCriteria();
    WorkspaceListingConfig cfg =
        new WorkspaceListingConfig(pg, new String[] {option}, new String[] {srchTerm}, -1L, false);
    return cfg;
  }

  /**
   * The search operator by default is AND
   *
   * @param options
   * @param srchTerms
   * @return
   */
  public static WorkspaceListingConfig createAdvSearchCfg(String[] options, String[] srchTerms) {
    PaginationCriteria<BaseRecord> pg = getPaginationCriteria();
    WorkspaceListingConfig cfg = new WorkspaceListingConfig(pg, options, srchTerms, -1L, true);
    return cfg;
  }

  /**
   * Using this method we can set a SearchOpearator operator = AND / OR.
   *
   * @param options
   * @param srchTerms
   * @param operator
   * @return
   */
  public static WorkspaceListingConfig createAdvSearchCfg(
      String[] options, String[] srchTerms, SearchOperator operator) {
    PaginationCriteria<BaseRecord> pg = getPaginationCriteria();
    WorkspaceListingConfig cfg = new WorkspaceListingConfig(pg, options, srchTerms, -1L, true);
    cfg.setOperator(operator);
    return cfg;
  }

  public static WorkspaceListingConfig createAdvSearchCfgWithFilters(
      String[] options, String[] srchTerms, SearchOperator operator, WorkspaceFilters filters) {
    WorkspaceSettings settings = new WorkspaceSettings();
    settings.setParentFolderId(-1L);
    settings.setAdvancedSearch(true);
    settings.setOperator(operator);
    settings.setFavoritesFilter(filters.isFavoritesFilter());
    settings.setSharedFilter(filters.isSharedFilter());
    settings.setViewableItemsFilter(filters.isViewableItemsFilter());
    settings.setTemplatesFilter(filters.isTemplatesFilter());
    settings.setResultsPerPage(
        Integer.parseInt(Preference.WORKSPACE_RESULTS_PER_PAGE.getDefaultValue()));

    WorkspaceListingConfig cfg = new WorkspaceListingConfig(settings, options, srchTerms);
    return cfg;
  }

  private static PaginationCriteria<BaseRecord> getPaginationCriteria() {
    return PaginationCriteria.createDefaultForClass(BaseRecord.class);
  }
}
