package com.researchspace.dao.hibernate;

import static com.researchspace.core.util.TransformerUtils.transformToString;
import static java.lang.Long.parseLong;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isEmpty;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.DatabaseUsageByGroupGroupByResult;
import com.researchspace.dao.DatabaseUsageByUserGroupByResult;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.record.ObjectToIdPropertyTransformer;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.Query;
import org.hibernate.transform.AliasToBeanResultTransformer;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

/** New File metadata Dao implementation */
@Repository("fileMetadataDao")
public class FileMetadataHibernate extends GenericDaoHibernate<FileProperty, Long>
    implements FileMetadataDao {

  public FileMetadataHibernate() {
    super(FileProperty.class);
  }

  public Long getTotalFileUsageForUser(User user) {
    Query<String> q =
        getSession()
            .createQuery(
                "select sum(fileSize) from  FileProperty where fileOwner=:owner", String.class);
    q.setParameter("owner", user.getUsername());
    String result = q.uniqueResult();
    if (result != null) {
      return Long.parseLong(result);
    } else {
      return null;
    }
  }

  @Override
  public Map<String, DatabaseUsageByUserGroupByResult> getTotalFileUsageForUsers(
      Collection<User> user, PaginationCriteria<User> pgCrit) {
    List<String> unames = TransformerUtils.transformToString(user, "username");
    Session session = sessionFactory.getCurrentSession();
    SortOrder sortOrder = getSortOrder(pgCrit).orElse(SortOrder.DESC);
    Query<DatabaseUsageByUserGroupByResult> query =
        session
            .createQuery(
                "select sum(fileSize) as usage, fileOwner as username "
                    + "from  FileProperty "
                    + "where fileOwner in (:unames) group by fileOwner order by usage "
                    + sortOrder)
            .setParameterList("unames", unames)
            .setResultTransformer(
                new AliasToBeanResultTransformer(DatabaseUsageByUserGroupByResult.class));
    return doQuery(pgCrit, query);
  }

  @NotNull
  private Map<String, DatabaseUsageByUserGroupByResult> doQuery(
      PaginationCriteria<User> pgCrit, Query<DatabaseUsageByUserGroupByResult> query) {
    if (pgCrit != null) {
      query.setFirstResult(pgCrit.getFirstResultIndex());
      query.setMaxResults(pgCrit.getResultsPerPage());
    }
    List<DatabaseUsageByUserGroupByResult> results = query.list();
    Map<String, DatabaseUsageByUserGroupByResult> userToUsageResults = new LinkedHashMap<>();
    for (DatabaseUsageByUserGroupByResult fu : results) {
      userToUsageResults.put(fu.getUsername(), fu);
    }
    return userToUsageResults;
  }

  @Override
  public Long getTotalFileUsage() {
    String count =
        (String) getSession().createQuery("select sum(fileSize) from FileProperty").uniqueResult();
    return extractSizeFromResult(count);
  }

  private Long extractSizeFromResult(String count) {
    return (!isEmpty(count)) ? parseLong(count) : 0;
  }

  @Override
  public Long getTotalFileUsageForGroup(Group group) {
    if (group.getSize() == 0) {
      return 0L;
    }
    List<String> unames = transformToString(group.getMembers(), "username");
    Session session = getSession();
    Query<String> q =
        session
            .createQuery(
                "select sum(fileSize) as usage from  FileProperty where fileOwner in (:unames)",
                String.class)
            .setParameterList("unames", unames);
    String count = q.uniqueResult();
    return extractSizeFromResult(count);
  }

  @SuppressWarnings("unchecked")
  @Override
  /** This is using SQL query so cannot be tested in regular DAO tests, needs real DB test */
  public ISearchResults<DatabaseUsageByGroupGroupByResult> getTotalFileUsageForLabGroups(
      PaginationCriteria<Group> pgCrit) {
    Session session = getSession();
    StringBuilder querySB = getBasicQuery(true);
    addLabGroupRestriction(querySB);
    boolean isCommunityRestricted = false;
    GroupSearchCriteria sc = (GroupSearchCriteria) pgCrit.getSearchCriteria();
    if (sc != null && sc.isCommunityIdSet()) {
      isCommunityRestricted = true;
      addCommunityRestriction(querySB, sc.getCommunityId());
    }
    addGroupAndOrder(querySB, pgCrit);
    String query = querySB.toString();
    Long count;
    if (isCommunityRestricted) {
      count =
          (Long)
              session
                  .createQuery(
                      "select count(*) from Group g where g.groupType=:type and"
                          + " g.communityId=:commId")
                  .setParameter("type", GroupType.LAB_GROUP)
                  .setParameter("commId", sc.getCommunityId())
                  .uniqueResult();
    } else {
      count =
          (Long)
              session
                  .createQuery("select count(*) from Group g where g.groupType=:type")
                  .setParameter("type", GroupType.LAB_GROUP)
                  .uniqueResult();
    }

    List<DatabaseUsageByGroupGroupByResult> results =
        session
            .createSQLQuery(query)
            .setMaxResults(pgCrit.getResultsPerPage())
            .setFirstResult(pgCrit.getFirstResultIndex())
            .setResultTransformer(
                new AliasToBeanResultTransformer(DatabaseUsageByGroupGroupByResult.class))
            .list();
    return new SearchResultsImpl<>(results, pgCrit, count);
  }

  private void addCommunityRestriction(StringBuilder querySB, Long communityId) {
    querySB.append(" and c.id=").append(communityId);
  }

  private void addLabGroupRestriction(StringBuilder querySB) {
    querySB.append(" where g.groupType=0");
  }

  private StringBuilder addGroupAndOrder(StringBuilder sb, PaginationCriteria<Group> pgCrit) {
    return sb.append(" group by g.id").append(" order by fileusage ").append(pgCrit.getSortOrder());
  }

  private StringBuilder getBasicQuery(boolean filterByCommunity) {
    StringBuilder sb =
        new StringBuilder().append("select g.id as groupId,  sum(fp.fileSize) as fileusage from ");
    if (filterByCommunity) {
      sb.append(
          " Community c left join  community_labGroups clg on clg.community_id=c.id inner join"
              + " rsGroup g on clg.group_id=g.id");
    } else {
      sb.append(" rsGroup g");
    }
    sb.append("  inner join UserGroup ug on g.id=ug.group_id")
        .append(" inner join User u on u.id=ug.user_id")
        .append(" left join FileProperty fp on fp.fileOwner=u.username");
    return sb;
  }

  public List<DatabaseUsageByGroupGroupByResult> getTotalFileUsageForGroups(
      Collection<Group> grps) {
    if (grps.isEmpty()) {
      return Collections.emptyList();
    }
    List<Long> ids =
        grps.stream().map(new ObjectToIdPropertyTransformer()).collect(Collectors.toList());
    StringBuilder querySB = getBasicQuery(false);
    addLabGroupRestriction(querySB);
    String inClause = "(" + StringUtils.join(ids, ",") + ")";
    querySB.append(" and g.id in ").append(inClause).append(" group by g.id");
    String query = querySB.toString();

    return getSession()
        .createSQLQuery(query)
        .setResultTransformer(
            new AliasToBeanResultTransformer(DatabaseUsageByGroupGroupByResult.class))
        .list();
  }

  public Long getCountOfUsersWithFilesInFileSystem() {
    Query<Long> q =
        getSession().createQuery("select count( distinct fileOwner) from FileProperty", Long.class);
    return q.uniqueResult();
  }

  public Map<String, DatabaseUsageByUserGroupByResult> getTotalFileUsageForAllUsers(
      PaginationCriteria<User> pgCrit) {
    Session session = getSession();
    SortOrder sortOrder = getSortOrder(pgCrit).orElse(SortOrder.DESC);
    Query<DatabaseUsageByUserGroupByResult> query =
        session
            // sortOrder is an enum so can't be a bad string even though it's added by concatenation
            .createQuery(
                "select sum(fileSize) as usage, fileOwner as username "
                    + "from  FileProperty "
                    + "where fileOwner is not null "
                    + "group by fileOwner"
                    + " order by usage "
                    + sortOrder)
            .setResultTransformer(
                new AliasToBeanResultTransformer(DatabaseUsageByUserGroupByResult.class));
    return doQuery(pgCrit, query);
  }

  private Optional<SortOrder> getSortOrder(PaginationCriteria<User> pgCrit) {
    SortOrder rc = null;
    if (pgCrit != null && pgCrit.getSortOrder() != null) {
      rc = pgCrit.getSortOrder();
    }
    return Optional.ofNullable(rc);
  }

  @Override
  public List<FileProperty> findProperties(Map<String, String> wheres) {
    Session session = getSession();
    Query<FileProperty> query =
        session.createQuery(" from FileProperty " + prepareWhereClause(wheres), FileProperty.class);
    for (Map.Entry<String, String> entry : wheres.entrySet()) {
      query.setParameter(entry.getKey(), entry.getValue());
    }
    return query.list();
  }

  private String prepareWhereClause(Map<String, String> where) {
    if (where == null || where.isEmpty()) {
      return "";
    }
    StringBuilder stringBuilder = new StringBuilder();
    int whereSize = where.size();
    stringBuilder.append(" where ");
    int count = 0;
    for (Map.Entry<String, String> x : where.entrySet()) {
      if (count < whereSize - 1) {
        stringBuilder.append(x.getKey()).append("=:").append(x.getKey()).append(" and ");
      } else {
        stringBuilder.append(x.getKey()).append("=:").append(x.getKey());
      }
      count++;
    }
    return stringBuilder.toString();
  }

  @Override
  public List<File> collectUserFilestoreResources(User user) {
    Session ssnx = getSession();
    Query<FileProperty> query =
        ssnx.createQuery(" from FileProperty where fileOwner=:owner ", FileProperty.class);
    query.setParameter("owner", user.getUsername());

    List<FileProperty> list = query.list();
    return list.stream()
        .map(this::getFileFromFileProperty)
        .filter(Objects::nonNull)
        .collect(toList());
  }

  private File getFileFromFileProperty(FileProperty fp) {
    try {
      return new File(new URI(fp.getAbsolutePathUri()));
    } catch (URISyntaxException e) {
      log.warn("can't parse URI: {}", fp.getAbsolutePathUri());
      return null;
    }
  }

  @Override
  public FileStoreRoot findByFileStorePath(String fileStorePath) {
    return (FileStoreRoot)
        getSession()
            .createCriteria(FileStoreRoot.class)
            .add(
                Restrictions.conjunction()
                    .add(Restrictions.ilike("fileStoreRoot", fileStorePath, MatchMode.ANYWHERE))
                    .add(Restrictions.ilike("fileStoreRoot", "file:", MatchMode.START)))
            .uniqueResult();
  }

  public FileStoreRoot saveFileStoreRoot(FileStoreRoot root) {
    return (FileStoreRoot) getSession().merge(root);
  }

  public void resetCurrentFileStoreRoot(boolean external) {
    getSession()
        .createQuery("update FileStoreRoot set current=:current where external=:external")
        .setParameter("current", false)
        .setParameter("external", external)
        .executeUpdate();
  }

  @Override
  public FileStoreRoot getCurrentFileStoreRoot(boolean external) {
    List<FileStoreRoot> results =
        getSession()
            .createQuery(
                "from FileStoreRoot where current=:current and external=:external",
                FileStoreRoot.class)
            .setParameter("current", true)
            .setParameter("external", external)
            .list();
    return results.isEmpty() ? null : results.get(0);
  }

  @Override
  public boolean doesUserOwnDocWithHash(User user, String contentsHash) {
    long sampleHits =
        (long)
            getSession()
                .createQuery(
                    "select count(*) from Sample where owner = :user and"
                        + " (imageFileProperty.contentsHash = :contentsHash or"
                        + " thumbnailFileProperty.contentsHash = :contentsHash) ")
                .setParameter("user", user)
                .setParameter("contentsHash", contentsHash)
                .getSingleResult();
    if (sampleHits > 0) {
      return true;
    }

    long subSampleHits =
        (long)
            getSession()
                .createQuery(
                    "select count(*) from SubSample where sample.owner = :user and"
                        + " (imageFileProperty.contentsHash = :contentsHash or"
                        + " thumbnailFileProperty.contentsHash = :contentsHash) ")
                .setParameter("user", user)
                .setParameter("contentsHash", contentsHash)
                .getSingleResult();
    if (subSampleHits > 0) {
      return true;
    }

    long containerHits =
        (long)
            getSession()
                .createQuery(
                    "select count(*) from Container where owner = :user and"
                        + " (imageFileProperty.contentsHash = :contentsHash or"
                        + " thumbnailFileProperty.contentsHash = :contentsHash or"
                        + " locationsImageFileProperty.contentsHash = :contentsHash) ")
                .setParameter("user", user)
                .setParameter("contentsHash", contentsHash)
                .getSingleResult();
    if (containerHits > 0) {
      return true;
    }

    return false;
  }

  @Override
  public FileProperty getImageFileByHash(String contentsHash) {
    return getSession()
        .createQuery("from FileProperty where contentsHash = :contentsHash", FileProperty.class)
        .setParameter("contentsHash", contentsHash)
        .setMaxResults(1)
        .getSingleResult();
  }
}
