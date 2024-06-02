package com.researchspace.service.audit.search;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.service.UserManager;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/** top-level handler for searching audit trail */
@Slf4j
public class AuditTrailHandlerImpl implements AuditTrailHandler {

  private @Autowired IAuditTrailSearch logSearcher;
  private @Autowired UserManager userManager;
  private @Autowired AuditTrailService auditService;
  private @Autowired IAuditSearchResultPostProcessor postProcessor;

  public ISearchResults<AuditTrailSearchResult> searchAuditTrail(
      IAuditTrailSearchConfig inputSearchConfig,
      PaginationCriteria<AuditTrailSearchResult> pgCrit,
      User subject) {

    AuditTrailSearchElement internalCfg = configureUserRestriction(inputSearchConfig, subject);
    if (internalCfg.getUsernames().isEmpty() && !isSysAdmin(subject)) {
      return SearchResultsImpl.emptyResult(pgCrit);
    }
    ISearchResults<AuditTrailSearchResult> res = logSearcher.search(pgCrit, internalCfg);
    postProcessAuditResults(res);
    auditService.notify(new GenericEvent(subject, internalCfg, AuditAction.SEARCH));
    return res;
  }

  private boolean isSysAdmin(User subject) {
    return subject.hasRole(Role.SYSTEM_ROLE);
  }

  /**
   * Gets the users whose audit events the subject is authorised to see.
   *
   * @param inputSearchConfig
   * @param subject
   * @return
   */
  AuditTrailSearchElement configureUserRestriction(
      IAuditTrailSearchConfig inputSearchConfig, User subject) {
    AuditTrailSearchElement internalCfg = new AuditTrailSearchElement(inputSearchConfig);

    if (!isSysAdmin(subject)) {

      PaginationCriteria<User> pgcrit = PaginationCriteria.createDefaultForClass(User.class);
      pgcrit.setResultsPerPage(Integer.MAX_VALUE);
      List<User> viewableUsers = userManager.getViewableUsers(subject, pgcrit).getResults();
      if (!viewableUsers.contains(subject)) {
        viewableUsers.add(subject);
      }
      // get intersection of supplied and viewable unames ub
      boolean usernameRestriction = !inputSearchConfig.getUsernames().isEmpty();
      Set<String> usernames =
          viewableUsers.stream()
              .map(u -> u.getUsername())
              .filter(s -> !usernameRestriction || inputSearchConfig.getUsernames().contains(s))
              .collect(Collectors.toSet());
      internalCfg.setUsernames(usernames);
    }
    return internalCfg;
  }

  // handles any modification of audit data for presentation in the UI.
  private void postProcessAuditResults(ISearchResults<AuditTrailSearchResult> res) {
    postProcessor.process(res);
  }
}
