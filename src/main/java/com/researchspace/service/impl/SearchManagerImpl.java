package com.researchspace.service.impl;

import static com.axiope.search.IFullTextSearchConfig.MAX_SYSADMIN_RESULTS;
import static com.axiope.search.IFullTextSearcher.ALL_LUCENE_SEARCH_STRATEGY;
import static com.axiope.search.IFullTextSearcher.SINGLE_LUCENE_SEARCH_STRATEGY;
import static com.axiope.search.SearchConstants.ALL_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.ATTACHMENT_SEARCH_OPTION;

import com.axiope.search.IFileSearcher;
import com.axiope.search.IFullTextSearcher;
import com.axiope.search.InventorySearchConfig;
import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.InventorySearchInputValidator;
import com.axiope.search.SearchConfig;
import com.axiope.search.SearchConstants;
import com.axiope.search.SearchManager;
import com.axiope.search.SearchUtils;
import com.axiope.search.WorkspaceSearchConfig;
import com.axiope.search.WorkspaceSearchInputValidator;
import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.TextSearchDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.FolderManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.BasketApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.SampleApiManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.UrlValidator;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

/** Implement search service */
@Service("searchManager")
public class SearchManagerImpl implements SearchManager {

  private @Autowired TextSearchDao textSearchDao;
  private @Autowired IFileSearcher searcher;
  private @Autowired UserManager userMgr;
  private @Autowired RecordManager recordManager;
  private @Autowired FolderManager folderMgr;
  private @Autowired SampleApiManager sampleApiManager;
  private @Autowired SampleDao sampleDao;
  private @Autowired BasketApiManager basketApiManager;
  private @Autowired InventoryPermissionUtils invPermissionUtils;
  private @Autowired MessageSourceUtils messages;

  private ISearchResults<BaseRecord> getAttachments(WorkspaceListingConfig input, User user)
      throws IOException {
    String[] terms = input.getSrchTerms();
    // we're searching attachment files only
    return searcher.searchContents(terms[0], input.getPgCrit(), user);
  }

  @Override
  public ISearchResults<BaseRecord> searchWorkspaceRecords(WorkspaceListingConfig input, User user)
      throws IllegalArgumentException, IOException {

    if (isASimpleAttachmentSearch(input.getSrchOptions())) {
      return getAttachments(input, user);
    }

    WorkspaceSearchInputValidator validator = new WorkspaceSearchInputValidator(user);
    Errors errors = new BeanPropertyBindingResult(input, "MyObject");
    validator.validate(input, errors);
    Validate.isTrue(!errors.hasErrors(), resolveErrorMsg(errors));

    List<User> userFilter = getUserFilter(user);
    SearchConfig searchConfig = createWorkspaceSearchCfg(user, userFilter);
    updateSrchConfiguration(input, searchConfig);
    return textSearchDao.getSearchedElnResults(searchConfig);
  }

  private SearchConfig createWorkspaceSearchCfg(User user, List<User> userFilter) {
    SearchConfig searchConfig = new WorkspaceSearchConfig(user);
    if (user.hasRole(Role.SYSTEM_ROLE)) {
      configureForSysAdminSearch(searchConfig, userFilter);
    }
    List<String> usernameFilter =
        userFilter.stream().map(User::getUsername).collect(Collectors.toList());
    usernameFilter.add(Constants.SYSADMIN_UNAME); // Sysadmin1 added to see public folders
    searchConfig.setUsernameFilter(usernameFilter);
    return searchConfig;
  }

  private boolean isASimpleAttachmentSearch(String[] options) {
    // handles attachment search from both advanced and simple options
    return options.length == 1 && ATTACHMENT_SEARCH_OPTION.equalsIgnoreCase(options[0]);
  }

  private void updateSrchConfiguration(WorkspaceListingConfig input, SearchConfig searchConfig) {
    if (input.isAdvancedSearch()) {
      searchConfig.setSearchStrategy(IFullTextSearcher.ADVANCED_LUCENE_SEARCH_STRATEGY);
      searchConfig.setOptions(input.getSrchOptions());
      searchConfig.setTerms(input.getSrchTerms());
    } else if (input.getSrchOptions()[0].equalsIgnoreCase(ALL_SEARCH_OPTION)) {
      searchConfig.setSearchStrategy(ALL_LUCENE_SEARCH_STRATEGY);
    } else {
      searchConfig.setSearchStrategy(SINGLE_LUCENE_SEARCH_STRATEGY);
    }

    if (input.getPgCrit().getOrderBy() == null) {
      input.getPgCrit().setOrderBy(SearchUtils.BASE_RECORD_ORDER_BY_LAST_MODIFIED);
    }
    searchConfig.setFilters(input.getFilters());
    searchConfig.setNotebookFilter(input.isNotebookFilter());
    searchConfig.setFolderId(input.getParentFolderId());
    searchConfig.setPaginationCriteria(input.getPgCrit());
    searchConfig.setAdvancedSearch(input.isAdvancedSearch());
    searchConfig.setOperator(input.getOperator());
    searchConfig.setOptions(input.getSrchOptions());
    searchConfig.setTerms(input.getSrchTerms());
  }

  /**
   * Get list of users whose files are viewable by the subject user.
   *
   * @param subject user performing the search
   */
  private List<User> getUserFilter(User subject) {
    // gets user lists for all except sysadmin who can see everybody anyway
    if (subject.hasSysadminRole()) {
      return Collections.emptyList();
    }
    if (subject.hasRole(Role.ADMIN_ROLE)) {
      List<User> communityMembers = userMgr.getAllUsersInAdminsCommunity(subject.getUsername());
      // make sure admin is included himself
      communityMembers.add(subject);
      return communityMembers;
    }
    return userMgr.getViewableUserList(subject);
  }

  /**
   * Get list of usernames whose inventory items are viewable by the subject user.
   *
   * @param subject user performing the search
   */
  private List<String> getUserFilterForInventory(User subject) {
    if (subject.hasAdminRole()) {
      return getUserFilter(subject).stream().map(User::getUsername).collect(Collectors.toList());
    }
    return invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(subject);
  }

  private List<BaseRecord> getFilteredRecords(WorkspaceFilters filters, User user) {
    return recordManager.getFilteredRecordsList(filters, user);
  }

  @Override
  public ISearchResults<BaseRecord> searchUserRecordsWithSimpleQuery(
      User user, String searchQuery, Integer maxResults) throws IOException {

    // Prepare WorkspaceListingConfig needed to search from query
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setPageNumber(0L);
    if (maxResults != null && maxResults > 0) {
      pgCrit.setResultsPerPage(5);
    }

    Long folderId = folderMgr.getRootFolderForUser(user).getId();

    WorkspaceListingConfig config =
        new WorkspaceListingConfig(
            pgCrit,
            new String[] {SearchConstants.ALL_SEARCH_OPTION},
            new String[] {searchQuery},
            folderId,
            false);

    // Validate the created WorkspaceListingConfig
    WorkspaceSearchInputValidator validator = new WorkspaceSearchInputValidator(user);
    Errors errors = new BeanPropertyBindingResult(config, "MyObject");
    validator.validate(config, errors);
    if (errors.hasErrors()) {
      throwIAEWithResolvedErrors(errors);
    }

    // Do the actual search
    return searchWorkspaceRecords(config, user);
  }

  @Override
  public ApiInventorySearchResult searchInventoryWithSimpleQuery(
      String searchQuery,
      InventorySearchType resultType,
      GlobalIdentifier parentOid,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    InventorySearchConfig searchConfig = createInventorySearchCfg(user, resultType, searchQuery);
    searchConfig.setSearchStrategy(ALL_LUCENE_SEARCH_STRATEGY);
    if (pgCrit != null) {
      searchConfig.setPaginationCriteria(pgCrit);
    }

    // if requesting particular owner, limit the username filter
    if (StringUtils.isNotEmpty(ownedBy)) {
      searchConfig.setUsernameFilter(Collections.singletonList(ownedBy));
    }
    if (deletedOption != null) {
      searchConfig.setDeletedOption(deletedOption);
    }

    List<String> options = new ArrayList<>();
    List<String> terms = new ArrayList<>();
    options.add(SearchConstants.INVENTORY_SEARCH_OPTION);
    terms.add(adjustUserSearchQueryForBestResults(searchQuery));
    if (parentOid != null) {
      if (GlobalIdPrefix.BA.equals(parentOid.getPrefix())) {
        List<String> basketContentIds =
            basketApiManager.getBasketById(parentOid.getDbId(), user).getContentGlobalIds();
        searchConfig.setLimitResultsToGlobalIds(basketContentIds);
      } else {
        searchConfig.setParentOid(parentOid);
        if (GlobalIdPrefix.IC.equals(parentOid.getPrefix())
            || GlobalIdPrefix.BE.equals(parentOid.getPrefix())) {
          options.add(SearchConstants.INVENTORY_PARENT_ID_OPTION);
        } else if (GlobalIdPrefix.IT.equals(parentOid.getPrefix())) {
          options.add(SearchConstants.INVENTORY_PARENT_TEMPLATE_ID_OPTION);
        } else if (GlobalIdPrefix.SA.equals(parentOid.getPrefix())) {
          options.add(SearchConstants.INVENTORY_PARENT_SAMPLE_ID_OPTION);
        } else {
          throw new IllegalArgumentException(
              "unsupported parent oid prefix: " + parentOid.getPrefix());
        }
        terms.add(parentOid.getDbId().toString());
      }
    }
    searchConfig.setOptions(options.toArray(new String[0]));
    searchConfig.setTerms(terms.toArray(new String[0]));

    // validate the provided query - use WorkspaceSearchInputValidator as the rules seem relevant
    // enough
    InventorySearchInputValidator validator = new InventorySearchInputValidator(user);
    Errors errors = new BeanPropertyBindingResult(searchConfig, "MyObject");
    validator.validate(searchConfig, errors);
    if (errors.hasErrors()) {
      throwIAEWithResolvedErrors(errors);
    }

    ISearchResults<? extends InventoryRecord> foundResults =
        textSearchDao.getSearchedInventoryResults(searchConfig);

    return sampleApiManager.convertToApiInventorySearchResult(
        foundResults.getTotalHits(), foundResults.getPageNumber(), foundResults.getResults(), user);
  }

  private String adjustUserSearchQueryForBestResults(String searchQuery) {
    if (StringUtils.isBlank(searchQuery) || StringUtils.containsWhitespace(searchQuery)) {
      return searchQuery;
    }
    if (searchQuery.contains("\"")) {
      return searchQuery; // user tries to get specific result by quoting, let's not interfere
    }

    UrlValidator urlValidator = new UrlValidator();
    if (urlValidator.isValid(searchQuery)) {
      return "\"" + searchQuery + "\""; // surround with quotes for exact url match (PRT-566)
    }
    return searchQuery;
  }

  private InventorySearchConfig createInventorySearchCfg(
      User user, InventorySearchType searchType, String searchQuery) {

    List<String> ownersFilter = getUserFilterForInventory(user);
    InventorySearchConfig searchConfig = new InventorySearchConfig(user);
    searchConfig.setOriginalSearchQuery(searchQuery);
    if (user.hasRole(Role.SYSTEM_ROLE)) {
      configureForSysAdminSearch(searchConfig, ownersFilter);
    }
    /*
     * if restricted search set username filter and add default templates owner
     * so default templates are included in search.
     */
    if (searchConfig.isRestrictByUser()) {
      String defaultTemplatesOwner = sampleDao.getDefaultTemplatesOwner();
      if (defaultTemplatesOwner != null
          && !invPermissionUtils.isInventoryOwnerReadableByUser(defaultTemplatesOwner, user)) {
        // let's also search templates of default templates owner
        ownersFilter.add(defaultTemplatesOwner);
        searchConfig.setDefaultTemplatesOwner(defaultTemplatesOwner);
      }
      searchConfig.setUsernameFilter(ownersFilter);

      List<String> userGroupUniqueNames =
          user.getGroups().stream().map(Group::getUniqueName).collect(Collectors.toList());
      searchConfig.setSharedWithFilter(userGroupUniqueNames);
    }
    if (searchType != null) {
      searchConfig.setSearchType(searchType);
    }
    return searchConfig;
  }

  private void configureForSysAdminSearch(SearchConfig searchConfig, List<?> userFilter) {
    if (userFilter.isEmpty()) {
      // that's case of sysadmin searching for everyone's content
      searchConfig.setRestrictByUser(false);
    }
    searchConfig.setMaxResults(MAX_SYSADMIN_RESULTS);
  }

  private void throwIAEWithResolvedErrors(Errors errors) {
    throw new IllegalArgumentException(resolveErrorMsg(errors));
  }

  private String resolveErrorMsg(Errors errors) {
    if (!errors.hasErrors()) {
      return "";
    }
    ObjectError error = errors.getAllErrors().get(0);
    return messages.getMessage(error.getCode(), error.getArguments());
  }
}
