package com.researchspace.service.audit.search;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/** top-level handler for searching audit trail */
@Slf4j
public class AuditTrailHandlerImpl implements AuditTrailHandler {

  private @Autowired IAuditTrailSearch logSearcher;
  private @Autowired AuditTrailActorVisibility actorVisibility;
  private @Autowired AuditTrailService auditService;

  public ISearchResults<AuditTrailSearchResult> searchAuditTrail(
      IAuditTrailSearchConfig inputSearchConfig,
      PaginationCriteria<AuditTrailSearchResult> pgCrit,
      User subject) {

    AuditTrailSearchElement internalCfg = configureUserRestriction(inputSearchConfig, subject);
    if (internalCfg.getUsernames().isEmpty() && !actorVisibility.isSysAdmin(subject)) {
      return SearchResultsImpl.emptyResult(pgCrit);
    }
    ISearchResults<AuditTrailSearchResult> res = logSearcher.search(pgCrit, internalCfg);
    auditService.notify(new GenericEvent(subject, internalCfg, AuditAction.SEARCH));
    return res;
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
    return actorVisibility.restrict(inputSearchConfig, subject);
  }
}
