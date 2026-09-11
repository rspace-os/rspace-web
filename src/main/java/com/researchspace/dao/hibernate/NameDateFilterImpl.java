package com.researchspace.dao.hibernate;

import com.axiope.search.SearchConstants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.NameDateFilter;
import com.researchspace.dao.RecordDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.sort.RecordSort;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Simple filter mechanism to filter a folder's contents by name or date <br>
 * This uses database search only, not Lucene.
 */
@Repository("nameDateFormfilter")
public class NameDateFilterImpl implements NameDateFilter {

  private @Autowired SessionFactory sf;
  private @Autowired RecordDao recordDao;

  public NameDateFilterImpl() {}

  /* (non-Javadoc)
   * @see com.researchspace.dao.hibernate.NameDateFilter#match(com.researchspace.webapp.controller.data.WorkspaceSearchInput)
   */
  @Override
  public ISearchResults<BaseRecord> match(WorkspaceListingConfig input) {

    String[] options = input.getSrchOptions();
    String[] terms = input.getSrchTerms();
    if (options.length == 0 || terms.length == 0) {
      throw new IllegalArgumentException(" Missing search term or search option");
    }

    List<String> pname = new ArrayList<String>();
    List<Object> pval = new ArrayList<Object>();

    String q1 = generateCountQueryString(input, pname, pval);

    Query<Long> countQuery = sf.getCurrentSession().createNativeQuery(q1, Long.class);
    applyNamedParameterToQuery(countQuery, pname, pval);

    Long totalHits = countQuery.uniqueResult().longValue();

    List<String> pname2 = new ArrayList<String>();
    List<Object> pval2 = new ArrayList<Object>();
    String q2 = generateRetrieveQueryString(input, pname2, pval2);

    PaginationCriteria<BaseRecord> pgCrit = input.getPgCrit();
    Query<Object> retrievequery = sf.getCurrentSession().createNativeQuery(q2, Object.class);
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

  private void applyNamedParameterToQuery(
      Query<?> queryObject, List<String> paramNames, List<Object> values)
      throws HibernateException {
    for (int c = 0; c < paramNames.size(); c++) {
      Object value = values.get(c);
      if (value instanceof Collection<?> collection) {
        queryObject.setParameterList(paramNames.get(c), collection);
      } else if (value instanceof Object[]) {
        queryObject.setParameterList(paramNames.get(c), (Object[]) value);
      } else {
        queryObject.setParameter(paramNames.get(c), value);
      }
    }
  }

  private void addOrderBy(
      StringBuffer sbf, PaginationCriteria<BaseRecord> pgCrit, String fallbackOrderBy) {
    if (pgCrit == null) {
      return;
    }
    RecordSort sort = RecordSort.fromRequest(pgCrit.getOrderBy());
    switch (sort) {
      case NAME:
      case CREATION_DATE:
      case CREATION_DATE_MILLIS:
      case MODIFICATION_DATE:
      case MODIFICATION_DATE_MILLIS:
        // these queries select the sort columns under their own names
        sbf.append(" order by " + sort.key() + "  " + pgCrit.getSortOrder());
        break;
      default:
        if (fallbackOrderBy != null) {
          sbf.append(" order by ").append(fallbackOrderBy).append(" ASC");
        }
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
    sb.append(makeFromClauseForNameDateSrc(input, pname, pval, "rc.id"));
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
    String from2 = makeFromClauseForNameDateSrc(input, pname, pval, null);
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
      WorkspaceListingConfig input, List<String> pname, List<Object> pval, String fallbackOrderBy) {

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

    addOrderBy(sbf, input.getPgCrit(), fallbackOrderBy);
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
