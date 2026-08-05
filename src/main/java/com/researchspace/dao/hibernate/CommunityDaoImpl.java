package com.researchspace.dao.hibernate;

import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.Community;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.hibernate.Session;
import org.hibernate.graph.GraphParser;
import org.hibernate.graph.RootGraph;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository("communityDao")
public class CommunityDaoImpl extends GenericDaoHibernate<Community, Long> implements CommunityDao {

  public CommunityDaoImpl() {
    super(Community.class);
  }

  public ISearchResults<Community> listAll(User subject, PaginationCriteria<Community> pgCrit) {
    if (pgCrit == null) {
      throw new IllegalArgumentException("pagination criteria must not be null!");
    }
    Session session = getSession();

    CriteriaBuilder builder = session.getCriteriaBuilder();

    CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
    Root<Community> countRoot = countQuery.from(Community.class);
    List<Predicate> predicates = buildCommunityPredicates(pgCrit, builder, countRoot);
    countQuery.select(builder.countDistinct(countRoot.get("id")));
    if (!predicates.isEmpty()) {
      countQuery.where(predicates.toArray(new Predicate[0]));
    }

    Long count = session.createQuery(countQuery).getSingleResult();
    if (count == 0) {
      return createEmptyResultSet(pgCrit);
    }

    CriteriaQuery<Long> idQuery = builder.createQuery(Long.class);
    Root<Community> idRoot = idQuery.from(Community.class);
    List<Predicate> idPredicates = buildCommunityPredicates(pgCrit, builder, idRoot);
    idQuery.select(idRoot.get("id")).distinct(true);
    if (!idPredicates.isEmpty()) {
      idQuery.where(idPredicates.toArray(new Predicate[0]));
    }
    applyCommunityOrder(pgCrit, builder, idRoot, idQuery);

    Query<Long> idQueryExec =
        session
            .createQuery(idQuery)
            .setFirstResult(pgCrit.getFirstResultIndex())
            .setMaxResults(pgCrit.getResultsPerPage());
    List<Long> comms = idQueryExec.list();
    if (comms.isEmpty()) {
      return createEmptyResultSet(pgCrit);
    }

    CriteriaQuery<Community> fetchQuery = builder.createQuery(Community.class);
    Root<Community> fetchRoot = fetchQuery.from(Community.class);
    fetchRoot.fetch("admins", JoinType.LEFT);
    fetchQuery.select(fetchRoot).distinct(true);
    fetchQuery.where(fetchRoot.get("id").in(comms));
    applyCommunityOrder(pgCrit, builder, fetchRoot, fetchQuery);

    return new SearchResultsImpl<>(session.createQuery(fetchQuery).list(), pgCrit, count);
  }

  private List<Predicate> buildCommunityPredicates(
      PaginationCriteria<Community> pgCrit, CriteriaBuilder builder, Root<Community> root) {
    List<Predicate> predicates = new ArrayList<>();
    if (pgCrit.getSearchCriteria() != null) {
      FilterCriteria sc = pgCrit.getSearchCriteria();
      Map<String, Object> key2Value;
      try {
        key2Value = sc.getSearchTermField2Values();
        for (Entry<String, Object> entry : key2Value.entrySet()) {
          if ("displayName".equals(entry.getKey())) {
            String term = entry.getValue().toString().toLowerCase();
            predicates.add(builder.like(builder.lower(root.get("displayName")), "%" + term + "%"));
          }
        }
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        log.error("Error getting parsing search terms: ", e);
      }
    }
    return predicates;
  }

  private void applyCommunityOrder(
      PaginationCriteria<Community> pgCrit,
      CriteriaBuilder builder,
      Root<Community> root,
      CriteriaQuery<?> query) {
    if (pgCrit.getOrderBy() != null && pgCrit.isOrderBySafe(pgCrit.getOrderBy())) {
      if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
        query.orderBy(builder.asc(root.get(pgCrit.getOrderBy())));
      } else {
        query.orderBy(builder.desc(root.get(pgCrit.getOrderBy())));
      }
    }
  }

  public List<User> listAdminsForCommunity(Long communityId) {
    Community comm =
        getSession()
            .createQuery("from Community where id=:id", Community.class)
            .setParameter("id", communityId)
            .uniqueResult();
    return new ArrayList<>(comm.getAdmins());
  }

  /**
   * Finds every Community to which the user belongs. This means that there is at least one lab
   * group in a community to which the user belongs.
   *
   * @param userId
   * @return list of communities
   */
  public List<Community> listCommunitiesForUser(Long userId) {
    CriteriaBuilder builder = getSession().getCriteriaBuilder();

    // eagerly fetch path labGroups.userGroups.user.id
    RootGraph<Community> communityRootGraph =
        GraphParser.parse(Community.class, "labGroups(userGroups(user))", getSession());

    CriteriaQuery<Community> criteriaQuery = builder.createQuery(Community.class);
    Root<Community> root = criteriaQuery.from(Community.class);
    criteriaQuery
        .select(root)
        .where(
            builder.equal(
                root.join("labGroups").join("userGroups").join("user").get("id"), userId));

    Query<Community> query = getSession().createQuery(criteriaQuery);
    query.applyFetchGraph(communityRootGraph);
    return query.list();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Community> listCommunitiesForAdmin(Long userId) {
    return getSession()
        .createQuery(
            "select distinct c from Community c join c.admins admin where admin.id=:userId",
            Community.class)
        .setParameter("userId", userId)
        .list();
  }

  @Override()
  public Community getCommunityForGroup(Long groupId) {
    return getSession()
        .createQuery(
            "select c from Community c join c.labGroups lg where lg.id=:groupId", Community.class)
        .setParameter("groupId", groupId)
        .uniqueResult();
  }

  @Override
  public Community getCommunityWithGroupsAndAdmins(Long communityId) {
    return getSession()
        .createQuery(
            "select distinct c from Community c "
                + "left join fetch c.admins "
                + "left join fetch c.labGroups "
                + "where c.id=:communityId",
            Community.class)
        .setParameter("communityId", communityId)
        .uniqueResult();
  }

  @Override
  public Community getWithAdmins(Long communityId) {
    return getSession()
        .createQuery(
            "select distinct c from Community c left join fetch c.admins where c.id=:communityId",
            Community.class)
        .setParameter("communityId", communityId)
        .uniqueResult();
  }

  @Override
  public boolean hasCommunity(User admin) {
    Long count =
        getSession()
            .createQuery(
                "select count(c) from Community c join c.admins admin where admin.id=:adminId",
                Long.class)
            .setParameter("adminId", admin.getId())
            .uniqueResult();
    return count > 0;
  }
}
