package com.researchspace.dao.hibernate;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.GroupDao;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.views.UserView;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("groupDao")
public class GroupDaoHibernateImpl extends GenericDaoHibernate<Group, Long> implements GroupDao {

  public GroupDaoHibernateImpl() {
    super(Group.class);
  }

  @Override
  public List<UserView> getCandidateMembersOfCollabGroup(Long id) {
    Query<UserView> q =
        getSession()
            .createQuery(
                // get users from lab groups of PIs
                "select new com.researchspace.model.views.UserView (ug2.user.id, ug2.user.username,"
                    + " ug2.user.email, concat(ug2.user.lastName, ', ', ug2.user.firstName)) from"
                    + " UserGroup ug2  where ug2.group.id in ("
                    // get all labgroups where pi is already in collab group
                    + "select group.id  from UserGroup ug where ug.user.id in ("
                    // get existing PIs in Collab group
                    + " select user.id from  UserGroup ug where ug.group.id=:id "
                    + "and ug.group.groupType=:groupType and ug.roleInGroup=:roleInGroup"
                    + ") and ug.group.groupType=:gt2"
                    + ")"
                    // that aren't already in the collaboration group.
                    + " and ug2.user.id not in ("
                    + " select user.id from UserGroup cg where cg.group.id=:groupId"
                    + ")",
                UserView.class);

    q.setParameter("id", id);
    q.setParameter("groupId", id);
    q.setParameter("groupType", GroupType.COLLABORATION_GROUP);
    q.setParameter("roleInGroup", RoleInGroup.PI);
    q.setParameter("gt2", GroupType.LAB_GROUP);
    return q.list();
  }

  @Override
  public Group getGroupWithCommunities(Long groupId) {
    return (Group)
        sessionFactory
            .getCurrentSession()
            // must be left join to get collab groups as well if need be
            // which don't join to communities
            .createQuery("from Group g left join fetch g.communities where g.id=:id")
            .setParameter("id", groupId)
            .uniqueResult();
  }

  @Override
  public ISearchResults<Group> list(PaginationCriteria<Group> pgCrit) {
    Session session = sessionFactory.getCurrentSession();
    CriteriaBuilder builder = session.getCriteriaBuilder();
    if (pgCrit.getSearchCriteria() != null) {
      GroupSearchCriteria filter = (GroupSearchCriteria) pgCrit.getSearchCriteria();
      if (filter == null) {
        return createEmptyResultSet(pgCrit);
      }
      // If the search terms cannot be parsed we return no results, matching the pre-migration
      // behaviour: an unparseable filter must not fall through to an unfiltered "list all" query.
      try {
        filter.getSearchTermField2Values();
      } catch (Exception e) {
        log.error("problem with converting search criteria to search terms", e);
        return createEmptyResultSet(pgCrit);
      }
    }

    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<Group> countRoot = countQuery.from(Group.class);
    List<Predicate> countPredicates = buildGroupPredicates(pgCrit, builder, countRoot);
    countQuery.select(builder.countDistinct(countRoot.get("id")));
    if (!countPredicates.isEmpty()) {
      countQuery.where(countPredicates.toArray(new Predicate[0]));
    }

    Long count = session.createQuery(countQuery).getSingleResult();
    if (count == 0) {
      return createEmptyResultSet(pgCrit);
    }

    CriteriaQuery<Long> idQuery = builder.createQuery(Long.class);
    Root<Group> idRoot = idQuery.from(Group.class);
    List<Predicate> idPredicates = buildGroupPredicates(pgCrit, builder, idRoot);
    idQuery.select(idRoot.get("id")).distinct(true);
    if (!idPredicates.isEmpty()) {
      idQuery.where(idPredicates.toArray(new Predicate[0]));
    }
    applyGroupOrder(pgCrit, builder, idRoot, idQuery);
    Query<Long> idQueryExec =
        session
            .createQuery(idQuery)
            .setFirstResult(pgCrit.getFirstResultIndex())
            .setMaxResults(pgCrit.getResultsPerPage());
    List<Long> ids = idQueryExec.list();

    if (ids.isEmpty()) {
      return createEmptyResultSet(pgCrit);
    }

    CriteriaQuery<Group> fetchQuery = builder.createQuery(Group.class);
    Root<Group> fetchRoot = fetchQuery.from(Group.class);
    fetchQuery.select(fetchRoot).distinct(true);
    fetchQuery.where(fetchRoot.get("id").in(ids));
    fetchRoot.join("owner", JoinType.LEFT);
    if (pgCrit.getSearchCriteria() != null) {
      GroupSearchCriteria filter = (GroupSearchCriteria) pgCrit.getSearchCriteria();
      if (filter != null && filter.isLoadCommunity()) {
        fetchRoot.fetch("communities", JoinType.LEFT);
      }
    }
    applyGroupOrder(pgCrit, builder, fetchRoot, fetchQuery);

    return new SearchResultsImpl<>(session.createQuery(fetchQuery).list(), pgCrit, count);
  }

  @Override
  public Group getByUniqueName(String uniqueName) {
    return getSession()
        .createQuery("from Group where uniqueName=:uniqueName", Group.class)
        .setParameter("uniqueName", uniqueName)
        .uniqueResult();
  }

  @Override
  public Group getByCommunalGroupFolderId(Long folderId) {
    return getSession()
        .createQuery("from Group where communalGroupFolderId=:folderId", Group.class)
        .setParameter("folderId", folderId)
        .uniqueResult();
  }

  @Override
  public List<Group> getForOwner(User owner) {
    return getSession()
        .createQuery("from Group where owner=:owner", Group.class)
        .setParameter("owner", owner)
        .getResultList();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Group> searchGroups(String term) {
    return getSession()
        .createQuery(
            "select distinct g from Group g where lower(g.displayName) like :term "
                + "order by g.displayName asc",
            Group.class)
        .setParameter("term", "%" + term.toLowerCase() + "%")
        .list();
  }

  private List<Predicate> buildGroupPredicates(
      PaginationCriteria<Group> pgCrit, CriteriaBuilder builder, Root<Group> root) {
    List<Predicate> predicates = new java.util.ArrayList<>();
    if (pgCrit.getSearchCriteria() != null) {
      GroupSearchCriteria filter = (GroupSearchCriteria) pgCrit.getSearchCriteria();
      Map<String, Object> searchTerms;
      try {
        searchTerms = filter.getSearchTermField2Values();
      } catch (Exception e) {
        log.error("problem with converting search criteria to search terms", e);
        return predicates;
      }

      for (Entry<String, Object> entry : searchTerms.entrySet()) {
        String term = entry.getValue().toString().toLowerCase();
        if ("displayName".equals(entry.getKey())) {
          predicates.add(builder.like(builder.lower(root.get("displayName")), "%" + term + "%"));
        }
        if ("uniqueName".equals(entry.getKey())) {
          predicates.add(builder.like(builder.lower(root.get("uniqueName")), "%" + term + "%"));
        }
        if ("groupType".equals(entry.getKey())) {
          predicates.add(builder.equal(root.get("groupType"), entry.getValue()));
        }
      }

      if (filter.getCommunityId() != null) {
        Join<Group, ?> communityJoin = root.join("communities", JoinType.INNER);
        predicates.add(builder.equal(communityJoin.get("id"), filter.getCommunityId()));
      }

      if (filter.isOnlyPublicProfiles()) {
        predicates.add(builder.notEqual(root.get("privateProfile"), true));
      }
    }
    return predicates;
  }

  private void applyGroupOrder(
      PaginationCriteria<Group> pgCrit,
      CriteriaBuilder builder,
      Root<Group> root,
      CriteriaQuery<?> query) {
    if (pgCrit.getOrderBy() == null) {
      return;
    }
    if ("piname".equals(pgCrit.getOrderBy())) {
      Join<Group, ?> ownerJoin = root.join("owner", JoinType.LEFT);
      if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
        query.orderBy(builder.asc(ownerJoin.get("lastName")));
      } else {
        query.orderBy(builder.desc(ownerJoin.get("lastName")));
      }
    } else if ("owner.username".equals(pgCrit.getOrderBy())) {
      Join<Group, ?> ownerJoin = root.join("owner", JoinType.LEFT);
      if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
        query.orderBy(builder.asc(ownerJoin.get("username")));
      } else {
        query.orderBy(builder.desc(ownerJoin.get("username")));
      }
    } else if ("owner.lastName".equals(pgCrit.getOrderBy())) {
      Join<Group, ?> ownerJoin = root.join("owner", JoinType.LEFT);
      if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
        query.orderBy(builder.asc(ownerJoin.get("lastName")));
      } else {
        query.orderBy(builder.desc(ownerJoin.get("lastName")));
      }
    } else if (pgCrit.isOrderBySafe(pgCrit.getOrderBy())) {
      if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
        query.orderBy(builder.asc(root.get(pgCrit.getOrderBy())));
      } else {
        query.orderBy(builder.desc(root.get(pgCrit.getOrderBy())));
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Group> getGroups(Collection<Long> groupIds) {
    if (groupIds.isEmpty()) {
      return Collections.emptyList();
    }
    Session session = getSessionFactory().getCurrentSession();
    return session
        .createQuery("from Group g left join fetch g.communities where g.id in :groupIds")
        .setParameterList("groupIds", groupIds)
        .list();
  }
}
