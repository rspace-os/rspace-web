package com.researchspace.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

public class PaginationUtil {
	/**
	 * Standard name for pagination list in Spring Model attribute for referral
	 * in JSP pages.
	 */
	public static final String PAGINATION_LIST_MODEL_ATTR_NAME = "paginationList";

	/**
	 * Generates listing for records-per-page listings
	 * 
	 * @param urlGenerator
	 * @param className
	 *            An optional CSS class name to identify elements for the
	 *            generated links
	 * @return A possibly empty but non-null {@link List} of
	 *         {@link PaginationObject}s
	 */
	public static List<PaginationObject> generateRecordsPerPageListing(URLGenerator urlGenerator, String className) {
		if (StringUtils.isBlank(className)) {
			className = PaginationObject.DEFAULT_CLASS_NAME;
		}
		List<PaginationObject> linkPages = new ArrayList<>();
		Integer[] values = new Integer[] { 10, 15, 30, 50 };
		for (Integer numRecords : values) {
			String link = urlGenerator.generateURLForRecordsPerPage(numRecords);
			PaginationObject po = PaginationObject.create(numRecords + "", link, className);
			linkPages.add(po);
		}
		return linkPages;
	}

	/**
	 * Generates listing for records-per-page listings
	 * 
	 * @param urlGenerator
	 * @param className
	 *            An optional CSS class name to identify elements for the
	 *            generated links
	 * @return A possibly empty but non-null {@link List} of
	 *         {@link PaginationObject}s
	 */
	public static PaginationObject generateOrderByLink(String orderBy, URLGenerator urlGenerator, String className) {
		if (StringUtils.isBlank(className)) {
			className = PaginationObject.DEFAULT_CLASS_NAME;
		}

		String link = urlGenerator.generateURLForOrderBy(orderBy);
		PaginationObject po = PaginationObject.create(orderBy, link, className);
		return po;
	}

	/**
	 * Given a list of properties to generate 'orderBy' links for, returns a map
	 * of PAginationObjects keyed by string that can be used to populate a Model
	 * object for return to the UI.
	 * <p>
	 * The convention is that for a sortable property xxx, the key will be of
	 * the form 'orderByXxxLink"
	 * 
	 * @param className
	 * @param urlGenerator
	 * @param propertiesToOrder
	 * @return A possibly empty but non-<code>null</code> Map
	 */
	public static Map<String, PaginationObject> generateOrderByLinks(String className, URLGenerator urlGenerator,
			String... propertiesToOrder) {
		Map<String, PaginationObject> rc = new TreeMap<>();
		if (propertiesToOrder == null) {
			return rc;
		}
		for (String property : propertiesToOrder) {
			PaginationObject po = generateOrderByLink(property, urlGenerator, className);
			String keyname = generateModelAttributeName(property);
			rc.put(keyname, po);
		}
		return rc;

	}

	private static String generateModelAttributeName(String property) {
		String keyname = "orderBy" + property.toUpperCase().substring(0, 1) + property.substring(1) + "Link";
		return keyname;
	}

	/**
	 * Alternative paginator that takes a callback class, {@link URLGenerator}
	 * as a parameter. that constructs the URLs.
	 * 
	 * @param totalPages
	 * @param pageId
	 * @param urlGenerator
	 * @return
	 */
	public static List<PaginationObject> generatePagination(int totalPages, int pageId, URLGenerator urlGenerator) {
		return generatePagination(totalPages, pageId, urlGenerator, PaginationObject.DEFAULT_CLASS_NAME);
	}

	/**
	 * Generates paginated links, using he supplied URL generator to generate
	 * the links themselves.
	 * 
	 * @param totalPages
	 * @param pageId
	 * @param urlGenerator
	 * @param classnameForLinks
	 * @return
	 */
	public static List<PaginationObject> generatePagination(int totalPages, int pageId, URLGenerator urlGenerator,
			String classnameForLinks) {

		List<PaginationObject> linkPages = new ArrayList<>();
		if (totalPages < 2) {
			return linkPages;
		}

		boolean showFirstPage = pageId > 5;
		int firstPage = showFirstPage ? pageId - 4 : 0;

		int laterPages = totalPages - pageId;
		boolean showLastPage = laterPages > 6;
		int lastPage = showLastPage ? pageId + 5 : totalPages;

		// first page
		if (showFirstPage) {
			createAndAddPaginationObject(urlGenerator, linkPages, "First", 0, pageId, classnameForLinks);
		}

		// middle pages
		for (int i = firstPage; i < lastPage; i++) {
			createAndAddPaginationObject(urlGenerator, linkPages, "" + (i + 1), i, pageId, classnameForLinks);
		}

		// last page
		if (showLastPage) {
			createAndAddPaginationObject(urlGenerator, linkPages, "Last", totalPages - 1, pageId, classnameForLinks);
		}

		return linkPages;
	}

	private static void createAndAddPaginationObject(URLGenerator urlGenerator, List<PaginationObject> linkPages,
			String pageName, int pageNumberForURL, int currentPageId, String classnameForLinks) {

		String link;
		if (pageNumberForURL == currentPageId) {
			link = "#";
		} else {
			link = urlGenerator.generateURL(pageNumberForURL);
		}

		PaginationObject po = PaginationObject.create(pageName, link, classnameForLinks, pageNumberForURL + 1);
		linkPages.add(po);
	}

}
