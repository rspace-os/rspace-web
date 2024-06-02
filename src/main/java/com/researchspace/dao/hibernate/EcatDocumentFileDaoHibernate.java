package com.researchspace.dao.hibernate;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.EcatDocumentFileDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.dto.DocAttachmentSummaryInfo;
import com.researchspace.model.record.BaseRecord;
import java.util.List;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

@Repository("ecatDocumentFileDao")
public class EcatDocumentFileDaoHibernate extends GenericDaoHibernate<EcatDocumentFile, Long>
    implements EcatDocumentFileDao {

  public EcatDocumentFileDaoHibernate() {
    super(EcatDocumentFile.class);
  }

  @Override
  public ISearchResults<BaseRecord> getEcatDocumentFileByURI(
      PaginationCriteria<BaseRecord> pg, String user, List<String> fileURIList) {

    Session session = getSession();
    // we remove '/' and '\\' chars here so the search will work regardless of the file separator
    // used.

    long totalHits = getTotalEcatDocumentFileByURIhql(user, fileURIList, session);

    List<BaseRecord> lst = getEcatDocumentFileByURIhql(user, fileURIList, pg, session);

    return new SearchResultsImpl<>(
        lst, pg.getPageNumber().intValue(), totalHits, pg.getResultsPerPage());
  }

  static final String BASIC_QUERY =
      "from EcatDocumentFile doc where "
          + "doc.deleted=:deleted and "
          + "doc.owner.username=:username and "
          + "replace(replace(doc.fileProperty.relPath,'/',''),'\\\\','') in :fileList";

  private List<BaseRecord> getEcatDocumentFileByURIhql(
      String user, List<String> fileURIList, PaginationCriteria<BaseRecord> pg, Session session) {

    String orderBySuffix = generateOrderBy(pg);
    String fullQuery = BASIC_QUERY + orderBySuffix;
    return session
        .createQuery(fullQuery, BaseRecord.class)
        .setParameterList("fileList", fileURIList)
        .setParameter("deleted", false)
        .setParameter("username", user)
        .setMaxResults(pg.getResultsPerPage())
        .setFirstResult(pg.getFirstResultIndex())
        .list();
  }

  private String generateOrderBy(PaginationCriteria<BaseRecord> pg) {
    String orderBy = pg.getOrderBy();
    SortOrder sortOrder = pg.getSortOrder();
    String orderBySuffix = "";
    if (orderBy != null) {
      if (orderBy.equalsIgnoreCase("name")) {
        if (isAsc(sortOrder)) {
          orderBySuffix = " order by doc.editInfo.name asc";
        } else if (isDesc(sortOrder)) {
          orderBySuffix = " order by doc.editInfo.name desc";
        }
      } else if (orderBy.equalsIgnoreCase("modificationDateMillis")) {
        if (isAsc(sortOrder)) {
          orderBySuffix = " order by doc.editInfo.modificationDate asc";
        } else if (isDesc(sortOrder)) {
          orderBySuffix = " order by doc.editInfo.modificationDate desc";
        }
      } else if (orderBy.equalsIgnoreCase("creationDateMillis")) {
        if (isAsc(sortOrder)) {
          orderBySuffix = " order by doc.editInfo.creationDate asc";
        } else if (isDesc(sortOrder)) {
          orderBySuffix = " order by doc.editInfo.creationDate desc";
        }
      }
    }
    return orderBySuffix;
  }

  private boolean isDesc(SortOrder sortOrder) {
    return SortOrder.DESC.equals(sortOrder);
  }

  private boolean isAsc(SortOrder sortOrder) {
    return SortOrder.ASC.equals(sortOrder);
  }

  private long getTotalEcatDocumentFileByURIhql(
      final String user, final List<String> fileURIList, Session session) {
    String fullQuery = "select count(doc.id) " + BASIC_QUERY;
    return (Long)
        session
            .createQuery(fullQuery)
            .setParameterList("fileList", fileURIList)
            .setParameter("deleted", false)
            .setParameter("username", user)
            .uniqueResult();
  }

  @Override
  public DocAttachmentSummaryInfo getSummaryInfo(Long id) {
    return getSession()
        .createQuery(
            "select br.id, br.name, br.type, emf.version,  from BaseRecord br"
                + " inner join EcatMediaFile emf inner join EcatDocumentFile ",
            DocAttachmentSummaryInfo.class)
        .uniqueResult();
  }
}
