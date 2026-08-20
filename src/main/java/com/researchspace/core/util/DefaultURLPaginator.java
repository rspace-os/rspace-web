package com.researchspace.core.util;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.StringUtils;

/**
 * Default implementation that adds pagination request parameters to a URL
 */
public class DefaultURLPaginator extends AbstractURLPaginator {

	private String path;

	/**
	 * 
	 * @param path
	 *            The path part of the URL; e.g., '/path/To/Controller'
	 * @param pgCrit
	 *            A {@link IPagination} object, can be <code>null</code>
	 *            .
	 */
	public DefaultURLPaginator(String path, IPagination<?> pgCrit) {
		super(pgCrit);
		this.path = path;
	}

	@Override
	public String generateURL(final int pageNum) {
		StringBuffer query = new StringBuffer();
		if (pgCrit != null) {
			query.append(pgCrit.toURLQueryString(pageNum));
			if (pgCrit.getSearchCriteria() != null) {
				String srch;
				try {
					srch = pgCrit.getSearchCriteria().getURLQueryString();
					if (!StringUtils.isBlank(srch.toString())) {
						query.append("&").append(srch);
					}
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("No method for search field");
				} catch (InvocationTargetException | NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		String queryStr = query.toString();
		return getEscapedURL(path, queryStr);
	}

	@Override
	public String generateURLForCurrentPage(int pageNum) {
		return "#";
	}

}
