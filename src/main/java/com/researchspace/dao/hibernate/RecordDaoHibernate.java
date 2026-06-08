package com.researchspace.dao.hibernate;

import static com.researchspace.core.util.TransformerUtils.toSet;

import com.axiope.search.SearchUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.DatabaseUsageByUserGroupByResult;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.RecordDao;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.transform.AliasToBeanResultTransformer;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Repository;

@Repository("recordDao")
public class RecordDaoHibernate extends GenericDaoHibernate<Record, Long> implements RecordDao {

  private static final String OWNER_FIELD = "owner";
  private static final String DOC_TAG_FIELD = "docTag";
  private static final String DOC_TAG_META_FIELD = "tagMetaData";
  private static final String DELETED = "deleted";
  private static final String COUNT_OWNERS_QUERY =
      "select count(id) as countLong, owner.username as username from BaseRecord  ";
  private static final String PARENT_ID = "parentId";
  private static final String USER_ID = "userId";
  private static final String RECORDS_IN_FOLDER_QUERY =
      " from BaseRecord br inner join br.parents c where "
          + "c.recordInFolderDeleted=false and br.deleted=false and c.folder.id=:parentId ";

  public RecordDaoHibernate() {
    super(Record.class);
  }

  /** Overriden to version structured documents on save. Also refreshes cache */
  @Override
  @CachePut(
      value = "com.researchspace.model.record.BaseRecord",
      key = "#a0.oid",
      condition = "(#a0.id ne null)")
  public Record save(Record object) {
    if (object.isStructuredDocument()) {
      StructuredDocument sd = (StructuredDocument) object;
      sd.incrementVersion();
    }
    return super.save(object);
  }

  // we should filter by type if filter is set but we don't want all types. IF we want all types
  // then we don't need
  // to filter
  private boolean shouldFilterByType(RecordTypeFilter recordFilter) {
    return recordFilter != null
        && !recordFilter.getWantedTypes().isEmpty()
        && !allTypesWanted(recordFilter);
  }

  private boolean allTypesWanted(RecordTypeFilter recordFilter) {
    return EnumSet.allOf(RecordType.class).size() == recordFilter.getWantedTypes().size();
  }

  private String createInClause(RecordTypeFilter recordFilter) {
    if (shouldFilterByType(recordFilter)) {
      StringBuilder sb = new StringBuilder();
      sb.append(" and (");
      for (RecordType rtf : recordFilter.getWantedTypes()) {
        sb.append(" br.type like '%").append(rtf.name()).append("%' or ");
      }
      sb.delete(sb.lastIndexOf("or"), sb.length());
      sb.append(") ");
      if (!recordFilter.getExcludedTypes().isEmpty()) {
        sb.append(" and (");
      }
      for (RecordType rtf : recordFilter.getExcludedTypes()) {
        sb.append(" br.type  not like '%").append(rtf.name()).append("%' and ");
      }
      sb.delete(sb.lastIndexOf("and"), sb.length());
      sb.append(") ");
      return sb.toString();
    } else {
      return "";
    }
  }

  @Override
  public long getChildRecordCount(final Long parentId, RecordTypeFilter recordFilter) {
    Session session = getSession();
    Query<Long> countQuery =
        session.createQuery(
            "select count(br.id) " + RECORDS_IN_FOLDER_QUERY + createInClause(recordFilter),
            Long.class);
    countQuery.setParameter(PARENT_ID, parentId);
    return countQuery.uniqueResult();
  }

  @Override
  public List<BaseRecord> loadRecordFromResults(List<Object> results) {
    List<BaseRecord> toReturn = new ArrayList<>();
    for (Object row : results) {
      // data [0] is id
      Object[] data = (Object[]) row;
      // Hibernate 6: native SQL returns Long, not BigInteger
      BaseRecord rc = getSession().get(BaseRecord.class, ((Number) data[0]).longValue());
      rc.getCreatedBy(); // init fields
      toReturn.add(rc);
    }
    return toReturn;
  }

  @Override
  public ISearchResults<BaseRecord> getPaginatedChildRecordsOfParentWithFilter(
      final Long parentId,
      PaginationCriteria<? extends BaseRecord> pgCrit,
      RecordTypeFilter recordFilter) {
    int firstResult = pgCrit.getFirstResultIndex();
    Session session = sessionFactory.getCurrentSession();
    // get total number of hits to work out how many pages needed.
    long totalHits = getChildRecordCount(parentId, recordFilter);
    // regular HQL OK for SDoc search
    Query<BaseRecord> query;
    if (pgCrit.getOrderBy() != null && pgCrit.getOrderBy().contains("template")) {
      final String structDocsInFolderQuery =
          "select r from StructuredDocument r join r.parents folders "
              + "where folders.folder.id = :parentId "
              + "and folders.recordInFolderDeleted=false "
              + "order by r.form.editInfo.name ";
      log.info("order by is 'template', retrieving docs by form name for folder {}", parentId);
      query =
          session.createQuery(structDocsInFolderQuery + pgCrit.getSortOrder(), BaseRecord.class);
    } else {
      query =
          session.createQuery(
              "select br "
                  + RECORDS_IN_FOLDER_QUERY
                  + createInClause(recordFilter)
                  + "  "
                  + makeOrderBy(pgCrit),
              BaseRecord.class);
    }
    query.setMaxResults(pgCrit.getResultsPerPage());
    query.setFirstResult(firstResult);
    query.setParameter(PARENT_ID, parentId);
    List<BaseRecord> br = query.list();
    return new SearchResultsImpl<>(br, pgCrit, totalHits);
  }

  private static String makeOrderBy(PaginationCriteria<? extends BaseRecord> pgCrit) {
    String orderBy;
    if (!StringUtils.isEmpty(pgCrit.getOrderBy())) {
      // Hibernate 6: editInfo properties need explicit path prefix
      String field = pgCrit.getOrderBy();
      if (!field.startsWith("editInfo.")) {
        field = "editInfo." + field;
      }
      orderBy = " order by " + field + " " + pgCrit.getSortOrder();
    } else {
      orderBy =
          " order by editInfo."
              + SearchUtils.BASE_RECORD_ORDER_BY_LAST_MODIFIED
              + " "
              + SortOrder.DESC;
    }
    return orderBy;
  }

  @Override
  public List<Long> getNotebookContentsExcludeFolders(Long parentId) {
    Session session = getSessionFactory().getCurrentSession();
    String query =
        "select doc.id from StructuredDocument doc inner join doc.parents rtf where"
            + " rtf.recordInFolderDeleted=false and doc.type like :type and doc.deleted=false and"
            + " doc.temporaryDoc=false and rtf.folder.id = :folder  order by"
            + " doc.editInfo.creationDateMillis asc";
    Query<Long> q = session.createQuery(query, Long.class);
    q.setParameter("folder", parentId);
    q.setParameter("type", "%" + RecordType.NORMAL.name() + "%");
    return q.list();
  }

  @Override
  public Map<String, DatabaseUsageByUserGroupByResult> getTotalRecordsForUsers(
      Collection<User> users, PaginationCriteria<User> pgCrit) {
    Map<String, DatabaseUsageByUserGroupByResult> rc = new LinkedHashMap<>();
    if (users == null || users.isEmpty()) {
      return rc;
    }
    Session session = sessionFactory.getCurrentSession();
    List<String> usernames = users.stream().map(User::getUsername).collect(Collectors.toList());
    Query q =
        session
            .createQuery(
                COUNT_OWNERS_QUERY
                    + " where owner.username in (:users) "
                    + " group by owner.username "
                    + " order by countLong "
                    + pgCrit.getSortOrder().toString())
            .setParameterList("users", usernames)
            .setResultTransformer(
                new AliasToBeanResultTransformer(DatabaseUsageByUserGroupByResult.class));

    q.setFirstResult(pgCrit.getFirstResultIndex());
    q.setMaxResults(pgCrit.getResultsPerPage());
    List<DatabaseUsageByUserGroupByResult> results = q.list();

    for (DatabaseUsageByUserGroupByResult fu : results) {
      rc.put(fu.getUsername(), fu);
    }
    return rc;
  }

  @Override
  public Map<String, DatabaseUsageByUserGroupByResult> getTotalRecordsForUsers(
      PaginationCriteria<User> pgCrit) {
    if (pgCrit == null) {
      throw new IllegalArgumentException("PaginationCriteria argument cannot be null!");
    }
    Session session = sessionFactory.getCurrentSession();
    String sortOrder = pgCrit.getSortOrder().toString();
    // preserve DB sort order
    Map<String, DatabaseUsageByUserGroupByResult> rc = new LinkedHashMap<>();
    Query q =
        session
            .createQuery(
                COUNT_OWNERS_QUERY + " group by owner.username order by countLong " + sortOrder)
            .setMaxResults(pgCrit.getResultsPerPage())
            .setFirstResult(pgCrit.getFirstResultIndex())
            .setResultTransformer(
                new AliasToBeanResultTransformer(DatabaseUsageByUserGroupByResult.class));
    List<DatabaseUsageByUserGroupByResult> results = q.list();

    for (DatabaseUsageByUserGroupByResult fu : results) {
      rc.put(fu.getUsername(), fu);
    }
    return rc;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public BaseRecord getRecordFromFieldId(long fieldId) {
    Session session = getSessionFactory().getCurrentSession();
    Query q =
        session.createQuery(
            "Select r from BaseRecord r, Field f "
                + "where f.id= :fieldId and r.id = f.structuredDocument.id");
    q.setParameter("fieldId", fieldId);
    List results = q.list();
    if (results != null && !results.isEmpty()) {
      return (BaseRecord) results.get(0);
    } else {
      return null;
    }
  }

  @Override
  public Long getCountOfUsersWithRecords() {
    Session session = sessionFactory.getCurrentSession();
    Query<?> q = session.createNativeQuery("select count(distinct owner_id) from BaseRecord");
    return ((Number) q.getSingleResult()).longValue();
  }

  @Override
  public List<RSpaceDocView> getRecordViewsById(Set<Long> dbids) {

    return getSession()
        .createQuery(
            "select new com.researchspace.model.views.RSpaceDocView(br.editInfo.name, br.id, "
                + " br.editInfo.creationDate,  br.editInfo.modificationDate, br.type , u.id,"
                + " u.username, u.email, concat(u.firstName, ' ', u.lastName) )  from BaseRecord br"
                + " inner join  User u on u.id=br.owner.id where br.id in :dbids",
            RSpaceDocView.class)
        .setParameterList("dbids", dbids)
        .list();
  }

  @Override
  public Set<BaseRecord> getViewableRecordsForUsers(Set<Long> userIds) {
    String[] exclude =
        new String[] {
          RecordType.SYSTEM.toString(),
          RecordType.FOLDER.toString(),
          RecordType.ROOT.toString(),
          RecordType.ROOT_MEDIA.toString(),
          RecordType.TEMPLATE.toString(),
          RecordType.SNIPPET.toString(),
          RecordType.MEDIA_FILE.toString()
        };
    return getViewableRecords(userIds, null, exclude, BaseRecord.class);
  }

  @Override
  public Set<BaseRecord> getViewableTemplatesForUsers(Set<Long> userIds) {
    String[] limitTo = new String[] {RecordType.TEMPLATE.toString()};
    String[] exclude = new String[] {RecordType.FOLDER.toString()};
    return getViewableRecords(userIds, limitTo, exclude, StructuredDocument.class);
  }

  @Override
  public Set<BaseRecord> getViewableMediaFiles(Set<Long> userIds) {
    String[] limitTo = new String[] {RecordType.MEDIA_FILE.toString()};
    return getViewableRecords(userIds, limitTo, null, EcatMediaFile.class);
  }

  @SuppressWarnings("unchecked")
  private <T extends BaseRecord> Set<BaseRecord> getViewableRecords(
      Set<Long> userIds, String[] limitToTypes, String[] excludeTypes, Class<T> clazz) {
    Session session = sessionFactory.getCurrentSession();
    StringBuilder hql =
        new StringBuilder(
            "select record from "
                + clazz.getSimpleName()
                + " record where record.owner.id in (:userIds) and record.deleted=false"
                + " and record.type not like :rootMedia");
    if (limitToTypes != null) {
      for (int i = 0; i < limitToTypes.length; i++) {
        hql.append(" and record.type like :limitType").append(i);
      }
    }
    if (excludeTypes != null) {
      for (int i = 0; i < excludeTypes.length; i++) {
        hql.append(" and record.type not like :excludeType").append(i);
      }
    }
    Query<T> query = session.createQuery(hql.toString(), clazz);
    query.setParameterList("userIds", userIds);
    query.setParameter("rootMedia", "%" + RecordType.ROOT_MEDIA.name() + "%");
    if (limitToTypes != null) {
      for (int i = 0; i < limitToTypes.length; i++) {
        query.setParameter("limitType" + i, "%" + limitToTypes[i] + "%");
      }
    }
    if (excludeTypes != null) {
      for (int i = 0; i < excludeTypes.length; i++) {
        query.setParameter("excludeType" + i, "%" + excludeTypes[i] + "%");
      }
    }
    return new HashSet<>(query.list());
  }

  @Override
  public boolean isRecord(Long id) {
    return getSession()
            .createQuery(" select id from Record where id =:id")
            .setParameter("id", id)
            .uniqueResult()
        != null;
  }

  @Override
  public List<String> getTagsMetaDataForRecordsVisibleByUserOrPi(User userOrPi, String tagSearch) {
    Set<User> users = getUserTargets(userOrPi);
    List<String> docTags =
        getTagMetaDataForOwners(users, tagSearch, StructuredDocument.class, true);
    List<String> folderAndNotebookTags =
        getTagMetaDataForOwners(users, tagSearch, Folder.class, true);

    List<String> result = new ArrayList<>(docTags);
    result.addAll(folderAndNotebookTags);
    return result;
  }

  private List<String> getTagMetaDataForOwners(
      Set<User> owners,
      String tagSearch,
      Class<? extends BaseRecord> entityClass,
      boolean filterDeleted) {
    StringBuilder hql =
        new StringBuilder(
            "select distinct br."
                + DOC_TAG_META_FIELD
                + " from "
                + entityClass.getSimpleName()
                + " br where br.owner in (:owners)");
    if (filterDeleted) {
      hql.append(" and br.deleted=false");
    }
    appendTagFilterClause(hql, tagSearch);
    Query<String> query = getSession().createQuery(hql.toString(), String.class);
    query.setParameterList("owners", owners);
    applyTagFilterParameter(query, tagSearch);
    return query.list();
  }

  @Override
  public List<String> getTextDataFromOntologiesOwnedByUser(User userOrPi) {
    return getTagTextOrWholeOntologyDocumentCoreQuery(userOrPi, true, false, String.class).list();
  }

  @Override
  public List<String> getTextDataFromOntologyFilesOwnedByUserIfSharedWithAGroup(
      User userOrPi, Long[] ontologyRecordIDsSharedWithAGroup) {
    return getVisibleOntologyTagsForUserWhenSharedWithAGroupQuery(
            userOrPi, ontologyRecordIDsSharedWithAGroup)
        .list();
  }

  @Override
  public List<BaseRecord> getOntologyFilesOwnedByUser(User userOrPi) {
    return getTagTextOrWholeOntologyDocumentCoreQuery(userOrPi, false, false, BaseRecord.class)
        .list();
  }

  @Override
  public List<BaseRecord> getOntologyTagsFilesForUserCalled(User userOrPi, String fileName) {
    return getOntologyFilesOwnedByUser(userOrPi).stream()
        .filter(r -> r.getName().equals(fileName))
        .collect(Collectors.toList());
  }

  private Query<String> getVisibleOntologyTagsForUserWhenSharedWithAGroupQuery(
      User userOrPi, Long[] sharedOntologyIds) {
    return getTagTextOrWholeOntologyDocumentCoreQuery(
        userOrPi, true, true, String.class, sharedOntologyIds);
  }

  private <T> Query<T> getTagTextOrWholeOntologyDocumentCoreQuery(
      User userOrPi,
      boolean getText,
      boolean useOnlyTargetOntologies,
      Class<T> type,
      Long... ontologyIDs) {
    Query<T> q =
        getSession()
            .createQuery(
                (getText ? "select distinct rtfData " : "select distinct field.structuredDocument ")
                    + " from TextField field where field.structuredDocument.owner.username ="
                    + " (:uname) and field.structuredDocument.form.owner.username='sysadmin1' and"
                    + " field.structuredDocument.form.editInfo.name = (:ontologyFormName)  and"
                    + " field.structuredDocument.deleted = false "
                    + (useOnlyTargetOntologies
                        ? " and field.structuredDocument.id in (:sharedDocIds) "
                        : ""),
                type)
            .setParameter("uname", userOrPi.getUsername())
            .setParameter("ontologyFormName", CustomFormAppInitialiser.ONTOLOGY_FORM_NAME);
    if (useOnlyTargetOntologies) {
      q.setParameterList("sharedDocIds", ontologyIDs);
    }
    return q;
  }

  @Override
  public List<StructuredDocument> getontologyDocumentsCreatedInPastThirtyMinutesByCurrentUser(
      String uName) {
    int thirtyMinsInMillis = 30 * 60 * 1000;
    Query<StructuredDocument> q =
        getSession()
            .createQuery(
                "select distinct field.structuredDocument from TextField field where"
                    + " field.structuredDocument.owner.username = (:uname) and"
                    + " field.structuredDocument.form.owner.username=(:uname) and"
                    + " field.structuredDocument.form.editInfo.name = (:ontologyFormName)  and"
                    + " field.structuredDocument.editInfo.modificationDateMillis > "
                    + (System.currentTimeMillis() - thirtyMinsInMillis)
                    + " and field.structuredDocument.deleted = false ",
                StructuredDocument.class)
            .setParameter("uname", uName)
            .setParameter("ontologyFormName", CustomFormAppInitialiser.ONTOLOGY_FORM_NAME);
    return q.list();
  }

  @Override
  public List<String> getTagsMetaDataForRecordsVisibleByCommunityAdmin(
      User admin, String tagSearch) {
    String usersInCommunitySubquery =
        "select distinct u.id from User u"
            + " join u.userGroups ug"
            + " join ug.group g"
            + " join g.communities comm"
            + " where comm.id in (select comm2.id from Community comm2 join comm2.admins admin"
            + " where admin.id = :adminId)";
    List<String> docTags =
        getTagMetaDataForOwnersById(
            usersInCommunitySubquery, admin, tagSearch, StructuredDocument.class);
    List<String> folderAndNotebookTags =
        getTagMetaDataForOwnersById(usersInCommunitySubquery, admin, tagSearch, Folder.class);

    List<String> result = new ArrayList<>(docTags);
    result.addAll(folderAndNotebookTags);
    return result;
  }

  private Set<User> getUserTargets(User userOrPi) {
    Set<User> rc = toSet(userOrPi);
    rc.addAll(userOrPi.getNonPiLabGroupMembersForPiOrViewAllAdmin());
    return rc;
  }

  @Override
  public List<String> getTagsMetaDataForRecordsVisibleBySystemAdmin(
      User subject, String tagFilter) {
    List<String> docTags = getTagMetaDataForAllRecords(tagFilter, StructuredDocument.class);
    List<String> folderAndNotebookTags = getTagMetaDataForAllRecords(tagFilter, Folder.class);

    List<String> result = new ArrayList<>(docTags);
    result.addAll(folderAndNotebookTags);
    return result;
  }

  /**
   * Overwritten for performance reasons to avoid loading up all proeprties of Record and its
   * associations. {@inheritDoc}
   */
  @Override
  public boolean exists(Long id) {
    return getSession()
            .createQuery("select 1 from Record where id = :id")
            .setParameter("id", id)
            .setMaxResults(1)
            .uniqueResult()
        != null;
  }

  private void appendTagFilterClause(StringBuilder hql, String tagSearch) {
    if (!StringUtils.isBlank(tagSearch)) {
      hql.append(" and lower(br.").append(DOC_TAG_FIELD).append(") like :tagSearch");
    } else {
      hql.append(" and br.").append(DOC_TAG_FIELD).append(" is not null");
    }
  }

  private void applyTagFilterParameter(Query<?> query, String tagSearch) {
    if (!StringUtils.isBlank(tagSearch)) {
      query.setParameter("tagSearch", "%" + tagSearch.toLowerCase() + "%");
    }
  }

  private List<String> getTagMetaDataForOwnersById(
      String ownersSubquery,
      User admin,
      String tagSearch,
      Class<? extends BaseRecord> entityClass) {
    StringBuilder hql =
        new StringBuilder(
            "select distinct br."
                + DOC_TAG_META_FIELD
                + " from "
                + entityClass.getSimpleName()
                + " br where br.owner.id in ("
                + ownersSubquery
                + ")");
    appendTagFilterClause(hql, tagSearch);
    Query<String> query = getSession().createQuery(hql.toString(), String.class);
    query.setParameter("adminId", admin.getId());
    applyTagFilterParameter(query, tagSearch);
    return query.list();
  }

  private List<String> getTagMetaDataForAllRecords(
      String tagSearch, Class<? extends BaseRecord> entityClass) {
    StringBuilder hql =
        new StringBuilder(
            "select distinct br."
                + DOC_TAG_META_FIELD
                + " from "
                + entityClass.getSimpleName()
                + " br where 1=1");
    appendTagFilterClause(hql, tagSearch);
    Query<String> query = getSession().createQuery(hql.toString(), String.class);
    applyTagFilterParameter(query, tagSearch);
    return query.list();
  }

  private static final String LINKED_DOCS_QUERY =
      "select doc.editInfo.name as name,"
          + " doc.id as id,"
          + " doc.editInfo.creationDate as creationDate,"
          + " doc.editInfo.modificationDate as modificationDate from StructuredDocument doc"
          + " join doc.fields f "
          + " join f.linkedMediaFiles medias"
          + " join medias.mediaFile file "
          + " where file.id=:mediaFileId and doc.deleted=:deleted"
          + " and medias.deleted=:mediaDeleted"
          + " order by doc.editInfo.modificationDateMillis desc";

  @Override
  public List<RecordInformation> getInfosOfDocumentsLinkedToMediaFile(Long mediaFileId) {
    @SuppressWarnings("unchecked")
    Query<RecordInformation> q =
        getSession()
            .createQuery(LINKED_DOCS_QUERY)
            .setParameter("mediaFileId", mediaFileId)
            .setParameter(DELETED, false)
            .setParameter("mediaDeleted", false)
            .setResultTransformer(new AliasToBeanResultTransformer(RecordInformation.class));
    return q.list();
  }

  @Override
  public List<Record> getRecordsById(List<Long> dbids) {
    if (dbids.isEmpty()) {
      return Collections.emptyList();
    } else {
      return getSession()
          .createQuery("from Record where id in :ids", Record.class)
          .setParameterList("ids", dbids)
          .list();
    }
  }

  @Override
  public int updateRecordToFolder(RecordToFolder toUpdate, Long newFolderId) {
    // Hibernate 6 HQL requires property paths, not SQL column names.
    return getSession()
        .createMutationQuery(
            "update RecordToFolder rtf set rtf.folder.id = :newFolderId"
                + " where rtf.folder.id = :fid and rtf.record.id = :recordId")
        .setParameter("newFolderId", newFolderId)
        .setParameter("fid", toUpdate.getFolder().getId())
        .setParameter("recordId", toUpdate.getRecord().getId())
        .executeUpdate();
  }

  @Override
  public List<Long> getAllNotebookIdsOwnedByUser(User user) {
    return getIdsbyRecordSubtype(user, "Notebook", "");
  }

  @Override
  public List<Long> getAllNonTemplateNonTemporaryStrucDocIdsOwnedByUser(User user) {
    return getIdsbyRecordSubtype(
        user, "StructuredDocument", "and e.type not like '%%TEMPLATE%%' and e.temporaryDoc=false");
  }

  private List<Long> getIdsbyRecordSubtype(User user, String entityName, String subquery) {
    String queryString =
        String.format(
            "select e.id from %s e where e.owner.id=:userId and e.deleted=false %s",
            entityName, subquery);
    Query<Long> query =
        getSession().createQuery(queryString, Long.class).setParameter(USER_ID, user.getId());
    return query.list();
  }

  @Override
  public List<Long> getAllDocumentIdsInNotebooksForUser(User user) {
    Query<Long> query =
        getSession()
            .createQuery(
                "select child.id from BaseRecord nb inner join RecordToFolder rtf  on"
                    + " rtf.folder.id=nb.id  inner join StructuredDocument child on"
                    + " child.id=rtf.record.id  where nb.type like '%NOTEBOOK%' and"
                    + " nb.deleted=false  and nb.owner.id=:userId and child.owner.id=:userId",
                Long.class)
            .setParameter(USER_ID, user.getId());
    return query.list();
  }

  @Override
  public Long getRecordCountForUser(RecordTypeFilter recordFilter, User user) {
    Session session = getSession();
    Query<Long> countQuery =
        session.createQuery(
            "select count(br.id) from BaseRecord br where br.owner.id=:userId and br.deleted=false "
                + createInClause(recordFilter),
            Long.class);
    countQuery.setParameter(USER_ID, user.getId());
    return countQuery.uniqueResult();
  }

  /**
   * Moves the specified records that are currently owned by the specified user to the given folder.
   *
   * @param recordIds - The records to be moved.
   * @param currentOwner - The current owner of those records
   * @param destinationFolder - The new folder for those records
   */
  @Override
  public void moveUsersRecordsToFolder(
      List<Long> recordIds, User currentOwner, Folder destinationFolder) {
    Session session = getSession();
    // Note the format of the below query, with the nested select.  This is because
    // MariaDB was giving an exception when attempting to simply use a WHERE clause
    // with two parameters...
    Query<?> query =
        session
            .createQuery(
                "UPDATE RecordToFolder rtf SET folder=:destination WHERE id IN (SELECT id FROM"
                    + " RecordToFolder WHERE folder.owner=:originalOwner AND record.id IN :ids)")
            .setParameter("destination", destinationFolder)
            .setParameter("ids", recordIds)
            .setParameter("originalOwner", currentOwner);
    query.executeUpdate();
  }

  @Override
  public boolean hasUserSharedTemplatesUsedByOtherUsers(User user) {
    Session session = getSession();
    Object result =
        session
            .createQuery(
                "SELECT COUNT(DISTINCT(br.id)) FROM BaseRecord br WHERE br.id IN (SELECT sd.id FROM"
                    + " StructuredDocument sd JOIN BaseRecord br ON br.id = sd.template.id  WHERE"
                    + " br.owner = :owner AND sd.template.id IS NOT NULL) AND br.owner != :owner")
            .setParameter("owner", user)
            .uniqueResult();
    Long count = (Long) result;
    return count > 0;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<BaseRecord> getTemplatesOwnedByUserAndUsedByOtherUsers(User user) {
    Session session = getSession();
    Query<BaseRecord> query =
        session
            .createQuery(
                "select brB from StructuredDocument sdB join BaseRecord brB on sdB.template.id ="
                    + " brB.id where sdB.id in (select brA.id from StructuredDocument sdA join"
                    + " BaseRecord brA on sdA.id = brA.id where sdA.template.id in (select"
                    + " sd.template.id from StructuredDocument sd join BaseRecord br on br.id ="
                    + " sd.template.id where br.owner = :owner) and brA.owner != :owner)")
            .setParameter("owner", user);
    List<BaseRecord> templates = query.list();
    return templates.stream().distinct().collect(Collectors.toList());
  }

  /**
   * Transfers ownership of templates from one user to another user.
   *
   * @param originalOwner - Original owner of the templates
   * @param newOwner - New owner of the templates
   * @param templateIds - IDs of the templates to be transferred
   * @param updatedOriginalOwnerName - An updated name, if any, for the original owner. (e.g. In the
   *     case the user has been deleted and updated with a suffix to denote that.)
   */
  @Override
  public void transferTemplates(
      User originalOwner, User newOwner, List<Long> templateIds, String updatedOriginalOwnerName) {
    Query<?> query;

    query =
        getSession()
            .createQuery("UPDATE BaseRecord b SET owner=:newOwner WHERE id IN :ids")
            .setParameter("newOwner", newOwner)
            .setParameter("ids", templateIds);
    query.executeUpdate();

    if (null != updatedOriginalOwnerName) {
      query =
          getSession()
              .createQuery(
                  "UPDATE BaseRecord b SET originalCreatorUsername=:updatedOriginalOwnerName WHERE"
                      + " id IN :ids AND originalCreatorUsername=:originalName")
              .setParameter("updatedOriginalOwnerName", updatedOriginalOwnerName)
              .setParameter("originalName", originalOwner.getUsername())
              .setParameter("ids", templateIds);
      query.executeUpdate();
      query =
          getSession()
              .createQuery(
                  "UPDATE BaseRecord b SET editInfo.createdBy=:updatedOriginalOwnerName WHERE id IN"
                      + " :ids AND editInfo.createdBy=:originalName")
              .setParameter("updatedOriginalOwnerName", updatedOriginalOwnerName)
              .setParameter("originalName", originalOwner.getUsername())
              .setParameter("ids", templateIds);
      query.executeUpdate();
      query =
          getSession()
              .createQuery(
                  "UPDATE BaseRecord b SET editInfo.modifiedBy=:updatedOriginalOwnerName WHERE id"
                      + " IN :ids AND editInfo.modifiedBy=:originalName")
              .setParameter("updatedOriginalOwnerName", updatedOriginalOwnerName)
              .setParameter("originalName", originalOwner.getUsername())
              .setParameter("ids", templateIds);
      query.executeUpdate();
    }

    query =
        getSession()
            .createNativeQuery("UPDATE BaseRecord_AUD b SET owner_id=:newOwner_id WHERE id IN :ids")
            .setParameter("newOwner_id", newOwner.getId())
            .setParameter("ids", templateIds);
    query.executeUpdate();

    // Modify the template ACL so that the new owner has the same level of
    // access as the original owner.  Do this in two stages to ensure that
    // we do not accidentally set for partial user name matches.
    query =
        getSession()
            .createNativeQuery(
                "UPDATE BaseRecord br SET acl = (SELECT CONCAT(:newOwner, (SELECT SUBSTRING(br.acl,"
                    + " LENGTH(:originalOwnerAtStart), LENGTH(br.acl))))) WHERE id IN :ids AND"
                    + " br.acl LIKE :originalOwnerAtStartQuery")
            .setParameter("originalOwnerAtStart", originalOwner.getUsername() + "=")
            .setParameter("originalOwnerAtStartQuery", "%" + originalOwner.getUsername() + "=%")
            .setParameter("newOwner", newOwner.getUsername())
            .setParameter("ids", templateIds);
    query.executeUpdate();

    query =
        getSession()
            .createNativeQuery(
                "UPDATE BaseRecord br SET acl = (SELECT REPLACE(br.acl, :originalOwnerInMiddle,"
                    + " :newOwnerInMiddle) ) WHERE id IN :ids")
            .setParameter("originalOwnerInMiddle", "&" + originalOwner.getUsername() + "=")
            .setParameter("newOwnerInMiddle", "&" + newOwner.getUsername() + "=")
            .setParameter("ids", templateIds);
    query.executeUpdate();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<EcatMediaFile> getGalleryItemsForTemplates(
      List<Long> templateIds, User originalOwner) {
    return getSession()
        .createQuery(
            "SELECT DISTINCT m FROM EcatMediaFile m"
                + " JOIN FieldAttachment fa ON fa.mediaFile = m"
                + " JOIN Field f ON fa.field = f"
                + " WHERE f.structuredDocument.id IN :templateIds"
                + " AND m.owner = :originalOwner"
                + " AND fa.deleted = false")
        .setParameter("templateIds", templateIds)
        .setParameter("originalOwner", originalOwner)
        .list();
  }

  @Override
  public void updateFilePropertyOwnerForMediaFiles(List<Long> mediaIds, String newOwnerUsername) {
    getSession()
        .createNativeQuery(
            "UPDATE FileProperty fp"
                + " INNER JOIN EcatMediaFile em ON em.fileProperty_id = fp.id"
                + " SET fp.fileOwner = :newOwnerUsername, fp.fileUser = :newOwnerUsername"
                + " WHERE em.id IN :mediaIds")
        .setParameter("newOwnerUsername", newOwnerUsername)
        .setParameter("mediaIds", mediaIds)
        .executeUpdate();

    getSession()
        .createNativeQuery(
            "UPDATE FileProperty fp"
                + " INNER JOIN EcatImage ei ON ei.thumbnailImageFP_id = fp.id"
                + " SET fp.fileOwner = :newOwnerUsername, fp.fileUser = :newOwnerUsername"
                + " WHERE ei.id IN :mediaIds")
        .setParameter("newOwnerUsername", newOwnerUsername)
        .setParameter("mediaIds", mediaIds)
        .executeUpdate();

    getSession()
        .createNativeQuery(
            "UPDATE FileProperty fp"
                + " INNER JOIN EcatDocumentFile edf ON edf.docThumbnailFP_id = fp.id"
                + " SET fp.fileOwner = :newOwnerUsername, fp.fileUser = :newOwnerUsername"
                + " WHERE edf.id IN :mediaIds")
        .setParameter("newOwnerUsername", newOwnerUsername)
        .setParameter("mediaIds", mediaIds)
        .executeUpdate();

    getSession()
        .createNativeQuery(
            "UPDATE FileProperty fp"
                + " INNER JOIN Thumbnail t ON t.thumbnailFP_id = fp.id"
                + " SET fp.fileOwner = :newOwnerUsername, fp.fileUser = :newOwnerUsername"
                + " WHERE t.sourceId IN :mediaIds")
        .setParameter("newOwnerUsername", newOwnerUsername)
        .setParameter("mediaIds", mediaIds)
        .executeUpdate();

    getSession()
        .createNativeQuery(
            "UPDATE FileProperty fp"
                + " INNER JOIN EcatImage ei ON ei.workingImageFP_id = fp.id"
                + " SET fp.fileOwner = :newOwnerUsername, fp.fileUser = :newOwnerUsername"
                + " WHERE ei.id IN :mediaIds")
        .setParameter("newOwnerUsername", newOwnerUsername)
        .setParameter("mediaIds", mediaIds)
        .executeUpdate();
  }
}
