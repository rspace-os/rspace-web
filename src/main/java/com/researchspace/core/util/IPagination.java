package com.researchspace.core.util;

import java.util.regex.Pattern;

/**
 * @param <T> type parameter for the object being sorted on; is used for dynamically accessing fields
 *            of the class to order by
 */
public interface IPagination<T> {

	int DEFAULT_RESULTS_PERPAGE = 10;
	/**
	 * Pattern of disallowed characters in orderby clauses to prevent SQL
	 * injection attacks.
	 */
	Pattern ORDERBYBLACKLIST = Pattern.compile("['\";%^&]");

	Class<T> getClazz();

	IPagination<T> setClazz(Class<T> clazz);

	/**
	 * Gets SearchCriteria object; this may be null if no search criteria have
	 * been specified by user.
	 *
	 * @return
	 */
	FilterCriteria getSearchCriteria();

	void setSearchCriteria(FilterCriteria searchCriteria);

	/**
	 * Generates query string, but does not do any escaping. To get this string
	 * escaped, pass into the constructor of URI as 'query' arg.
	 *
	 * @param pageNumber
	 * @return
	 */
	String toURLQueryString(int pageNumber);

	Long getPageNumber();

	/**
	 *
	 * @param pageNumber
	 *            . 0 is the first page. A non-negative {@link Long}.
	 */
	void setPageNumber(Long pageNumber);

	/**
	 * Gets the number of results to be displayed per page (default = 10)
	 *
	 * @return
	 */
	Integer getResultsPerPage();

	/**
	 *
	 * @param resultsPerPage
	 *            A non-negative integer
	 */
	void setResultsPerPage(Integer resultsPerPage);

	/**
	 * Convenience method to indicate that <b> ALL </b> results are needed.
	 *
	 * @return this object to allow methosd chaining
	 */
	IPagination<T> setGetAllResults();

	/**
	 * Gets the name of the property to order the results by. CLients should
	 * validate that this is a meaningful term. <br/>
	 * Can be <code>null</code>
	 *
	 * @return an order by string, or <code>null</code>
	 */
	String getOrderBy();

	/**
	 * Must be a JavaBean property in the listed objects (e.g., for a property
	 * xxx, there must be a getXxx() method.
	 * <p/>
	 * If <Code>orderByField</code> is <code>null</code>, this method does
	 * nothing. Can be a . notation of properties
	 *
	 * @param orderByField
	 *
	 *
	 */
	void setOrderBy(String orderByField);

	/**
	 * setOrderBy method that allows setting value to null
	 *
	 * @param orderByField
	 */
	void setOrderByWithoutChecks(String orderByField);

	/**
	 * Getter for whether this sort order should be ascending or descending
	 *
	 * @return
	 */
	SortOrder getSortOrder();

	void setSortOrder(SortOrder sortOrder);

	/**
	 * Sets the order by clause if this is currently not set - for example, to
	 * set a default
	 *
	 * @param orderBy
	 * @return <code>true</code> if was set, <code>false</code> otherwise
	 */
	boolean setOrderByIfNull(String orderBy);

	/**
	 * Convenience method to calculate the index of the first result based on
	 * pagination.
	 *
	 * @return
	 */
	int getFirstResultIndex();

	/**
	 * Reject blacklisted orderBy fields
	 *
	 * @param orderBy
	 *            - can be <code>null</code> or empty.
	 * @return <code>true</code> if <code>orderBy</code> is safe to use,
	 *         <code>false</code> otherwise
	 */
	boolean isOrderBySafe(String orderBy);

}