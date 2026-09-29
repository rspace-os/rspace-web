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
import com.researchspace.model.sort.RecordSort;
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
    String column;
    switch (RecordSort.fromRequest(pg.getOrderBy())) {
      case NAME:
        column = "doc.editInfo.name";
        break;
      case CREATION_DATE:
      case CREATION_DATE_MILLIS:
        column = "doc.editInfo.creationDate";
        break;
      case MODIFICATION_DATE:
      case MODIFICATION_DATE_MILLIS:
      default:
        column = "doc.editInfo.modificationDate";
        break;
    }
    String direction = SortOrder.ASC.equals(pg.getSortOrder()) ? "asc" : "desc";
    return " order by " + column + " " + direction;
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
