package com.researchspace.webapp.controller;

import static com.researchspace.model.preference.Preference.DIRECTORY_RESULTS_PER_PAGE;

import com.researchspace.core.util.*;
import com.researchspace.model.*;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.CommunitySearchCriteria;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.views.PublicUserList;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserProfileManager;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller("directoryController")
@RequestMapping("/directory")
public class DirectoryController extends BaseController {

  private static final int MINIMUM_SEARCHTERMLENGTH = 3;

  private CommunityServiceManager communityServiceManager;
  private @Autowired UserProfileManager userProfileManager;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @Autowired
  public void setCommunityServiceManager(CommunityServiceManager communityServiceManager) {
    this.communityServiceManager = communityServiceManager;
  }

  @GetMapping("/ajax/subject")
  public @ResponseBody UserBasicInfo currentSubject() {
    User subject = userManager.getAuthenticatedUserInSession();
    return subject.toBasicInfo();
  }

  @GetMapping
  public ModelAndView directory(Principal principal, Model model, PaginationCriteria<User> pgCrit) {
    User subject = userManager.getAuthenticatedUserInSession();
    if (invalidSearchTerms(null)) {
      returnEmptyResults(principal, model, pgCrit);
    } else {
      doPublicUserListing(principal, model, pgCrit, subject);
    }
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(subject, "public_sharing"));
    model.addAttribute("pageReload", true);
    return new ModelAndView("admin/directory/directory");
  }

  private void returnEmptyResults(
      Principal principal, Model model, PaginationCriteria<User> pgCrit) {
    PaginationCriteria<PublicUserList> pgCrit2 =
        PaginationCriteria.createDefaultForClass(PublicUserList.class);
    ISearchResults<PublicUserList> res = SearchResultsImpl.emptyResult(pgCrit2);
    paginateAndSetModelAttributesForUserSearch(principal, model, pgCrit, res);
  }

  /**
   * Method to retrieve the user list.
   *
   * @param principal
   * @param model
   * @param pgCrit
   * @return
   */
  /* leaving @RequestMapping, as switching to @GetMapping cause MVC test failure */
  @RequestMapping(method = RequestMethod.GET, value = "/ajax/userlist")
  public ModelAndView ajaxUserList(
      Principal principal,
      Model model,
      PaginationCriteria<User> pgCrit,
      UserSearchCriteria searchCriteria,
      @RequestParam(value = "pageReload", required = false, defaultValue = "false")
          Boolean pageReload) {
    User subject = userManager.getAuthenticatedUserInSession();
    cleanSearchTerm(searchCriteria);
    if (invalidSearchTerms(searchCriteria)) {
      returnEmptyResults(principal, model, pgCrit);
      if (pageReload) {
        model.addAttribute("pageReload", true);
      }
    } else {
      pgCrit.setSearchCriteria(searchCriteria);
      doPublicUserListing(principal, model, pgCrit, subject);
    }

    return new ModelAndView("admin/directory/user_list_ajax");
  }

  // removes wild card characters
  protected void cleanSearchTerm(UserSearchCriteria searchCriteria) {
    String term = searchCriteria.getAllFields();
    term = SecureStringUtils.removeWildCards(term);
    searchCriteria.setAllFields(term);
  }

  private boolean invalidSearchTerms(UserSearchCriteria searchCriteria) {
    return properties.isCloud()
        && (searchCriteria == null
            || StringUtils.isBlank(searchCriteria.getAllFields())
            || searchCriteria.getAllFields().length() < MINIMUM_SEARCHTERMLENGTH);
  }

  private void doPublicUserListing(
      Principal principal, Model model, PaginationCriteria<User> pgCrit, User subject) {
    configureUserListingPagination(pgCrit, subject);
    ISearchResults<PublicUserList> users = userProfileManager.getPublicUserListing(pgCrit);
    paginateAndSetModelAttributesForUserSearch(principal, model, pgCrit, users);
  }

  private void paginateAndSetModelAttributesForUserSearch(
      Principal principal,
      Model model,
      PaginationCriteria<User> pgCrit,
      ISearchResults<PublicUserList> users) {

    User subject = userManager.getUserByUsername(principal.getName());
    userManager.populateConnectedGroupList(subject);

    DefaultURLPaginator urlGn = new DefaultURLPaginator("/directory/ajax/userlist", pgCrit);
    List<PaginationObject> pagination =
        PaginationUtil.generatePagination(users.getTotalPages(), users.getPageNumber(), urlGn);
    model.addAttribute("users", users);
    model.addAttribute("subject", subject);
    model.addAttribute(PaginationUtil.PAGINATION_LIST_MODEL_ATTR_NAME, pagination);
    Map<String, PaginationObject> sortableHeaders =
        PaginationUtil.generateOrderByLinks(
            null, urlGn, "lastName", "username", "email", "affiliation");
    model.addAllAttributes(sortableHeaders);
    model.addAttribute("numberRecords", users.getHitsPerPage());
  }

  private void configureUserListingPagination(PaginationCriteria<User> pgCrit, User subject) {
    pgCrit.setClazz(User.class);
    // set defaults if need be
    if (pgCrit.setOrderByIfNull("lastName")) {
      pgCrit.setSortOrder(SortOrder.ASC);
    }
    updateResultsPerPageProperty(subject, pgCrit, Preference.DIRECTORY_RESULTS_PER_PAGE);
  }

  /**
   * Method to retrieve the group list.
   *
   * @param model
   * @param pgCrit
   * @return
   */
  @GetMapping("/ajax/grouplist")
  public ModelAndView ajaxListGroup(
      Model model, PaginationCriteria<Group> pgCrit, GroupSearchCriteria searchCriteria) {
    pgCrit.setSearchCriteria(searchCriteria);
    doGroupList(model, pgCrit);
    return new ModelAndView("admin/directory/labgroup_list_ajax");
  }

  private void doGroupList(Model model, PaginationCriteria<Group> pgCrit) {
    configureGroupPagination(pgCrit);
    GroupSearchCriteria searchCriteria = (GroupSearchCriteria) pgCrit.getSearchCriteria();
    searchCriteria.setGroupType(GroupType.LAB_GROUP);
    searchCriteria.setLoadCommunity(true);
    searchCriteria.setFilterByCommunity(false);
    searchCriteria.setOnlyPublicProfiles(true);
    pgCrit.setSearchCriteria(searchCriteria);

    User subject = userManager.getAuthenticatedUserInSession();
    updateResultsPerPageProperty(subject, pgCrit, DIRECTORY_RESULTS_PER_PAGE);
    userManager.populateConnectedUserList(subject);

    ISearchResults<Group> groups = groupManager.list(subject, pgCrit);
    List<PaginationObject> pagination =
        PaginationUtil.generatePagination(
            groups.getTotalPages(),
            groups.getPageNumber(),
            new DefaultURLPaginator("/directory/ajax/grouplist", pgCrit));
    model.addAttribute("groups", groups);
    model.addAttribute("subject", subject);
    model.addAttribute(PaginationUtil.PAGINATION_LIST_MODEL_ATTR_NAME, pagination);
    model.addAttribute("pgCrit", pgCrit);
    model.addAttribute("numberRecords", groups.getHitsPerPage());
  }

  void configureGroupPagination(PaginationCriteria<Group> pgCrit) {
    pgCrit.setClazz(Group.class);
    // set defaults if need be
    if (pgCrit.setOrderByIfNull("displayName")) {
      pgCrit.setSortOrder(SortOrder.ASC);
    }
  }

  /**
   * Method to retrieve the community list.
   *
   * @param principal
   * @param model
   * @param pgCrit
   * @return
   */
  @GetMapping("/ajax/communitylist")
  public ModelAndView ajaxCommunityList(
      Principal principal,
      Model model,
      PaginationCriteria<Community> pgCrit,
      CommunitySearchCriteria searchCriteria) {
    pgCrit.setSearchCriteria(searchCriteria);
    doCommunityList(principal, model, pgCrit);
    return new ModelAndView("admin/directory/community_list_ajax");
  }

  private void doCommunityList(
      Principal principal, Model model, PaginationCriteria<Community> pgCrit) {

    configureCommunityPagination(pgCrit);
    User subject = userManager.getUserByUsername(principal.getName());
    updateResultsPerPageProperty(subject, pgCrit, DIRECTORY_RESULTS_PER_PAGE);
    userManager.populateConnectedUserList(subject);

    ISearchResults<Community> communities =
        communityServiceManager.listCommunities(subject, pgCrit);
    List<PaginationObject> pagination =
        PaginationUtil.generatePagination(
            communities.getTotalPages(),
            communities.getPageNumber(),
            new DefaultURLPaginator("/directory/ajax/communitylist", pgCrit));

    model.addAttribute("communities", communities);
    model.addAttribute("subject", subject);
    model.addAttribute(PaginationUtil.PAGINATION_LIST_MODEL_ATTR_NAME, pagination);
    model.addAttribute("pgCrit", pgCrit);
    model.addAttribute("numberRecords", communities.getHitsPerPage());
  }

  void configureCommunityPagination(PaginationCriteria<Community> pgCrit) {
    pgCrit.setClazz(Community.class);
    // set defaults if need be
    if (pgCrit.setOrderByIfNull("displayName")) {
      pgCrit.setSortOrder(SortOrder.ASC);
    }
  }

  /**
   * Gets a view-only community page.
   *
   * @param principal
   * @param model
   * @param id
   * @return
   */
  @GetMapping("/community/{id}")
  public String viewCommunity(Principal principal, Model model, @PathVariable Long id) {

    User subject = userManager.getUserByUsername(principal.getName());
    userManager.populateConnectedUserList(subject);
    userManager.populateConnectedGroupList(subject);

    Community comm = communityServiceManager.getCommunityWithAdminsAndGroups(id);
    model.addAttribute("community", comm);
    model.addAttribute("subject", subject);
    // view mode by default
    model.addAttribute("view", true);
    model.addAttribute("canEdit", false);

    return "admin/directory/communityView";
  }

  /**
   * Method to retrieve the project group listing.
   *
   * @param model
   * @param pgCrit
   * @return
   */
  @GetMapping("/ajax/projectgrouplist")
  public ModelAndView ajaxProjectListGroup(
      Model model, PaginationCriteria<Group> pgCrit, GroupSearchCriteria searchCriteria) {
    pgCrit.setSearchCriteria(searchCriteria);
    doProjectGroupList(model, pgCrit);
    return new ModelAndView("admin/directory/projectgroup_list_ajax");
  }

  private void doProjectGroupList(Model model, PaginationCriteria<Group> pgCrit) {
    configureGroupPagination(pgCrit);
    GroupSearchCriteria searchCriteria = (GroupSearchCriteria) pgCrit.getSearchCriteria();
    searchCriteria.setGroupType(GroupType.PROJECT_GROUP);
    searchCriteria.setLoadCommunity(true);
    searchCriteria.setFilterByCommunity(false);
    searchCriteria.setOnlyPublicProfiles(true);
    pgCrit.setSearchCriteria(searchCriteria);

    User subject = userManager.getAuthenticatedUserInSession();
    updateResultsPerPageProperty(subject, pgCrit, DIRECTORY_RESULTS_PER_PAGE);
    userManager.populateConnectedUserList(subject);

    ISearchResults<Group> groups = groupManager.list(subject, pgCrit);
    List<PaginationObject> pagination =
        PaginationUtil.generatePagination(
            groups.getTotalPages(),
            groups.getPageNumber(),
            new DefaultURLPaginator("/directory/ajax/projectgrouplist", pgCrit));
    model.addAttribute("groups", groups);
    model.addAttribute("subject", subject);
    model.addAttribute(PaginationUtil.PAGINATION_LIST_MODEL_ATTR_NAME, pagination);
    model.addAttribute("pgCrit", pgCrit);
    model.addAttribute("numberRecords", groups.getHitsPerPage());
  }
}
