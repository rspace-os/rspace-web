package com.researchspace.core.util;

/**
 * Callback interface for generating URLs from paginated listings of objects.
 */
public interface URLGenerator {

	/**
	 * Gets the URL to be placed as the value of the 'href' attribute inteh
	 * paginatedlisting
	 * 
	 * @param pageNum
	 *            the pagenumber to be included in the link
	 * @return A String
	 */
	String generateURL(int pageNum);

	/**
	 * Gets the URL to be placed as the value of the 'href' attribute for the
	 * currently displayed page, in case this requires special handling.
	 * 
	 * @param pageNum
	 * @return A String
	 */
	String generateURLForCurrentPage(int pageNum);

	String generateURLForRecordsPerPage(int numRecordsPerPage);

	String generateURLForOrderBy(String orderByClause);

}
