package com.researchspace.core.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provides shared methods common to all {@link URLGenerator}s
 */
public abstract class AbstractURLPaginator implements URLGenerator {

	protected IPagination<?> pgCrit;

	public AbstractURLPaginator(IPagination<?> pgCrit) {
		this.pgCrit = pgCrit;
	}

	/**
	 * Gets an escaped URL String from a Path and a Query.
	 * 
	 * @param path
	 * @param query
	 * @return
	 */
	public String getEscapedURL(String path, String query) {
		URI uri = null;
		try {
			uri = new URI(null, null, path, query, null);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return (uri != null) ? uri.toASCIIString() : null;
	}

	@Override
	public String generateURLForRecordsPerPage(int numRecordsPerPage) {
		int originalRecordsPerPage = IPagination.DEFAULT_RESULTS_PERPAGE;
		if (pgCrit != null) {
			originalRecordsPerPage = pgCrit.getResultsPerPage();
			pgCrit.setResultsPerPage(numRecordsPerPage);
		}
		String url = generateURL(0);
		if (pgCrit != null) {
			pgCrit.setResultsPerPage(originalRecordsPerPage);
		}
		return url;
	}

	@Override
	public String generateURLForOrderBy(String orderByClause) {
		String originalRecordsPerPage = null;
		if (pgCrit != null) {
			originalRecordsPerPage = pgCrit.getOrderBy();
			pgCrit.setOrderBy(orderByClause);

		}
		String url = generateURL(0);
		if (pgCrit != null) {
			pgCrit.setOrderByWithoutChecks(originalRecordsPerPage);
		}
		return url;
	}

}
