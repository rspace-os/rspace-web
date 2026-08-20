package com.researchspace.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.core.util.BasicPaginationCriteria;
import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.IPagination;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.audittrail.AuditTrailData;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.beanutils.BeanUtils;


/**
 * POJO to hold pagination criteria, defined as a RequestScope bean. If not configured otherwise,
 * will return 10 results from page1 of a listing, in descending order
 *
 * @param <T> type parameter for the object being sorted on; is used for dynamically accessing fields
 *  *           of the class to order by
 */

@AuditTrailData
public class PaginationCriteria<T> implements Serializable, IPagination<T> {


	private static final long serialVersionUID = -4958233781343541045L;
	
	/**
	 * Controller methods generate a new instance of this class per request and 
	 * set values based on request parameters.<br>
	 * <code>resultsPerPageExplicitlySet<code> records whether resultsPerPage is explicitly set (i.e. was set in a request parameter)
	 * so as to know whether to update stored preferences. See RSPAC-1740
	 */
	@Getter
	@Setter
	private boolean resultsPerPageExplicitlySet = false;
	
	IPagination<T> delegate;
	/**
	 * @param clazz
	 *            A Class<T> object that can be used to create an instance of the generic type for
	 *            checking orderBy properties.
	 */
	public PaginationCriteria(Class<T> clazz) {
		this.delegate = new BasicPaginationCriteria<>(clazz);
	}
	
	public PaginationCriteria() {
		this.delegate = new BasicPaginationCriteria<>();
	}
	
	/**
	 * Creates default {@link PaginationCriteria} with 10 records per page, set to page 0, sort
	 * order DESC
	 * 
	 * @param clazz
	 * @return A PaginationCriteria<T> object.
	 */
	public static <T> PaginationCriteria<T> createDefaultForClass(Class<T> clazz) {
		return createForClass(clazz, null, SortOrder.DESC.toString(), 0L,
					IPagination.DEFAULT_RESULTS_PERPAGE);
	}
	
	/**
	 * Convenient factory method to create a {@link PaginationCriteria} object.
	 * 
	 * @param clazz
	 *            The class of the listed object that will be paginated
	 * @param orderBy
	 * @param sortOrder
	 * @param pageNumber
	 * @param resultsPerPage
	 * @return
	 */
	public static <T> PaginationCriteria<T> createForClass(Class<T> clazz, String orderBy,
			String sortOrder, Long pageNumber, Integer resultsPerPage) {
		PaginationCriteria<T> rc = new PaginationCriteria<>(clazz);
		rc.setOrderBy(orderBy);
		rc.setPageNumber(pageNumber);
		rc.setResultsPerPage(resultsPerPage);
		rc.setSortOrder(SortOrder.valueOf(sortOrder));
		return rc;
	}	
	/**
	 * Boolean query for whether the order by term is a basic Javabeans property
	 *  of the class
	 * @return <code>true</code> if it is, <code>false</code>otherwise
	 */
	public  boolean isOrderByAnInstanceProperty () {
		if (getOrderBy() == null) {
			return false;
		}
		try {
			BeanUtils.getProperty(delegate.getClazz().newInstance(), getOrderBy());
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	@JsonIgnore
	public Class<T> getClazz() {
		return delegate.getClazz();
	}

	public IPagination<T> setClazz(Class<T> clazz) {
		delegate.setClazz(clazz);
		return this;
	}

	/**
	 * Gets SearchCriteria object; this may be null if no search criteria have been specified by
	 * user.
	 * 
	 * @return
	 */
	public FilterCriteria getSearchCriteria() {
		return delegate.getSearchCriteria();
	}

	public void setSearchCriteria(FilterCriteria searchCriteria) {
		delegate.setSearchCriteria(searchCriteria);
	}

	/**
	 * Generates query string, but does not do any escaping. To get this string escaped, pass into
	 * the constructor of URI as 'query' arg.
	 * 
	 * @param pageNumber
	 * @return
	 */
	public String toURLQueryString(int pageNumber) {
		return delegate.toURLQueryString(pageNumber);
	}

	public Long getPageNumber() {
		return delegate.getPageNumber();
	}

	/**
	 * 
	 * @param pageNumber
	 *            . 0 is the first page. A non-negative {@link Long}.
	 */
	public void setPageNumber(Long pageNumber) {
		delegate.setPageNumber(pageNumber);
	}

	/**
	 * Gets the number of results to be displayed per page (default = 10)
	 * 
	 * @return
	 */
	public Integer getResultsPerPage() {
		return delegate.getResultsPerPage();
	}

	/**
	 * Gets the default number of results to be displayed per page
	 * 
	 * @return
	 */
	public static int getDefaultResultsPerPage() {
		return IPagination.DEFAULT_RESULTS_PERPAGE;
	}
	
	/**
	 * 
	 * @param resultsPerPage
	 *            A non-negative integer
	 */
	public void setResultsPerPage(Integer resultsPerPage) {
		delegate.setResultsPerPage(resultsPerPage);
		resultsPerPageExplicitlySet=true; // as opposed to using the default settings
	}

	/**
	 * Convenience method to indicate that <b> ALL </b> results are needed.
	 * @return this object to allow methosd chaining
	 */
	public PaginationCriteria<T> setGetAllResults() {
		delegate.setGetAllResults();
		return this;
	}

	@Override
	public int hashCode() {
		return delegate.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		@SuppressWarnings("rawtypes")
		PaginationCriteria other = (PaginationCriteria) obj;

		if (delegate == null) {
			if (other.delegate != null) {
				return false;
			}
		} else if (!delegate.equals(other.delegate)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return delegate.toString();
	}

	/**
	 * Gets the name of the property to order the results by. CLients should validate that this is a
	 * meaningful term. <br/>
	 * Can be <code>null</code>
	 * 
	 * @return an order by string, or <code>null</code>
	 */
	public String getOrderBy() {
		return delegate.getOrderBy();
	}

	/**
	 * Must be a JavaBean property in the listed objects (e.g., for a property xxx, there must be a
	 * getXxx() method.
	 * <p/>
	 * If <Code>orderByField</code> is <code>null</code>, this method does nothing. Can be a .
	 * notation of properties
	 * 
	 * @param orderByField
	 * 
	 * 
	 */
	public void setOrderBy(String orderByField) {
		delegate.setOrderBy(orderByField);
	}

	/**
	 * setOrderBy method that allows setting value to null
	 * @param orderByField
	 */
	public void setOrderByWithoutChecks(String orderByField) {
		delegate.setOrderByWithoutChecks(orderByField);
	}

	/**
	 * Getter for whether this sort order should be ascending or descending
	 * 
	 * @return
	 */
	public SortOrder getSortOrder() {
		return delegate.getSortOrder();
	}

	public void setSortOrder(SortOrder sortOrder) {
		delegate.setSortOrder(sortOrder);
	}

	/**
	 * Sets the order by clause if this is currently not set - for example, to set a default
	 * 
	 * @param orderBy
	 * @return <code>true</code> if was set, <code>false</code> otherwise
	 */
	public boolean setOrderByIfNull(String orderBy) {
		return delegate.setOrderByIfNull(orderBy);
	}

	/**
	 * Convenience method to calculate the index of the first result based on pagination.
	 * 
	 * @return
	 */
	public int getFirstResultIndex() {
		return delegate.getFirstResultIndex();
	}
	/**
	 * Reject blacklisted orderBy fields
	 * @param orderBy - can be <code>null</code> or empty.
	 * @return <code>true</code> if <code>orderBy</code> is safe to use, <code>false</code> otherwise
	 */
	public  boolean isOrderBySafe(String orderBy) {
		return delegate.isOrderBySafe(orderBy);
	}

}
