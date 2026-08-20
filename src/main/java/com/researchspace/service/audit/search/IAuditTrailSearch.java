package com.researchspace.service.audit.search;

import com.researchspace.core.util.IPagination;
import com.researchspace.core.util.ISearchResults;

/**
 * Top-level search interface for interacting with audit-trail
 */
public interface IAuditTrailSearch {

	/**
	 * Searches audit logs using the supplied search configuration and pagination criteria
	 * @return An {@link ISearchResults} of {@link AuditTrailSearchElement}s.
	 */
	ISearchResults<AuditTrailSearchResult> search(IPagination<?> pgCrit,
			AuditTrailSearchElement searchConfig);

}
