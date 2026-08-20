package com.researchspace.core.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchResultsImpl<T> implements ISearchResults<T>, Serializable {

	private static final long serialVersionUID = -3796753218620963472L;

	public static final int HITS_PER_PAGE_DEFAULT = 10;
	private List<T> results = new ArrayList<>();
	private int pageNumber = 0;
	private Long totalHits;
	private int hitsPerPage = HITS_PER_PAGE_DEFAULT;
	private List<PaginationObject> linkPages;
	private IPagination<?> paginationCriteria;

	/**
	 * Utility method to return an empty result set
	 *
	 * @param pgCrit
	 * @return
	 */
	public static <T> SearchResultsImpl<T> emptyResult(IPagination<T> pgCrit) {
		return new SearchResultsImpl<>(Collections.<T> emptyList(), pgCrit, 0L);
	}

	/**
	 * No null arguments.
	 *
	 * @param results
	 *            A possibly empty but non-null <code>List</code> of results.
	 * @param pageNumber
	 *            The desired page number to display.
	 * @param totalHits
	 *            The total number of possible hits (disregarding pagination)
	 */
	public SearchResultsImpl(List<T> results, int pageNumber, long totalHits) {
		super();
		this.results = results;
		this.pageNumber = pageNumber;
		this.totalHits = totalHits;
	}

	/**
	 * Convenience constructor that populates internal fields from a
	 * PaginationCriteria object
	 *
	 * @param results
	 *            A <code>List</code> of results.
	 * @param pgCrit
	 *            The pagination criteria where we can access information about
	 *            current page number, total hits per page, sort/ordering.
	 * @param totalHits
	 *            The total number of possible hits (disregarding pagination)
	 */
	public SearchResultsImpl(List<T> results, IPagination<?> pgCrit, long totalHits) {
		this(results, pgCrit.getPageNumber().intValue(), totalHits);

		this.hitsPerPage = pgCrit.getResultsPerPage();
		this.paginationCriteria = pgCrit;
	}

	public SearchResultsImpl(List<T> results, int pageNumber, long totalHits, int hitPerPage){
		this(results, pageNumber, totalHits);
		this.hitsPerPage = hitPerPage;
	}

	@Override
	public List<T> getResults() {
		return results;
	}

	@Override
	public Long getTotalHits() {
		return totalHits;
	}

	@Override
	public Integer getHits() {
		return results.size();
	}

	@Override
	public Integer getPageNumber() {
		return pageNumber;
	}

	@Override
	public Integer getTotalPages() {
		assert (hitsPerPage > 0);

		Long pages = totalHits / hitsPerPage;
		if (totalHits % hitsPerPage != 0) {
			pages += 1;
		}
		return pages.intValue();
	}

	@Override
	public String toString() {
		return "DatabaseSearchResults [pageNumber=" + pageNumber + ", totalHits=" + totalHits + "]";
	}

	@Override
	public int getHitsPerPage() {

		return hitsPerPage;
	}

	public List<PaginationObject> getLinkPages() {
		return linkPages;
	}

	@Override
	public void setLinkPages(List<PaginationObject> linkPages) {
		this.linkPages = linkPages;
	}

	@Override
	public IPagination<?> getPaginationCriteria() {
		return paginationCriteria;
	}

	@Override
	public T getFirstResult() {
		if (!results.isEmpty()) {
			return results.get(0);
		} else {
			return null;
		}
	}

	@Override
	public T getLastResult() {
		if (!results.isEmpty()) {
			return results.get(results.size() - 1);
		} else {
			return null;
		}
	}

}
