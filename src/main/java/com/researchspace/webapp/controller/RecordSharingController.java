package com.researchspace.webapp.controller;

import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SharedRecordSearchCriteria;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.ShareRecordMessageOrRequestDTO;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/** Front end for 'Shared Documents' page. */
@Controller
@RequestMapping("/record/share")
public class RecordSharingController extends BaseController {
  public static final String CONTAINS_PUBLISHED_LINKS = "publishedLinks_for_user_to_see";
  public static final String SHARED_RECORDS_LIST = "groups/sharing/shared_records_list";
  public static final String SHARED_RECORDS_LIST_AJAX = "groups/sharing/shared_records_list_ajax";
  public static final String PUBLISHED_RECORDS_LIST_AJAX =
      "groups/sharing/published_records_list_ajax";
  public static final String SHARED_RECORDS_ATTR_NAME = "sharedRecords";
  public static final String SYSADMIN_PUBLISHED_LINKS = "publishedLinks_for_sysadmin_to_manage";
  public static final String SHAREE = "sharee";

  private @Autowired RecordSharingManager recShareMgr;
  private @Autowired SharingHandler sharingHandler;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  /** Gets initial listing page load, sorted by most recently shared for sharedRecords */
  @GetMapping("manage")
  public ModelAndView list(
      Model model, Principal principal, PaginationCriteria<RecordGroupSharing> pagCrit) {
    User u = userManager.getUserByUsername(principal.getName());

    if (pagCrit.setOrderByIfNull("creationDate")) {
      pagCrit.setSortOrder(SortOrder.DESC);
    }
    model.addAttribute(
        "publish_allowed", systemPropertyPermissionManager.isPropertyAllowed(u, "public_sharing"));
    return populateAndShowManageView(model, u, pagCrit, SHARED_RECORDS_LIST);
  }

  /** Gets initial listing page load, sorted by most recently published for published records */
  @GetMapping("published/manage")
  public ModelAndView listPublished(
      Model model, Principal principal, PaginationCriteria<RecordGroupSharing> pagCrit) {
    User u = userManager.getUserByUsername(principal.getName());

    if (pagCrit.setOrderByIfNull("creationDate")) {
      pagCrit.setSortOrder(SortOrder.DESC);
    }
    model.addAttribute(CONTAINS_PUBLISHED_LINKS, true);
    model.addAttribute(SHAREE, u.getUsername());
    model.addAttribute(
        "publish_allowed", systemPropertyPermissionManager.isPropertyAllowed(u, "public_sharing"));
    return populateAndShowManageView(model, u, pagCrit, SHARED_RECORDS_LIST, true);
  }

  /** Ajax list refreshes list view based on pagination links. */
  @GetMapping("ajax/manage")
  public ModelAndView ajaxlist(
      Model model,
      Principal principal,
      PaginationCriteria<RecordGroupSharing> pagCrit,
      SharedRecordSearchCriteria searchCrit) {
    User u = doShareManageSetup(principal, pagCrit, searchCrit);
    return populateAndShowManageView(model, u, pagCrit, SHARED_RECORDS_LIST_AJAX);
  }

  /**
   * Ajax list refreshes list view based on pagination links.
   *
   * @param model
   * @param principal
   * @param pagCrit
   * @param searchCrit
   * @return
   */
  @GetMapping("ajax/publiclinks/manage")
  public ModelAndView ajaxpublicLinkList(
      Model model,
      Principal principal,
      PaginationCriteria<RecordGroupSharing> pagCrit,
      SharedRecordSearchCriteria searchCrit) {
    User u = doShareManageSetup(principal, pagCrit, searchCrit);
    model.addAttribute(SHAREE, u.getUsername());
    model.addAttribute(CONTAINS_PUBLISHED_LINKS, true);
    return populateAndShowManageView(model, u, pagCrit, PUBLISHED_RECORDS_LIST_AJAX, true);
  }

  private User doShareManageSetup(
      Principal principal,
      PaginationCriteria<RecordGroupSharing> pagCrit,
      SharedRecordSearchCriteria searchCrit) {
    User u = userManager.getUserByUsername(principal.getName());
    pagCrit.setClazz(RecordGroupSharing.class);
    cleanSearchTerm(searchCrit);
    if (!invalidSearchTerms(searchCrit)) {
      pagCrit.setSearchCriteria(searchCrit);
    }
    return u;
  }

  ModelAndView populateAndShowManageView(
      Model model, User subject, PaginationCriteria<RecordGroupSharing> pagCrit, String viewName) {
    return populateAndShowManageView(model, subject, pagCrit, viewName, false);
  }

  ModelAndView populateAndShowManageView(
      Model model,
      User subject,
      PaginationCriteria<RecordGroupSharing> pagCrit,
      String viewName,
      boolean isForPublicLinks) {
    updateResultsPerPageProperty(subject, pagCrit, Preference.SHARED_RECORDS_RESULTS_PER_PAGE);
    DefaultURLPaginator urlGn = null;
    ISearchResults<RecordGroupSharing> sharedRecs = null;
    if (isForPublicLinks) {
      urlGn = new DefaultURLPaginator("/record/share/ajax/publiclinks/manage", pagCrit);
      if (subject.hasRole(Role.SYSTEM_ROLE)) {
        sharedRecs = recShareMgr.listAllPublishedRecords(pagCrit);
        model.addAttribute(SYSADMIN_PUBLISHED_LINKS, true);
      } else if (subject.hasRole(Role.ADMIN_ROLE)) {
        sharedRecs =
            recShareMgr.listAllRecordsPublishedByMembersOfAdminsCommunities(pagCrit, subject);
        model.addAttribute(SYSADMIN_PUBLISHED_LINKS, true);
      } else if (subject.hasRole(Role.PI_ROLE)) {
        sharedRecs =
            recShareMgr.listPublishedRecordsOwnedByUserOrGroupMembersPlusRecordsPublishedByUser(
                subject, pagCrit);
      } else {
        sharedRecs = recShareMgr.listUserRecordsPublished(subject, pagCrit);
      }
    } else {
      urlGn = new DefaultURLPaginator("/record/share/ajax/manage", pagCrit);
      sharedRecs = recShareMgr.listSharedRecordsForUser(subject, pagCrit);
    }

    List<PaginationObject> listings =
        PaginationUtil.generatePagination(
            sharedRecs.getTotalPages(), sharedRecs.getPageNumber(), urlGn);
    model.addAttribute("paginationList", listings);
    model.addAttribute("numberRecords", sharedRecs.getHitsPerPage());
    model.addAttribute(SHARED_RECORDS_ATTR_NAME, sharedRecs.getResults());

    Map<String, PaginationObject> sortableHeaders =
        PaginationUtil.generateOrderByLinks(null, urlGn, "name", "sharee", "creationDate");
    model.addAllAttributes(sortableHeaders);

    List<ShareRecordMessageOrRequestDTO> dto =
        recShareMgr.getSharedRecordRequestsByUserId(subject.getId());
    model.addAttribute("requests", dto);

    return new ModelAndView(viewName, model.asMap());
  }

  /**
   * Unshares a single record with a group
   *
   * @param principal
   * @param recordGroupShareId The Id of a RecordGroupSharing object
   * @return ModelAndView to the
   */
  @PostMapping("unshare")
  public String unshare(Principal principal, @RequestParam("grpShareId") Long recordGroupShareId) {
    User subject = userManager.getUserByUsername(principal.getName());
    sharingHandler.unshare(recordGroupShareId, subject);
    // redirect-after-post
    return "redirect:/record/share/manage";
  }

  /**
   * Updates view/ edit permissions via Ajax
   *
   * @param principal
   * @param id
   * @param action
   * @return
   */
  @PostMapping("permissions")
  @ResponseBody
  // action is validated by service method
  public AjaxReturnObject<String> updatePermissions(
      Principal principal, @RequestParam("id") Long id, @RequestParam("action") String action) {

    String uname = principal.getName();
    // check arguments
    ErrorList el = recShareMgr.updatePermissionForRecord(id, action, uname);
    if (el == null) {
      return new AjaxReturnObject<String>("Updated", null);
    } else {
      return new AjaxReturnObject<String>(null, el);
    }
  }

  // removes wild card characters
  private void cleanSearchTerm(SharedRecordSearchCriteria searchCriteria) {
    String term = searchCriteria.getAllFields();
    term = SecureStringUtils.removeWildCards(term);
    searchCriteria.setAllFields(term);
  }

  private boolean invalidSearchTerms(SharedRecordSearchCriteria searchCriteria) {
    return searchCriteria == null || StringUtils.isBlank(searchCriteria.getAllFields());
  }
}
