package com.researchspace.dao.hibernate;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isEmpty;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.TaggableElnRecord;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("recordGroupSharingDao")
public class RecordGroupSharingDaoHibernateImpl
    extends GenericDaoHibernate<RecordGroupSharing, Long> implements RecordGroupSharingDao {

  public RecordGroupSharingDaoHibernateImpl() {
    super(RecordGroupSharing.class);
  }

  @Override
  public List<BaseRecord> getRecordsSharedByGroup(Long groupId) {
    String qStr = "from RecordGroupSharing where sharee.id=:id";
    Query<RecordGroupSharing> query =
        getSession().createQuery(qStr, RecordGroupSharing.class).setParameter("id", groupId);

    List<RecordGroupSharing> rcgs = query.list();
    List<BaseRecord> res = convertToBaseRecordList(rcgs);
    return res;
  }

  @Override
  public RecordGroupSharing getRecordWithPublicLink(String publicLink) {
    String qStr = "from RecordGroupSharing where publicLink=:publicLink";
    Query<RecordGroupSharing> query =
        getSession()
            .createQuery(qStr, RecordGroupSharing.class)
            .setParameter("publicLink", publicLink);
    return query.uniqueResult();
  }

  private List<BaseRecord> convertToBaseRecordList(List<RecordGroupSharing> rcgs) {
    return rcgs.stream().map(RecordGroupSharing::getShared).collect(toList());
  }

  @Override
  public long removeAllForGroup(Group group) {
    Query<?> query = getSession().createQuery("delete from RecordGroupSharing where sharee=:group");
    query.setParameter("group", group);
    return query.executeUpdate();
  }

  public long removeRecordFromGroupShare(Long groupId, Long recordId) {
    Query<?> query =
        getSession()
            .createQuery(
                "delete from RecordGroupSharing where sharee.id=:id and shared.id=:recordId");
    query.setParameter("id", groupId);
    query.setParameter("recordId", recordId);
    return query.executeUpdate();
  }

  public boolean isRecordAlreadySharedInGroup(Long groupId, Long... recordIds) {
    Query<Long> query =
        getSession()
            .createQuery(
                "select count(id) from RecordGroupSharing "
                    + "where sharee.id=:id and shared.id in(:recordIds)",
                Long.class)
            .setParameter("id", groupId)
            .setParameterList("recordIds", recordIds);
    return query.uniqueResult() > 0;
  }

  public List<BaseRecord> findRecordsSharedWithUserOrGroup(
      Long userOrGroupId, Collection<Long> recordIds) {
    Query<RecordGroupSharing> query =
        getSession()
            .createQuery(
                "from RecordGroupSharing where sharee.id=:id and shared.id in(:recordIds)",
                RecordGroupSharing.class);
    query.setParameter("id", userOrGroupId);
    query.setParameterList("recordIds", recordIds);
    List<BaseRecord> res = convertToBaseRecordList(query.list());
    return res;
  }

  @Override
  public Optional<RecordGroupSharing> findByRecordAndUserOrGroup(Long userOrGroupId, Long recordId) {
    Query<RecordGroupSharing> query =
        getSession()
            .createQuery(
                "from RecordGroupSharing where sharee.id=:id and shared.id = :recordId",
                RecordGroupSharing.class);
    query.setParameter("id", userOrGroupId);
    query.setParameter("recordId", recordId);
    return query.uniqueResultOptional();
  }

  @Override
  public List<RecordGroupSharing> getSharedRecordsForUser(User u) {
    Query<RecordGroupSharing> query =
        getSession()
            .createQuery(
                " from RecordGroupSharing rgs where rgs.shared.owner.id=:id group by shared.id",
                RecordGroupSharing.class)
            .setParameter("id", u.getId());
    return query.list();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<BaseRecord> getSharedRecordsWithUser(User user) {
    Criteria query =
        getSharedRecordsWithUserQuery(user)
            .setProjection(Projections.distinct(Projections.property("shared")));
    return query.list();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Long> getSharedRecordIdsWithUser(User user) {
    Criteria query =
        getSharedRecordsWithUserQuery(user)
            .setProjection(Projections.distinct(Projections.property("id")));
    return query.list();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<BaseRecord> getSharedTemplatesWithUser(User user) {
    Criteria query =
        getSharedRecordsWithUserQuery(user)
            .setProjection(Projections.distinct(Projections.property("shared")));
    query.add(
        Restrictions.ilike("shared.type", RecordType.TEMPLATE.toString(), MatchMode.ANYWHERE));
    return query.list();
  }

  private Criteria getSharedRecordsWithUserQuery(User user) {
    Criteria query = getSession().createCriteria(RecordGroupSharing.class, "rgs");
    query
        .createAlias("rgs.shared", "shared")
        .createAlias("shared.owner", "owner")
        .add(Restrictions.not(Restrictions.eq("owner.id", user.getId())));
    query.createAlias("rgs.sharee", "sharee");

    DetachedCriteria subquery = DetachedCriteria.forClass(UserGroup.class, "userGroup");
    subquery.createAlias("userGroup.user", "user").add(Restrictions.eq("user.id", user.getId()));
    subquery.setProjection(Projections.property("userGroup.group.id"));

    Disjunction or = Restrictions.disjunction();
    or.add(Restrictions.eq("sharee.id", user.getId()));
    or.add(Subqueries.propertyIn("sharee.id", subquery));
    query.add(or);
    return query;
  }

  public List<String> getTagsMetaDataForRecordsSharedWithUser(User subject, String tagFilter) {
    Criteria allDocsSharedWithUser = getSharedRecordsWithUserQuery(subject);
    allDocsSharedWithUser.setProjection(Projections.distinct(Projections.property("shared")));
    allDocsSharedWithUser.setReadOnly(true);
    List<BaseRecord> allSharedDocs = allDocsSharedWithUser.list();
    List<String> result =
        allSharedDocs.stream()
            .filter(
                br ->
                    br.isTaggable() && StringUtils.isNotEmpty(((TaggableElnRecord) br).getDocTag()))
            .filter(
                br ->
                    StringUtils.isBlank(tagFilter)
                        || (((TaggableElnRecord) br).getDocTag().contains(tagFilter)))
            .map(br -> ((TaggableElnRecord) br).getTagMetaData())
            .collect(toList());
    return result;
  }

  @Override
  public List<String> getTextDataFromOntologiesSharedWithUser(User subject) {
    Criteria docsSharedWithUser = getOntologiesSharedWithUserQuery(subject);
    docsSharedWithUser.setProjection(Projections.distinct(Projections.property("field.rtfData")));
    return docsSharedWithUser.list();
  }

  @Override
  public List<String> getTextDataFromOntologiesSharedWithUserIfSharedWithAGroup(
      User subject, Long[] ontologyIDsSharedWithAGroup) {
    Criteria docsSharedWithUser = getOntologiesSharedWithUserQuery(subject);
    docsSharedWithUser.add(Restrictions.in("shared.id", ontologyIDsSharedWithAGroup));
    docsSharedWithUser.setProjection(Projections.distinct(Projections.property("field.rtfData")));
    return docsSharedWithUser.list();
  }

  @Override
  public List<BaseRecord> getOntologiesFilesSharedWithUser(User subject) {
    Criteria docsSharedWithUser = getOntologiesSharedWithUserQuery(subject);
    docsSharedWithUser.setProjection(Projections.distinct(Projections.property("shared")));
    return docsSharedWithUser.list();
  }

  private Criteria getOntologiesSharedWithUserQuery(User subject) {
    Criteria docsSharedWithUser = getSharedRecordsWithUserQuery(subject);
    docsSharedWithUser.createAlias("shared.fields", "field");
    docsSharedWithUser.add(Restrictions.eq("shared.deleted", false));
    docsSharedWithUser
        .createCriteria("shared.form")
        .createAlias("owner", "sdowner")
        .add(Restrictions.eq("editInfo.name", CustomFormAppInitialiser.ONTOLOGY_FORM_NAME))
        .add(Restrictions.eq("sdowner.username", "sysadmin1"));
    docsSharedWithUser.setProjection(Projections.distinct(Projections.property("field.rtfData")));
    docsSharedWithUser.setReadOnly(true);
    return docsSharedWithUser;
  }

  @Override
  public List<AbstractUserOrGroupImpl> getUsersOrGroupsWithRecordAccess(Long recordId) {
    Query<RecordGroupSharing> query =
        getSession()
            .createQuery(
                " from RecordGroupSharing rgs where rgs.shared.id=:id", RecordGroupSharing.class);
    query.setParameter("id", recordId);
    List<RecordGroupSharing> rc = query.list();
    List<AbstractUserOrGroupImpl> sharedWith = new ArrayList<AbstractUserOrGroupImpl>();
    for (RecordGroupSharing rgs : rc) {
      sharedWith.add(rgs.getSharee());
    }
    return sharedWith;
  }

  @Override
  public ISearchResults<RecordGroupSharing>
      listPublishedRecordsOwnedByUserOrGroupMembersPlusRecordsPublishedByUser(
          User u, PaginationCriteria<RecordGroupSharing> pcg, List<Long> membersOfUsersGroups) {
    if (pcg == null) {
      throw new IllegalArgumentException(" Pagination criteria cannot be null");
    }
    String baseQuery =
        " (rgs in (from RecordGroupSharing rgs2 where rgs2.shared.owner.id=:id and rgs2.publicLink"
            + " is not null) or rgs in (from RecordGroupSharing rgs3 where rgs3.sharedBy.id=:id and"
            + " rgs3.publicLink is not null) or rgs in (from RecordGroupSharing rgs4 where"
            + " rgs4.shared.owner.id in (:memberIDs) and rgs4.publicLink is not null))";
    String qStrCount = "select count(distinct id) from RecordGroupSharing rgs where" + baseQuery;
    String qStr = "select distinct rgs from RecordGroupSharing rgs where" + baseQuery;
    return getRecordGroupSharingISearchResults(u, pcg, qStrCount, qStr, membersOfUsersGroups, true);
  }

  @Override
  public ISearchResults<RecordGroupSharing> listUserRecordsPublished(
      User u, PaginationCriteria<RecordGroupSharing> pcg) {
    Validate.notNull(pcg, " Pagination criteria cannot be null");
    String qStrCount =
        "select count(distinct id) from RecordGroupSharing rgs where rgs.shared.owner.id=:id "
            + "and rgs.publicLink is not null ";
    String qStr =
        "select distinct rgs from RecordGroupSharing rgs where rgs.shared.owner.id=:id "
            + "and rgs.publicLink is not null ";
    return getRecordGroupSharingISearchResults(u, pcg, qStrCount, qStr, null, true);
  }

  @Override
  public ISearchResults<RecordGroupSharing> listSharedRecordsForUser(
      User u, PaginationCriteria<RecordGroupSharing> pcg) {
    if (pcg == null) {
      throw new IllegalArgumentException(" Pagination criteria cannot be null");
    }
    String qStrCount =
        "select count(id) from RecordGroupSharing rgs where rgs.shared.owner.id=:id and"
            + " rgs.publicLink is null";
    String qStr =
        "from RecordGroupSharing rgs where rgs.shared.owner.id=:id and rgs.publicLink is null";

    return getRecordGroupSharingISearchResults(u, pcg, qStrCount, qStr);
  }

  @Override
  public ISearchResults<RecordGroupSharing> listAllPublishedRecords(
      PaginationCriteria<RecordGroupSharing> pcg) {
    String qStr = "from RecordGroupSharing rgs where rgs.publicLink is not null";
    String qStrCount =
        "select count(id) from RecordGroupSharing rgs where rgs.publicLink is not null";
    return getRecordGroupSharingISearchResults(null, pcg, qStrCount, qStr, null, true);
  }

  @Override
  public ISearchResults<RecordGroupSharing> listAllPublishedRecordsForInternet(
      PaginationCriteria<RecordGroupSharing> pcg) {
    String qStr =
        "from RecordGroupSharing rgs where rgs.publicLink is not null and rgs.publishOnInternet ="
            + " true";
    String qStrCount =
        "select count(id) from RecordGroupSharing rgs where rgs.publicLink is not null and"
            + " rgs.publishOnInternet = true";
    return getRecordGroupSharingISearchResults(null, pcg, qStrCount, qStr, null, true);
  }

  @Override
  public ISearchResults<RecordGroupSharing> listAllRecordsPublishedByCommunityMembers(
      PaginationCriteria<RecordGroupSharing> pcg, List<Community> communities) {
    Set<Long> usersInCommunity = new HashSet<>();
    for (Community community : communities) {
      for (Group aGroup : community.getLabGroups()) {
        for (User aUser : aGroup.getMembers()) {
          usersInCommunity.add(aUser.getId());
        }
      }
    }
    List<Long> membersOfUsersGroups = new ArrayList<>(usersInCommunity);
    String qStr =
        "from RecordGroupSharing rgs where rgs.publicLink is not null and rgs.sharedBy.id in"
            + " (:memberIDs)";
    String qStrCount =
        "select count(id) from RecordGroupSharing rgs where rgs.publicLink is not null and"
            + " rgs.sharedBy.id in (:memberIDs)";
    return getRecordGroupSharingISearchResults(
        null, pcg, qStrCount, qStr, membersOfUsersGroups, true);
  }

  private ISearchResults<RecordGroupSharing> getRecordGroupSharingISearchResults(
      User user, PaginationCriteria<RecordGroupSharing> pcg, String qStrCount, String qStr) {
    return getRecordGroupSharingISearchResults(user, pcg, qStrCount, qStr, null, false);
  }

  // isPublishQuery denotes that there will be a sharedBy user as this attribute was added to
  // RecordGroupSharing for the publish work, RSPAC-2460
  // The sql generated returns null for shared records which dont have a sharedBy user, so we dont
  // add a sharedBy clause to the query if isPublishQuery is false
  private ISearchResults<RecordGroupSharing> getRecordGroupSharingISearchResults(
      User user,
      PaginationCriteria<RecordGroupSharing> pcg,
      String qStrCount,
      String qStr,
      List<Long> membersOfUsersGroups,
      boolean isPublishQuery) {
    String searchTerm = "";
    Session session = getSession();
    if (pcg.getSearchCriteria() != null) {
      try {
        searchTerm = (String) pcg.getSearchCriteria().getSearchTermField2Values().get("allFields");
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        log.error("Error getting parsing search terms: ", e);
      }
      String where =
          " and (rgs.shared.editInfo.name LIKE :search or rgs.sharee.displayName LIKE :search"
              + (isPublishQuery ? " or rgs.sharedBy.username LIKE :search " : " ")
              + "or rgs.sharee.username LIKE :search or rgs.shared.owner.username LIKE :search or"
              + " concat(rgs.shared.owner.firstName, ' ', rgs.shared.owner.lastName) LIKE :search"
              + " or rgs.shared.owner.firstName LIKE :search or rgs.shared.owner.lastName LIKE"
              + " :search or concat(rgs.sharee.firstName, ' ', rgs.sharee.lastName) LIKE :search or"
              + " concat('SD',rgs.shared.id) = :globalId or concat('NB',rgs.shared.id) ="
              + " :globalId)";
      if (!isEmpty(searchTerm)) {
        qStr = qStr + where;
        qStrCount = qStrCount + where;
      }
    }

    qStr = qStr + " order by " + translateOrderBy(pcg.getOrderBy()) + " " + pcg.getSortOrder();

    Query<Long> countQ = session.createQuery(qStrCount, Long.class);
    Query<RecordGroupSharing> query = session.createQuery(qStr, RecordGroupSharing.class);
    if (!isEmpty(searchTerm)) {
      countQ.setParameter("search", "%" + searchTerm + "%");
      query.setParameter("search", "%" + searchTerm + "%");
      countQ.setParameter("globalId", searchTerm);
      query.setParameter("globalId", searchTerm);
    }
    if (user != null) {
      countQ.setParameter("id", user.getId());
      query.setParameter("id", user.getId());
    }
    if (membersOfUsersGroups != null) {
      countQ.setParameterList("memberIDs", membersOfUsersGroups);
      query.setParameterList("memberIDs", membersOfUsersGroups);
    }
    Long count = countQ.uniqueResult();

    log.info("Running query {}", qStr);
    query.setMaxResults(pcg.getResultsPerPage());
    query.setFirstResult(pcg.getFirstResultIndex());

    List<RecordGroupSharing> rcgs = query.list();

    ISearchResults<RecordGroupSharing> rc =
        new SearchResultsImpl<RecordGroupSharing>(rcgs, pcg, count);
    return rc;
  }

  private String translateOrderBy(String orderBy) {
    if (orderBy != null) {
      switch (orderBy) {
        case "sharee":
          return "coalesce(rgs.sharee.displayName, concat(rgs.sharee.firstName, ' ',"
              + " rgs.sharee.lastName))";
        case "creationDate":
          return "rgs.creationDate";
        case "name":
        default:
          return "rgs.shared.editInfo.name";
      }
    } else {
      return "rgs.shared.editInfo.name";
    }
  }

  @Override
  public List<RecordGroupSharing> getRecordsSharedByUserToGroup(User user, Group grp) {
    Session session = getSession();
    Query<RecordGroupSharing> query =
        session
            .createQuery(
                " from RecordGroupSharing rgs where rgs.shared.owner.id=:userId "
                    + " and rgs.sharee.id=:grpId",
                RecordGroupSharing.class)
            .setParameter("userId", user.getId())
            .setParameter("grpId", grp.getId());
    List<RecordGroupSharing> rc = query.list();
    return rc;
  }

  @Override
  public List<Long> getRecordIdSharedByUserToGroup(User user, Group grp) {
    Session session = getSession();
    Query<Long> query =
        session
            .createQuery(
                "select rgs.shared.id from RecordGroupSharing rgs "
                    + " where rgs.shared.owner.id = :userId and rgs.sharee.id = :grpId",
                Long.class)
            .setParameter("userId", user.getId())
            .setParameter("grpId", grp.getId());
    List<Long> rc = query.list();
    return rc;
  }

  @Override
  public List<Long> findSharedRecords(List<Long> recordIds) {
    if (recordIds.isEmpty()) {
      return new ArrayList<Long>();
    }
    Session session = getSessionFactory().getCurrentSession();
    Query<Long> q =
        session
            .createQuery(
                "select  distinct rgs.shared.id from RecordGroupSharing rgs "
                    + "where rgs.shared.id in (:recordIds)",
                Long.class)
            .setReadOnly(true);
    q.setParameterList("recordIds", recordIds);
    return q.list();
  }

  @Override
  public List<RecordGroupSharing> getRecordGroupSharingsForRecord(Long recordId) {
    Session session = getSessionFactory().getCurrentSession();
    Query<RecordGroupSharing> query =
        session.createQuery(
            " from RecordGroupSharing rgs where rgs.shared.id=:recordId", RecordGroupSharing.class);
    query.setParameter("recordId", recordId);
    List<RecordGroupSharing> rc = query.list();
    return rc;
  }
}
