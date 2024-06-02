package com.researchspace.dao.hibernate;

import com.axiope.search.SearchConstants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.NameDateFilter;
import com.researchspace.dao.RecordDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.ObjectToIdPropertyTransformer;
import com.researchspace.model.record.RSForm;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Simple filter mechanism to filter a folder's contents by name, date or form <br>
 * This uses database search only, not Lucene.
 */
@Repository("nameDateFormfilter")
public class NameDateFilterImpl implements NameDateFilter {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired SessionFactory sf;
  private @Autowired RecordDao recordDao;

  public NameDateFilterImpl() {}

  /* (non-Javadoc)
   * @see com.researchspace.dao.hibernate.NameDateFilter#match(com.researchspace.webapp.controller.data.WorkspaceSearchInput)
   */
  @Override
  @SuppressWarnings("unchecked")
  public ISearchResults<BaseRecord> match(WorkspaceListingConfig input) {

    String[] options = input.getSrchOptions();
    String[] terms = input.getSrchTerms();
    if (options.length == 0 || terms.length == 0) {
      throw new IllegalArgumentException(" Missing search term or search option");
    }

    List<String> pname = new ArrayList<String>();
    List<Object> pval = new ArrayList<Object>();

    if (SearchConstants.FORM_SEARCH_OPTION.equals(options[0])) {

      List<RSForm> rc = searchDBForForms(terms[0]);
      // if no forms match search term, there can't be any results
      if (rc.isEmpty()) {
        return new SearchResultsImpl<BaseRecord>(Collections.EMPTY_LIST, 0, 0L);
      }

      List<Long> formIds =
          rc.stream().map(new ObjectToIdPropertyTransformer()).collect(Collectors.toList());
      return filterStrucDocsOnly(input, pname, pval, formIds);
    }

    String q1 = generateCountQueryString(input, pname, pval);

    Query<BigInteger> countQuery = sf.getCurrentSession().createNativeQuery(q1);
    applyNamedParameterToQuery(countQuery, pname, pval);

    Long totalHits = countQuery.uniqueResult().longValue();

    List<String> pname2 = new ArrayList<String>();
    List<Object> pval2 = new ArrayList<Object>();
    String q2 = generateRetrieveQueryString(input, pname2, pval2);

    PaginationCriteria<BaseRecord> pgCrit = input.getPgCrit();
    Query<Object> retrievequery = sf.getCurrentSession().createNativeQuery(q2);
    retrievequery.setFirstResult(pgCrit.getFirstResultIndex());
    retrievequery.setMaxResults(pgCrit.getResultsPerPage());

    applyNamedParameterToQuery(retrievequery, pname2, pval2);
    List<Object> records = retrievequery.list();
    List<BaseRecord> rc = recordDao.loadRecordFromResults(records);

    ISearchResults<BaseRecord> results =
        new SearchResultsImpl<BaseRecord>(
            rc, pgCrit.getPageNumber().intValue(), totalHits, pgCrit.getResultsPerPage());
    return results;
  }

  @SuppressWarnings("rawtypes")
  private void applyNamedParameterToQuery(
      Query queryObject, List<String> paramNames, List<Object> values) throws HibernateException {
    for (int c = 0; c < paramNames.size(); c++) {
      Object value = values.get(c);
      if (value instanceof Collection) {
        queryObject.setParameterList(paramNames.get(c), (Collection) value);
      } else if (value instanceof Object[]) {
        queryObject.setParameterList(paramNames.get(c), (Object[]) value);
      } else {
        queryObject.setParameter(paramNames.get(c), value);
      }
    }
  }

  private ISearchResults<BaseRecord> filterStrucDocsOnly(
      WorkspaceListingConfig input, List<String> pname, List<Object> pval, List<Long> formIds) {
    String fromquery = generateFilterStrucDocByFormQuery(input, pname, pval, formIds);
    int pageSize = input.getPgCrit().getResultsPerPage(); // as default
    int pageNumber = input.getPgCrit().getPageNumber().intValue();

    int firstResult = pageNumber * pageSize;
    int maxResults = pageSize;

    String countQuery = "select count(r) " + fromquery;
    Query<Long> q = sf.getCurrentSession().createQuery(countQuery, Long.class);
    applyNamedParameterToQuery(q, pname, pval);

    Long numHits = q.uniqueResult();
    String getQuery = "select r " + fromquery;
    Query<BaseRecord> q2 = sf.getCurrentSession().createQuery(getQuery, BaseRecord.class);
    applyNamedParameterToQuery(q2, pname, pval);
    q2.setFirstResult(firstResult);
    q2.setMaxResults(maxResults);
    List<BaseRecord> records = q2.list();
    return new SearchResultsImpl<BaseRecord>(records, pageNumber, numHits, maxResults);
    // return null;
  }

  protected String generateFilterStrucDocByFormQuery(
      WorkspaceListingConfig input, List<String> pname, List<Object> pval, List<Long> formIds) {
    String term = input.getSrchTerms()[0];
    StringBuffer sbf = new StringBuffer();
    sbf.append(" from StructuredDocument r join r.parents flders");
    sbf.append(" where flders.folder.id = :parentId and flders.recordInFolderDeleted=:deleted ");
    pname.add("parentId");
    pname.add("deleted");

    pval.add(input.getParentFolderId());
    pval.add(Boolean.FALSE);

    addForm(pname, pval, term, sbf, formIds);

    addOrderBy(sbf, input.getPgCrit());
    return sbf.toString();
  }

  private void addOrderBy(StringBuffer sbf, PaginationCriteria<BaseRecord> pgCrit) {
    String orderBy = null;
    SortOrder so = null;
    if (pgCrit != null && pgCrit.getOrderBy() != null) {
      orderBy = pgCrit.getOrderBy();
      so = pgCrit.getSortOrder();
      sbf.append(" order by " + orderBy + "  " + so);
    }
  }

  protected String generateRetrieveQueryString(
      WorkspaceListingConfig input, List<String> pname, List<Object> pval) {
    StringBuffer sb = new StringBuffer();
    pname.add("parentId");
    pname.add("deleted");

    pval.add(input.getParentFolderId());
    pval.add(Boolean.FALSE);
    final String select =
        "select rc.id as id, rc.name as name, "
            + "rc.type as type, rc.modificationDate as modificationDate from";
    sb.append(select).append(" BaseRecord rc, RecordToFolder rtf");
    pname.add("parentId");
    pname.add("deleted");

    pval.add(input.getParentFolderId());
    pval.add(Boolean.FALSE);
    sb.append(makeFromClauseForNameDateSrc(input, pname, pval));
    return sb.toString();
  }

  protected String generateCountQueryString(
      WorkspaceListingConfig input, List<String> pname, List<Object> pval) {
    StringBuffer sb = new StringBuffer();
    pname.add("parentId");
    pname.add("deleted");

    pval.add(input.getParentFolderId());
    pval.add(Boolean.FALSE);
    String countSQL2 = " select count(rc.id) from  BaseRecord rc join RecordToFolder rtf";
    sb.append(countSQL2);
    pname.add("parentId");
    pname.add("deleted");

    pval.add(input.getParentFolderId());
    pval.add(Boolean.FALSE);
    String from2 = makeFromClauseForNameDateSrc(input, pname, pval);
    sb.append(from2);

    String query = sb.toString();
    query = query.replaceAll(" name", " rc.name");
    query = query.replaceAll("([^:])modificationDate", "$1rc.modificationDate");
    return query;
  }

  private int parseDateStr(String out[], String dst) {
    int rst = 0;

    StringTokenizer tk = new StringTokenizer(dst, ",");
    rst++;
    out[0] = tk.nextToken().trim();
    if (tk.hasMoreTokens()) {
      out[1] = tk.nextToken().trim();
      rst++;
    }
    return rst;
  }

  private String makeFromClauseForNameDateSrc(
      WorkspaceListingConfig input, List<String> pname, List<Object> pval) {

    StringBuffer sbf = new StringBuffer();
    sbf.append(
        " where  rtf.record_id=rc.id and rtf.folder_id=:parentId "
            + "and rtf.recordInFolderDeleted=:deleted and rc.deleted=false");
    if (SearchConstants.NAME_SEARCH_OPTION.equals(input.getSrchOptions()[0])) {
      addName(pname, pval, input.getSrchTerms()[0], sbf);
    } else if (SearchConstants.MODIFICATION_DATE_SEARCH_OPTION.equals(input.getSrchOptions()[0])) {
      addDate(pname, pval, input.getSrchTerms()[0], sbf);
    } else if (SearchConstants.CREATION_DATE_SEARCH_OPTION.equals(input.getSrchOptions()[0])) {
      addCreationDate(pname, pval, input.getSrchTerms()[0], sbf);
    }

    addOrderBy(sbf, input.getPgCrit());
    return sbf.toString();
  }

  private void addDate(List<String> pname, List<Object> pval, String date, StringBuffer sbf) {

    String out[] = new String[2];
    int sz = parseDateStr(out, date);
    if (sz == 1) {
      int len = out[0].length();
      char c1 = out[0].charAt(len - 1);
      if (c1 == '-') {
        out[0] = out[0].substring(0, len - 2);
        sbf.append(" and modificationDate< :modificationDate ");
      } else {
        sbf.append(" and modificationDate> :modificationDate ");
      }

      pname.add("modificationDate");
      pval.add(out[0]);
    } else {
      sbf.append(" and (modificationDate> :modificationDate1 ");
      sbf.append(" and modificationDate< :modificationDate2) ");
      pname.add("modificationDate1");
      pval.add(out[0]);
      pname.add("modificationDate2");
      pval.add(out[1]);
    }
  }

  private void addCreationDate(
      List<String> pname, List<Object> pval, String date, StringBuffer sbf) {

    String out[] = new String[2];
    int sz = parseDateStr(out, date);
    if (sz == 1) {
      int len = out[0].length();
      char c1 = out[0].charAt(len - 1);
      if (c1 == '-') {
        out[0] = out[0].substring(0, len - 2);
        sbf.append(" and creationDate< :creationDate ");
      } else {
        sbf.append(" and creationDate> :creationDate ");
      }

      pname.add("creationDate");
      pval.add(out[0]);
    } else {
      sbf.append(" and (creationDate> :creationDate1 ");
      sbf.append(" and creationDate< :creationDate2) ");
      pname.add("creationDate1");
      pval.add(out[0]);
      pname.add("creationDate2");
      pval.add(out[1]);
    }
  }

  private void addForm(
      List<String> pname, List<Object> pval, String term, StringBuffer sbf, List<Long> formIds) {

    if (formIds != null && formIds.size() > 0) {
      sbf.append("and form_id in (:forms)  ");
      pname.add("forms");
      pval.add(formIds);
    }
  }

  @SuppressWarnings("unchecked")
  private List<RSForm> searchDBForForms(String searchTerm) {
    Criteria c = sf.getCurrentSession().createCriteria(RSForm.class);
    c.add(Restrictions.eq("publishingState", FormState.PUBLISHED));
    if (searchTerm.endsWith(SearchConstants.WILDCARD)) {
      searchTerm = StringUtils.removeEnd(searchTerm, SearchConstants.WILDCARD);
      c.add(Restrictions.like("editInfo.name", "%" + searchTerm + "%").ignoreCase());
    } else {
      c.add(Restrictions.eq("editInfo.name", searchTerm).ignoreCase());
    }

    return c.list();
  }

  private void addName(List<String> pname, List<Object> pval, String term, StringBuffer sbf) {
    if (term.endsWith(SearchConstants.WILDCARD)) {
      term = StringUtils.removeEnd(term, SearchConstants.WILDCARD);
      sbf.append(" and name like :name  ");
      pname.add("name");
      term = "%" + term + "%"; // add % to value
      pval.add(term);
    } else {
      sbf.append(" and name like :name  ");
      pname.add("name");
      pval.add(term);
    }
  }
}
