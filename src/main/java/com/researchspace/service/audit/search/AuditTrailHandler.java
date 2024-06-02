package com.researchspace.service.audit.search;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import org.apache.shiro.authz.AuthorizationException;

/** top-level handler for searching audit logs trail */
public interface AuditTrailHandler {
  /**
   * @param searchConfig
   * @param pgCrit
   * @param subject
   * @return
   * @throws AuthorizationException if requesting to see audit of unauthorised group or community
   */
  ISearchResults<AuditTrailSearchResult> searchAuditTrail(
      IAuditTrailSearchConfig searchConfig,
      PaginationCriteria<AuditTrailSearchResult> pgCrit,
      User subject);
}
