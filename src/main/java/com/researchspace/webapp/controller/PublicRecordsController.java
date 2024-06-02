package com.researchspace.webapp.controller;

import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SharedRecordSearchCriteria;
import com.researchspace.model.preference.Preference;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/** Returns a list of all published document's and their landing pages */
@Controller
@RequestMapping("/public/publishedView/")
public class PublicRecordsController extends BaseController {
  public static final String CONTAINS_PUBLISHED_LINKS = "publishedLinks_for_user_to_see";
  public static final String PUBLIC_PUBLISHED_RECORDS_LIST =
      "groups/sharing/public_published_records_list";
  public static final String PUBLIC_PUBLISHED_RECORDS_LIST_AJAX =
      "groups/sharing/public_published_records_list_ajax";
  public static final String SHARED_RECORDS_ATTR_NAME = "sharedRecords";
  public static final String SHAREE = "sharee";
  private static final String USER_LOGGED_IN = "user_logged_in";

  private @Autowired RecordSharingManager recShareMgr;
  @Autowired private SystemPropertyManager systemPropertyManager;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @GetMapping("publishedDocuments")
  public ModelAndView list(Model model, PaginationCriteria<RecordGroupSharing> pagCrit)
      throws IOException {
    User u = userManager.getAuthenticatedUserInSession();
    if (u == null) {
      u = getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
    } else {
      model.addAttribute(USER_LOGGED_IN, true);
    }
    model.addAttribute(CONTAINS_PUBLISHED_LINKS, true);
    if (pagCrit.setOrderByIfNull("creationDate")) {
      pagCrit.setSortOrder(SortOrder.DESC);
    }
    model.addAttribute(SHAREE, RecordGroupSharing.ANONYMOUS_USER);
    return populateAndShowManageView(model, u, pagCrit, PUBLIC_PUBLISHED_RECORDS_LIST);
  }

  private boolean getPublishAllowedBySysAdmin() {
    return systemPropertyManager
        .getAllSysadminPropertiesAsMap()
        .get("public_sharing")
        .getValue()
        .equals("ALLOWED");
  }

  @GetMapping("publishedDocuments/sort")
  public ModelAndView publicViewOfPublicLinkList(
      Model model,
      PaginationCriteria<RecordGroupSharing> pagCrit,
      SharedRecordSearchCriteria searchCrit) {
    User u = doShareManageSetup(pagCrit, searchCrit);
    model.addAttribute(SHAREE, u.getUsername());
    model.addAttribute(CONTAINS_PUBLISHED_LINKS, true);
    return populateAndShowManageView(model, u, pagCrit, PUBLIC_PUBLISHED_RECORDS_LIST_AJAX);
  }

  @GetMapping("/ajax/publishedDocuments/allowed")
  @ResponseBody
  public AjaxReturnObject<Boolean> isPublishAllowed() {
    return new AjaxReturnObject<>(getPublishAllowedBySysAdmin(), null);
  }

  private User doShareManageSetup(
      PaginationCriteria<RecordGroupSharing> pagCrit, SharedRecordSearchCriteria searchCrit) {
    User u = getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
    pagCrit.setClazz(RecordGroupSharing.class);
    cleanSearchTerm(searchCrit);
    if (!invalidSearchTerms(searchCrit)) {
      pagCrit.setSearchCriteria(searchCrit);
    }
    return u;
  }

  ModelAndView populateAndShowManageView(
      Model model, User subject, PaginationCriteria<RecordGroupSharing> pagCrit, String viewName) {
    updateResultsPerPageProperty(subject, pagCrit, Preference.SHARED_RECORDS_RESULTS_PER_PAGE);
    DefaultURLPaginator urlGn = null;
    ISearchResults<RecordGroupSharing> sharedRecs = null;
    urlGn = new DefaultURLPaginator("/public/publishedView/publishedDocuments/sort", pagCrit);
    sharedRecs = recShareMgr.listAllPublishedRecordsForInternet(pagCrit);

    List<PaginationObject> listings =
        PaginationUtil.generatePagination(
            sharedRecs.getTotalPages(), sharedRecs.getPageNumber(), urlGn);
    model.addAttribute("paginationList", listings);
    model.addAttribute("numberRecords", sharedRecs.getHitsPerPage());
    List<RecordGroupSharing> published = sharedRecs.getResults();
    for (RecordGroupSharing publishedRecord : published) {
      User publisher = publishedRecord.getSharedBy();
      if (!getPublishAllowedBySysAdmin()
          || !systemPropertyPermissionManager.isPropertyAllowed(publisher, "public_sharing")) {
        publishedRecord.setPublicationSummary("Publication disabled");
        publishedRecord.getShared().setName("Publication disabled");
      }
    }
    model.addAttribute(SHARED_RECORDS_ATTR_NAME, published);

    Map<String, PaginationObject> sortableHeaders =
        PaginationUtil.generateOrderByLinks(null, urlGn, "name", "sharee", "creationDate");
    model.addAllAttributes(sortableHeaders);

    return new ModelAndView(viewName, model.asMap());
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
