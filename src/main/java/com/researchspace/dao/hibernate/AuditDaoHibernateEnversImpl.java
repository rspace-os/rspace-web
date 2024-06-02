package com.researchspace.dao.hibernate;

import com.researchspace.core.util.DateRange;
import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.AuditDao;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.dtos.RevisionSearchCriteria;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.exception.AuditException;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.hibernate.envers.query.criteria.AuditDisjunction;
import org.hibernate.envers.query.criteria.MatchMode;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository("auditDao")
public class AuditDaoHibernateEnversImpl implements AuditDao {

  public static final String DELETED = "deleted";
  public static final String EDIT_INFO_MODIFIED_BY = "editInfo_modifiedBy";
  public static final String EDIT_INFO_MODIFICATION_DATE = "editInfo_modificationDate";
  private @Autowired SessionFactory sessionFactory;

  private final Logger log = LoggerFactory.getLogger(getClass());

  /*
   * Utility method to get the AuditReader which provides access to Envers API.
   */
  private AuditReader getAuditReader() {
    return AuditReaderFactory.get(sessionFactory.getCurrentSession());
  }

  /** Gets a list of deleted documents - all data inside the SDs will be null, except for the ID */
  public ISearchResults<AuditedRecord> getRestorableDeletedRecords(
      User user, String searchTerm, PaginationCriteria<AuditedRecord> pgCrit) {

    AuditReader auditReader = getAuditReader();
    String orderBy;
    if (!StringUtils.isEmpty(pgCrit.getOrderBy())) {
      orderBy = " order by " + pgCrit.getOrderBy() + " " + pgCrit.getSortOrder();
    } else {
      orderBy = " order by deletedDate " + SortOrder.DESC;
    }

    // Query for all user's deleted folders; we will then exclude their contents from the final
    // results as only 'top level' deleted items can be restored
    AuditQuery folderIdsQuery =
        auditReader
            .createQuery()
            .forRevisionsOfEntity(Folder.class, false, true)
            .add(AuditEntity.property(DELETED).eq(true))
            .add(AuditEntity.property("owner").eq(user))
            .addProjection(AuditEntity.id());
    List<Long> allDeletedFolderIDs = folderIdsQuery.getResultList();
    List<Long> deletedFolderContentsIDsToExclude =
        getContentsIDsFromDeletedFolders(allDeletedFolderIDs);
    if (searchTerm == null) {
      searchTerm = "";
    }
    boolean excludedIDsExist = !deletedFolderContentsIDsToExclude.isEmpty();
    Query<Long> countRecordsQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count (id) "
                    + "from RecordToFolder rtf "
                    + "where rtf.record.editInfo.name like :searchTerm "
                    + "and recordInFolderDeleted=true "
                    + "and userName=:username"
                    + (excludedIDsExist ? " and not rtf.record.id in (:ids)" : " "),
                Long.class);
    countRecordsQuery.setParameter("username", user.getUsername());
    countRecordsQuery.setParameter("searchTerm", "%" + searchTerm + "%");
    if (excludedIDsExist) {
      countRecordsQuery.setParameter("ids", deletedFolderContentsIDsToExclude);
    }
    Long count = countRecordsQuery.uniqueResult();

    Query<RecordToFolder> recordsQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from RecordToFolder rtf "
                    + "where rtf.record.editInfo.name like :searchTerm "
                    + "and recordInFolderDeleted=true "
                    + "and userName=:username"
                    + (excludedIDsExist ? " and not rtf.record.id in (:ids) " : " ")
                    + orderBy,
                RecordToFolder.class);
    recordsQuery.setParameter("username", user.getUsername());
    recordsQuery.setParameter("searchTerm", "%" + searchTerm + "%");
    if (excludedIDsExist) {
      recordsQuery.setParameter("ids", deletedFolderContentsIDsToExclude);
    }
    recordsQuery
        .setFirstResult(pgCrit.getPageNumber().intValue() * pgCrit.getResultsPerPage())
        .setMaxResults(pgCrit.getResultsPerPage());

    List<RecordToFolder> deletedRecordToFolder = recordsQuery.list();
    List<AuditedRecord> deletedAudRecords = processRecordToFolderResults(deletedRecordToFolder);
    return new SearchResultsImpl<>(deletedAudRecords, pgCrit, count);
  }

  private List<Long> getContentsIDsFromDeletedFolders(List<Long> deletedFolderIds) {
    // get Records that match deleted folder IDs
    Query<Long> recordsQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select rtf.record.id from RecordToFolder rtf where rtf.folder.id in (:ids)  and"
                    + " recordInFolderDeleted=true ",
                Long.class);
    recordsQuery.setParameter("ids", deletedFolderIds);
    return recordsQuery.list();
  }

  public int updateDeletedFolderAsRestored(Long folderId) {
    AuditReader ar = getAuditReader();
    int totalUpdated = 0;
    AuditQuery query =
        ar.createQuery()
            .forRevisionsOfEntity(Folder.class, false, true)
            .add(AuditEntity.revisionType().eq(RevisionType.MOD))
            .add(AuditEntity.id().eq(folderId))
            .add(AuditEntity.property(DELETED).eq(true));
    List<AuditedRecord> allDeleted = new ArrayList<>();
    List<AuditedRecord> deletedASD = processRecordResults(query.getResultList());
    allDeleted.addAll(deletedASD);
    for (AuditedRecord del : allDeleted) {
      NativeQuery<?> q2 =
          sessionFactory
              .getCurrentSession()
              .createNativeQuery(
                  "update "
                      + "BaseRecord_AUD "
                      + " set deleted=:deleted where REV=:REV and id=:id");
      q2.setParameter(DELETED, false);
      q2.setParameter("REV", del.getRevision().intValue());
      q2.setParameter("id", del.getRecord().getId());
      int updated = q2.executeUpdate();
      totalUpdated += updated;
    }
    return totalUpdated;
  }

  @SuppressWarnings("unchecked")
  public List<AuditedRecord> getRevisionsForDocument(
      StructuredDocument doc, PaginationCriteria<AuditedRecord> pgCrit) {
    AuditReader auditReader = getAuditReader();

    List<AuditedRecord> auditedRecords = new ArrayList<>();
    Long id = doc.getId();

    int maxResults = Integer.MAX_VALUE;
    int firstResult = 0;
    if (pgCrit != null) {
      maxResults = pgCrit.getResultsPerPage();
      firstResult = pgCrit.getFirstResultIndex();
    }

    AuditQuery auditQuery =
        auditReader
            .createQuery()
            .forRevisionsOfEntity(StructuredDocument.class, false, true)
            .addProjection(AuditEntity.revisionNumber())
            .add(AuditEntity.id().eq(id))
            .setFirstResult(firstResult)
            .setMaxResults(maxResults);

    List<Number> revisions;

    if (pgCrit != null && pgCrit.getSearchCriteria() != null) {
      FilterCriteria searchCriteria = pgCrit.getSearchCriteria();
      try {
        AuditQuery withSearchCriteria = addSearchCriteria(searchCriteria, auditQuery);
        revisions = withSearchCriteria.getResultList();
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        return logAndReturnEmptyList(e);
      }
    } else {
      revisions = auditQuery.getResultList();
    }

    for (Number revisionNumber : revisions) {
      StructuredDocument sd =
          auditReader.find(StructuredDocument.class, doc.getId(), revisionNumber);
      // initialise for perm checking
      sd.getForm().getName();
      sd.getOwner().getUsername();
      auditedRecords.add(new AuditedRecord(sd, revisionNumber));
    }

    return auditedRecords;
  }

  private static AuditQuery addSearchCriteria(FilterCriteria searchCriteria, AuditQuery auditQuery)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Map<String, Object> key2Value = searchCriteria.getSearchTermField2Values();
    for (Entry<String, Object> entry : key2Value.entrySet()) {
      if ("modifiedBy".equals(entry.getKey())) {
        auditQuery.add(AuditEntity.property(EDIT_INFO_MODIFIED_BY).eq(entry.getValue()));

      } else if ("dateRange".equals(entry.getKey())
          && !StringUtils.isBlank((String) entry.getValue())) {
        DateRange dateRange = DateRange.parse((String) entry.getValue());
        if (dateRange.getFromDate() != null) {
          auditQuery.add(
              AuditEntity.property(EDIT_INFO_MODIFICATION_DATE).ge(dateRange.getFromDate()));
        }
        if (dateRange.getToDate() != null) {
          auditQuery.add(
              AuditEntity.property(EDIT_INFO_MODIFICATION_DATE).le(dateRange.getToDate()));
        }

      } else if ("selectedFields".equals(entry.getKey()) && entry.getValue() != null) {
        AuditDisjunction ad = AuditEntity.disjunction();
        // these are 'or'd together.
        String[] values = (String[]) entry.getValue();
        for (String fieldName : values) {
          ad.add(AuditEntity.property("delta_deltaString").like(fieldName, MatchMode.ANYWHERE));
        }
        auditQuery.add(ad);
      }
    }
    return auditQuery;
  }

  @Override
  public List<AuditedEntity<StructuredDocument>> getRevisionsForDocumentVersion(
      Long docId, Number userVersion) {
    AuditReader ar = getAuditReader();
    AuditQuery q =
        ar.createQuery()
            .forRevisionsOfEntity(StructuredDocument.class, false, false)
            .add(AuditEntity.id().eq(docId))
            .add(AuditEntity.property("userVersion").eq(new Version(userVersion)))
            .addOrder(AuditEntity.revisionNumber().desc());
    return processGenericResults(q.getResultList());
  }

  @Override
  public List<AuditedEntity<EcatMediaFile>> getRevisionsForMediaFileVersion(
      Long mediaId, Number version) {
    AuditReader ar = getAuditReader();
    AuditQuery q =
        ar.createQuery()
            .forRevisionsOfEntity(EcatMediaFile.class, false, false)
            .add(AuditEntity.id().eq(mediaId))
            .add(AuditEntity.property("version").eq(version))
            .addOrder(AuditEntity.revisionNumber().desc());
    return processGenericResults(q.getResultList());
  }

  private List<AuditedRecord> logAndReturnEmptyList(Exception e) {
    log.error(
        "Problem handling search criteria; returning empty result list. " + e.getMessage(), e);
    return Collections.emptyList();
  }

  // returns new list with records in the same order as in the results
  private List<AuditedRecord> processRecordToFolderResults(List<RecordToFolder> results) {
    List<AuditedRecord> rc = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      RecordToFolder rtf = results.get(i);
      // run in try-catch to ameliorate RSPAC-1401
      try {
        AuditedEntity<BaseRecord> rec =
            getNewestRevisionForEntity(BaseRecord.class, rtf.getRecord().getId());
        rc.add(
            new AuditedRecord(
                rec.getEntity(),
                rec.getRevision().intValue(),
                rec.getRevType(),
                rtf.getDeletedDate()));
      } catch (AuditException auditException) {
        log.error(
            "Record with id {} could not be loaded, omitting from results - RSPAC-1401?: {}",
            rtf.getRecord().getId(),
            auditException.getMessage());
      } catch (DataAccessException auditException) {
        log.error(
            "Record with id {} could not be loaded due to data access exception, omitting from"
                + " results - RSPAC-1401?: {}",
            rtf.getRecord().getId(),
            auditException.getMessage());
      } catch (Exception e) { // catchall.
        log.error(
            "Record with id {} could not be loaded, omitting from results. {}",
            rtf.getRecord().getId(),
            e.getMessage());
      }
    }
    return rc;
  }

  @SuppressWarnings("rawtypes")
  private List<AuditedRecord> processRecordResults(List results) {
    List<AuditedRecord> rc = new ArrayList<>();

    for (int i = 0; i < results.size(); i++) {
      Object[] row = (Object[]) results.get(i);
      BaseRecord recordOrFolder = (BaseRecord) row[0];
      DefaultRevisionEntity dre = (DefaultRevisionEntity) row[1];

      RevisionType revType = (RevisionType) row[2];
      rc.add(
          new AuditedRecord(recordOrFolder, dre.getId(), revType, recordOrFolder.getDeletedDate()));
    }
    return rc;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private <T> List<AuditedEntity<T>> processGenericResults(List results) {
    List<AuditedEntity<T>> rc = new ArrayList<>();

    for (Object result : results) {
      Object[] row = (Object[]) result;
      T entity = (T) row[0];
      DefaultRevisionEntity dre = (DefaultRevisionEntity) row[1];

      RevisionType revType = (RevisionType) row[2];
      rc.add(new AuditedEntity<>(entity, dre.getId(), revType));
    }
    return rc;
  }

  public AuditedRecord getDocumentForRevision(StructuredDocument doc, Number revision) {
    AuditReader ar = getAuditReader();
    StructuredDocument sd = ar.find(StructuredDocument.class, doc.getId(), revision);
    sd.setForm(doc.getForm());
    return new AuditedRecord(sd, revision);
  }

  @Override
  public AuditedRecord getAttachmentForRevision(long mediaFileId, Number revision) {
    AuditReader ar = getAuditReader();

    EcatDocumentFile media = ar.find(EcatDocumentFile.class, mediaFileId, revision);
    // initialise collection
    media.getLinkedFields().size();
    for (FieldAttachment field : media.getLinkedFields()) {
      field.getClass();
    }
    return new AuditedRecord(media, revision);
  }

  @Override
  public Integer getRevisionCountForDocument(StructuredDocument doc, RevisionSearchCriteria sc) {

    AuditReader ar = getAuditReader();
    AuditQuery q =
        ar.createQuery()
            .forRevisionsOfEntity(StructuredDocument.class, false, true)
            .addProjection(AuditEntity.revisionNumber().count())
            .add(AuditEntity.id().eq(doc.getId()));

    if (sc != null) {
      if (sc.getModifiedBy() != null) {
        q.add(AuditEntity.property(EDIT_INFO_MODIFIED_BY).eq(sc.getModifiedBy()));
      }
      if (sc.getDateRange() != null) {
        DateRange dateRange = DateRange.parse(sc.getDateRange());
        if (dateRange.getFromDate() != null) {
          q.add(AuditEntity.property(EDIT_INFO_MODIFICATION_DATE).ge(dateRange.getFromDate()));
        }
        if (dateRange.getToDate() != null) {
          q.add(AuditEntity.property(EDIT_INFO_MODIFICATION_DATE).le(dateRange.getToDate()));
        }
      }
      if (sc.getSelectedFields() != null) {
        AuditDisjunction ad = AuditEntity.disjunction();
        // these are 'or'd together.
        for (String fieldName : sc.getSelectedFields()) {
          ad.add(AuditEntity.property("delta_deltaString").like(fieldName, MatchMode.ANYWHERE));
        }
        q.add(ad);
      }
    }
    return ((Number) q.getSingleResult()).intValue();
  }

  @Override
  public <T> List<AuditedEntity<T>> getRevisionsForObject(Class<T> cls, Long primaryKey) {
    AuditReader ar = getAuditReader();
    AuditQuery q =
        ar.createQuery()
            .forRevisionsOfEntity(cls, false, false)
            .add(AuditEntity.id().eq(primaryKey));
    return processGenericResults(q.getResultList());
  }

  @Override
  public <T> AuditedEntity<T> getObjectForRevision(Class<T> cls, Long primaryKey, Number revision) {
    AuditReader ar = getAuditReader();
    T entity = ar.find(cls, primaryKey, revision.intValue());
    if (entity == null) {
      return null;
    }
    return new AuditedEntity<>(entity, revision);
  }

  @SuppressWarnings({"rawtypes"})
  public <T> AuditedEntity<T> getNewestRevisionForEntity(Class<T> clazz, Long objectId) {
    AuditReader ar = getAuditReader();
    List results =
        ar.createQuery()
            .forRevisionsOfEntity(clazz, false, false)
            .add(AuditEntity.id().eq(objectId))
            .addOrder(AuditEntity.revisionNumber().desc())
            .getResultList();

    List<AuditedEntity<T>> processed = processGenericResults(results);
    if (processed.isEmpty()) {
      return null; // should never happen except into tests
    } else {
      return processed.get(0);
    }
  }

  @SuppressWarnings({"unchecked"})
  @Override
  public List<EcatCommentItem> getCommentItemsForCommentAtDocumentRevision(
      Long commentId, Integer revision) {
    AuditReader ar = getAuditReader();
    /* all comment items at or before this revision */
    return ar.createQuery()
        .forRevisionsOfEntity(EcatCommentItem.class, true, false)
        .add(AuditEntity.property("comId").eq(commentId))
        .add(AuditEntity.revisionNumber().le(revision))
        .getResultList();
  }

  /* ============================
   *  test helper methods below
   * ============================ */

  @SuppressWarnings("rawtypes")
  public List<StructuredDocument> getEveryDocumentAndRevisionModifiedByUser(User user) {
    AuditReader ar = getAuditReader();

    AuditQuery query =
        ar.createQuery()
            .forRevisionsOfEntity(StructuredDocument.class, false, true)
            .add(AuditEntity.property(EDIT_INFO_MODIFIED_BY).eq(user.getUsername()));

    List results = query.getResultList();
    List<StructuredDocument> rc = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      Object[] row = (Object[]) results.get(i);
      StructuredDocument sd = (StructuredDocument) row[0];
      rc.add(sd);
    }
    return rc;
  }

  public Collection<BaseRecord> getRecordsToArchive(final int maxToKeep) {
    List<BaseRecord> results = new ArrayList<>();
    List<AuditedRecord> allresults = getRecordsToArchiveInternal(maxToKeep);
    for (AuditedRecord asd : allresults) {
      results.add(asd.getRecord());
    }
    return results;
  }

  @Override
  public int deleteOldArchives(int numToKeep) {
    int totalnumDeleted = 0;
    List<AuditedRecord> allresults = getRecordsToArchiveInternal(numToKeep);
    Session sess = sessionFactory.getCurrentSession();

    for (AuditedRecord asd : allresults) {
      NativeQuery<?> deleteOld =
          sess.createNativeQuery(
              "delete  from StructuredDocument_AUD where id =:id and REV = :REV");

      deleteOld.setParameter("id", asd.getRecord().getId());
      deleteOld.setParameter("REV", asd.getRevision().intValue());
      int deleted = deleteOld.executeUpdate();
      totalnumDeleted += deleted;

      NativeQuery<?> deleteOldFields =
          sess.createNativeQuery(
              "delete  from Field_AUD where structuredDocument_id =:id and REV = :REV");

      deleteOldFields.setParameter("id", asd.getRecord().getId());
      deleteOldFields.setParameter("REV", asd.getRevision().intValue());
    }

    return totalnumDeleted;
  }

  @SuppressWarnings("rawtypes")
  private List<AuditedRecord> getRecordsToArchiveInternal(int maxToKeep) {
    List<AuditedRecord> allresults = new ArrayList<>();
    String distinctIdsQuery = "select distinct id from StructuredDocument_AUD";
    NativeQuery getIds = sessionFactory.getCurrentSession().createNativeQuery(distinctIdsQuery);
    NativeQuery getOld =
        sessionFactory
            .getCurrentSession()
            .createNativeQuery(
                "select REV, id  from StructuredDocument_AUD where id =? order by REV desc");
    List ids = getIds.list();
    for (Object o : ids) {
      Long id = ((BigInteger) o).longValue();
      getOld.setParameter(1, id);
      getOld.setFirstResult(maxToKeep);
      List res = getOld.list();
      if (!res.isEmpty()) {
        Object[] toDelete0 = (Object[]) res.get(0);

        List res2 =
            getAuditReader()
                .createQuery()
                .forRevisionsOfEntity(StructuredDocument.class, false, false)
                .add(AuditEntity.id().eq(id))
                .add(AuditEntity.revisionNumber().le((Number) (toDelete0[0])))
                .getResultList();
        List<AuditedRecord> asds = processRecordResults(res2);
        allresults.addAll(asds);
      }
    }
    return allresults;
  }
}
