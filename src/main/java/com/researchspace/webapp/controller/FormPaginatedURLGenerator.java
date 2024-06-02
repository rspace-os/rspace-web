package com.researchspace.webapp.controller;

import com.researchspace.core.util.AbstractURLPaginator;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.views.FormSearchCriteria;
import org.apache.commons.lang.StringUtils;

public class FormPaginatedURLGenerator extends AbstractURLPaginator {

  private FormSearchCriteria sc;

  /**
   * @param sc Optional search criteria, can be <code>null</code>.
   * @param pc Optional pagination criteria, can be <code>null</code>.
   */
  public FormPaginatedURLGenerator(FormSearchCriteria sc, PaginationCriteria<RSForm> pc) {
    super(pc);
    this.sc = sc;
  }

  /**
   * /ajax/template/list?nameLike=xxx&pageNumber=xxx&resultsPerPage=xxx&
   * sortOrder=ASC&orderBy=fieldName
   */
  @Override
  public String generateURL(final int pageNum) {
    String path = "/workspace/editor/form/ajax/list";
    StringBuffer sb = new StringBuffer();
    boolean needsAnd = false;
    if (sc != null) {
      if (!StringUtils.isBlank(sc.getSearchTerm())) {
        sb.append("searchTerm=" + sc.getSearchTerm());
        needsAnd = true;
      }
    }
    if (pgCrit != null) {
      if (needsAnd) {
        sb.append("&");
      }
      sb.append(pgCrit.toURLQueryString(pageNum));
    }
    // use URI constructors to escape properly
    String query = sb.toString();
    return getEscapedURL(path, query);
  }

  @Override
  public String generateURLForCurrentPage(int pageNum) {
    return "#";
  }
}
