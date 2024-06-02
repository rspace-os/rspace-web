package com.researchspace.webapp.controller;

import com.researchspace.admin.service.GroupUsageInfo;
import com.researchspace.admin.service.SysadminGroupManager;
import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Controller for 'Groups' part of Sysadmin tab */
@Controller("sysAdminGroupsController")
@RequestMapping("/system/groups")
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
public class SysadminGroupsController extends BaseController {

  private static final String GROUP_LIST = "system/group_list";
  private static final String GROUP_LIST_AJAX = "system/group_list_ajax";

  @Autowired private SysadminGroupManager sysMgr;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  /**
   * Loads a new page of group results
   *
   * @param model
   * @param pgCrit
   * @return fa JSP view name for the group list page
   */
  @GetMapping("/list")
  public String listGroups(
      Model model, PaginationCriteria<Group> pgCrit, GroupSearchCriteria groupSearchCriteria) {
    // PaginationCriteria<Group>pgCrit = PaginationCriteria.createDefaultForClass(Group.class);
    configureGrpPagination(pgCrit);
    User subject = userManager.getAuthenticatedUserInSession();
    doGroupList(subject, model, pgCrit, 0);
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(subject, "public_sharing"));
    return GROUP_LIST;
  }

  private void doGroupList(
      User subject, Model model, PaginationCriteria<Group> pgCrit, int pageNum) {
    if (pgCrit.getSearchCriteria() == null) {
      pgCrit.setSearchCriteria(new GroupSearchCriteria());
    }

    GroupSearchCriteria glf = (GroupSearchCriteria) pgCrit.getSearchCriteria();
    glf.setLoadCommunity(true);
    glf.setFilterByCommunity(true);
    pgCrit.setSearchCriteria(glf);

    ISearchResults<GroupUsageInfo> grps = sysMgr.getGroupUsageInfo(subject, pgCrit);
    log.info("Search results generated, adding results to view...");
    List<PaginationObject> paginationList =
        PaginationUtil.generatePagination(
            grps.getTotalPages(),
            pageNum,
            new DefaultURLPaginator("/system/groups/ajax/list", pgCrit));
    model.addAttribute("groupInfo", grps.getResults());
    model.addAttribute("paginationList", paginationList);
    model.addAttribute("pgCrit", pgCrit);
  }

  /**
   * Reloads a new section of group listing page.
   *
   * @param principal
   * @param model
   * @param pgCrit
   * @return a JSP page for group listing
   */
  @GetMapping("/ajax/list")
  public String ajaxlistGroups(
      Principal principal,
      Model model,
      PaginationCriteria<Group> pgCrit,
      GroupSearchCriteria groupSearchCriteria) {
    pgCrit.setSearchCriteria(groupSearchCriteria);
    configureGrpPagination(pgCrit);
    User subject = userManager.getAuthenticatedUserInSession();
    doGroupList(subject, model, pgCrit, pgCrit.getPageNumber().intValue());
    return GROUP_LIST_AJAX;
  }

  void configureGrpPagination(PaginationCriteria<Group> pgCrit) {
    pgCrit.setClazz(Group.class);
    // set defaults if need be
    if (pgCrit.setOrderByIfNull("displayName")) {
      pgCrit.setSortOrder(SortOrder.DESC);
    }
  }
}
