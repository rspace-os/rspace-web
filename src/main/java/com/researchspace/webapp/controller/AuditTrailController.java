package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toSet;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.views.CommunityListResult;
import com.researchspace.model.views.GroupListResult;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.audit.search.AuditTrailHandler;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller("auditTrailController")
@RequestMapping("/audit")
public class AuditTrailController extends BaseController {

  private @Autowired AuditTrailHandler auditTrailHandler;
  private @Autowired CommunityServiceManager communityMgr;
  private @Autowired AuditTrailSearchResultCsvGenerator auditTrailSearchResultCsvGenerator;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @GetMapping("/auditing")
  public ModelAndView auditingPage(Model model) {
    User subject = userManager.getAuthenticatedUserInSession();
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(subject, "public_sharing"));
    return new ModelAndView("audit/auditing");
  }

  /**
   * Gets available audit domains
   *
   * @return
   */
  @GetMapping("/domains")
  @ResponseBody
  public AjaxReturnObject<EnumSet<AuditDomain>> getDomains() {
    return new AjaxReturnObject<EnumSet<AuditDomain>>(EnumSet.allOf(AuditDomain.class), null);
  }

  /**
   * Gets available audit actions
   *
   * @return
   */
  @GetMapping("/actions")
  @ResponseBody
  public AjaxReturnObject<EnumSet<AuditAction>> getActions() {
    return new AjaxReturnObject<>(EnumSet.allOf(AuditAction.class), null);
  }

  /**
   * Gets available users whose audit trails can be examined by the given subject
   *
   * @return AjaxReturnObject<List<PublicUserInfo>>
   */
  @GetMapping("/queryableUsers")
  @ResponseBody
  public AjaxReturnObject<List<UserBasicInfo>> getUsersToQuery(
      @RequestParam(value = "term") String term) {

    User subject = userManager.getAuthenticatedUserInSession();
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setGetAllResults();
    UserSearchCriteria userSearchCriteria = new UserSearchCriteria();
    userSearchCriteria.setOnlyEnabled(true);
    pgCrit.setSearchCriteria(userSearchCriteria);
    List<User> queryableUsers = userManager.getViewableUsers(subject, pgCrit).getResults();
    if (StringUtils.isNotEmpty(term)) {
      List<User> matchingUsers = userManager.searchUsers(term);
      queryableUsers.retainAll(matchingUsers);
    }

    List<UserBasicInfo> userInfos = new ArrayList<>();
    final int autoCompleteDisplayLimit = 10;
    for (User user : queryableUsers) {
      userInfos.add(user.toBasicInfo());
      if (userInfos.size() > autoCompleteDisplayLimit) {
        break; // autocomplete displays only 10 users, so no need for
        // more (RSPAC-1252)
      }
    }
    return new AjaxReturnObject<List<UserBasicInfo>>(userInfos, null);
  }

  /**
   * Main query interface for querying audit trail. Example queries below.
   *
   * <p>Currently the UI restricts audits to be only for record-related domains.
   *
   * <table>
   * <tr>
   * <th>Query</th>
   * <th>Explanation</th>
   * </tr>
   * <tr>
   * <td>/audit/query</td>
   * <td>completely unconstrained search over all domains, users, actions and
   * time</td>
   * </tr>
   * <tr>
   * <td>/audit/query?from=2014-04-30</td>
   * <td>all logged events after 30th April</td>
   * </tr>
   * <tr>
   * <td>/audit/query?to=2014-04-30</td>
   * <td>all logged events up to and including 30th April</td>
   * </tr>
   * <tr>
   * <td>/audit/query?from=2014-03-31&to=2014-04-30</td>
   * <td>all logged events in April</td>
   * </tr>
   * <tr>
   * <td>/audit/query?users=userid1&users=userid2&users=userid3</td>
   * <td>all logged events for 3 users e.g., as if selected from checkbox or
   * select</td>
   * </tr>
   * <tr>
   * <td>/audit/query?groups=groupId1&groups=groupid2</td>
   * <td>all logged events for people in 2 groups e.g., as if selected from
   * checkbox or select</td>
   * </tr>
   * <tr>
   * <td>/audit/query?domain=RECORD&domain=USER</td>
   * <td>all logged events in RECORD or USER domain</td>
   * </tr>
   * <tr>
   * <td>/audit/query?action=CREATE&action=EDIT</td>
   * <td>all logged 'CREATE' or 'EDIT' actions</td>
   * </tr>
   * <tr>
   * <td>/audit/query?oid=SD12345</td>
   * <td>searches for logs relating to a specific object identified by this
   * ID.</td>
   * </tr>
   * </table>
   *
   * @param inputSearchConfig A configuration object AuditTrailUISearchConfig
   * @param errors BindingResult validation of configuration
   * @param pgCrit
   * @return
   */
  @GetMapping("/query")
  @ResponseBody
  public AjaxReturnObject<ISearchResults<AuditTrailSearchResult>> search(
      @Valid AuditTrailUISearchConfig inputSearchConfig,
      BindingResult errors,
      PaginationCriteria<AuditTrailSearchResult> pgCrit) {
    User subject = userManager.getAuthenticatedUserInSession();
    Optional<ErrorList> validationErrors = validateAndConfigure(inputSearchConfig, errors, subject);
    if (validationErrors.isPresent()) {
      return new AjaxReturnObject<>(null, validationErrors.get());
    }
    ISearchResults<AuditTrailSearchResult> res =
        auditTrailHandler.searchAuditTrail(inputSearchConfig, pgCrit, subject);
    return new AjaxReturnObject<>(res, null);
  }

  @GetMapping("/download")
  @ResponseBody
  public ResponseEntity<String> download(
      @Valid AuditTrailUISearchConfig inputSearchConfig,
      BindingResult errors,
      PaginationCriteria<AuditTrailSearchResult> pgCrit)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    Optional<ErrorList> validationErrors = validateAndConfigure(inputSearchConfig, errors, subject);
    if (validationErrors.isPresent()) {
      throw new IllegalArgumentException(
          validationErrors.get().getAllErrorMessagesAsStringsSeparatedBy(","));
    }
    pgCrit.setResultsPerPage(AuditTrailSearchResultCsvGenerator.MAX_RESULTS_PER_CSV);
    ISearchResults<AuditTrailSearchResult> res =
        auditTrailHandler.searchAuditTrail(inputSearchConfig, pgCrit, subject);
    ResponseEntity<String> rc =
        auditTrailSearchResultCsvGenerator.convertToCsv(res, inputSearchConfig);

    return rc;
  }

  private Optional<ErrorList> validateAndConfigure(
      AuditTrailUISearchConfig inputSearchConfig, BindingResult errors, User subject) {

    validatePermissions(subject, inputSearchConfig);
    if (errors.hasErrors()) {
      ErrorList errorList = new ErrorList();
      inputValidator.populateErrorList(errors, errorList);
      return Optional.of(errorList);
    }
    ErrorList errorList =
        inputValidator.validateAndGetErrorList(
            inputSearchConfig, new AuditTrailUISearchConfigValidator());
    if (errorList != null) {
      return Optional.of(errorList);
    }
    if (inputSearchConfig.getUsers() != null) {
      addUsers(inputSearchConfig);
    }
    if (inputSearchConfig.getGroups() != null) {
      addGroupUsers(inputSearchConfig);
    }
    if (inputSearchConfig.getCommunities() != null) {
      addCommunityUsers(inputSearchConfig, subject);
    }
    return Optional.empty();
  }

  private void validatePermissions(User subject, AuditTrailUISearchConfig config) {
    if (subject.hasRole(Role.USER_ROLE)) { // covers PI and
      if (config.getCommunities() != null || config.getGroups() != null) {
        throw new AuthorizationException("Unauthorized attempt to audit a group or community");
      }
    } else if (subject.hasRole(Role.ADMIN_ROLE)) {
      if (config.getCommunities() != null) {
        throw new AuthorizationException("Unauthorized attempt to audit a community");
      }
    }
  }

  private void addUsers(AuditTrailUISearchConfig searchConfig) {
    searchConfig.setUsernames(toSet(User.getUsernamesFromMultiUser(searchConfig.getUsers())));
  }

  private void addGroupUsers(AuditTrailUISearchConfig searchConfig) {
    Set<Long> groupIds =
        GroupListResult.getGroupIdsfromMultiGroupAutocomplete(searchConfig.getGroups());
    for (Long grpId : groupIds) {
      Group grp = groupManager.getGroup(grpId);
      searchConfig.addUsernames(
          grp.getMembers().stream().map(User::getUsername).collect(Collectors.toList()));
    }
  }

  private void addCommunityUsers(AuditTrailUISearchConfig searchConfig, User subject) {
    Set<Long> commIds =
        CommunityListResult.getCommunityIdsfromMultiGroupAutocomplete(
            searchConfig.getCommunities());

    for (Long commId : commIds) {
      PaginationCriteria<User> pgcrit = PaginationCriteria.createDefaultForClass(User.class);
      // pgCrit.setGetAllResults();
      ISearchResults<User> usersInCommunity = communityMgr.listUsers(commId, subject, pgcrit);
      searchConfig.addUsernames(
          usersInCommunity.getResults().stream()
              .map(User::getUsername)
              .collect(Collectors.toList()));
    }
  }
}
