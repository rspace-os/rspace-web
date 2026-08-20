package com.researchspace.core.util;

import java.util.List;

/**
 * Interface for search results generated when retrieving records. This
 * interface hides the UI and service levels from the search details.
 */
public interface ISearchResults<T> {

	/**
	 * Return list of search results from the DB; should be consistent with
	 * total pages & records per page fields.
	 * 
	 * @return
	 */
	List<T> getResults();

	/**
	 * Convenience method to return the first search result.
	 * 
	 * @return The first search result, or <code>null</code> if there are no
	 *         search results.
	 */
	T getFirstResult();

	/**
	 * Convenience method to return the last search result in the results held
	 * in this object.
	 * 
	 * @return The last search result, or <code>null</code> if there are no
	 *         search results.
	 */
	T getLastResult();

	/**
	 * The total number of hits matching the query
	 * 
	 * @return
	 */
	Long getTotalHits();

	/**
	 * The number of hits returned in this page. This should be less than or
	 * equal to the hits per page limit
	 * 
	 * @return
	 */
	Integer getHits();

	/**
	 * Gets the page number of the current page that these results are
	 * displaying
	 * 
	 * @return
	 */
	Integer getPageNumber();

	/**
	 * Total number of pages that contain the search results.
	 * 
	 * @return
	 */
	Integer getTotalPages();

	/**
	 * Number of records per page that contain the search results.
	 * 
	 * @return
	 */
	int getHitsPerPage();

	List<PaginationObject> getLinkPages();

	void setLinkPages(List<PaginationObject> linkPages);

	/**
	 * If search was paginated, this returns the original pagination criteria in
	 * the resulst
	 * 
	 * @return a {@link IPagination}, or <code>null</code> if not set.
	 */
	IPagination<?> getPaginationCriteria();

}