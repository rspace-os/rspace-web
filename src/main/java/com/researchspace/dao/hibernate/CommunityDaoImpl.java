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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
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

    // load admins
    Criteria listQuery =
        session.createCriteria(Community.class).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);

    if (pgCrit.getSearchCriteria() != null) {
      FilterCriteria sc = pgCrit.getSearchCriteria();

      Map<String, Object> key2Value;
      try {
        key2Value = sc.getSearchTermField2Values();
        for (Entry<String, Object> entry : key2Value.entrySet()) {
          if ("displayName".equals(entry.getKey())) {
            listQuery.add(
                Restrictions.disjunction()
                    .add(
                        Restrictions.ilike(
                            "displayName", entry.getValue().toString(), MatchMode.ANYWHERE)));
          }
        }
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        log.error("Error getting parsing search terms: ", e);
      }
    }

    Long count = getTotalCommunityCount(listQuery);
    listQuery.setProjection(null);
    // listQuery.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
    if (count == 0) {
      return createEmptyResultSet(pgCrit);
    }

    DatabasePaginationUtils.addPaginationCriteriaToHibernateCriteria(pgCrit, listQuery);

    listQuery.setProjection(Projections.distinct(Projections.id()));
    @SuppressWarnings("unchecked")
    // now, retrieve by id, paginate, load associated admins and sort see JIRA-97
    List<Long> comms = listQuery.list();
    if (!comms.isEmpty()) {
      listQuery =
          getSession()
              .createCriteria(Community.class)
              .add(Restrictions.in("id", comms))
              .setFetchMode("admins", FetchMode.JOIN)
              .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
      if (pgCrit.getOrderBy() != null) {
        if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
          listQuery.addOrder(Order.asc(pgCrit.getOrderBy()));
        } else {
          listQuery.addOrder(Order.desc(pgCrit.getOrderBy()));
        }
      }
      return new SearchResultsImpl<Community>(listQuery.list(), pgCrit, count);
    } else {
      return createEmptyResultSet(pgCrit);
    }
  }

  public Long getTotalCommunityCount(Criteria crit) {
    crit.setProjection(Projections.count("id"));
    Long count = (Long) crit.uniqueResult();
    return count;
  }

  public List<User> listAdminsForCommunity(Long communityId) {
    Criteria listQuery = getSession().createCriteria(Community.class);
    listQuery.add(Restrictions.idEq(communityId));
    Community comm = (Community) listQuery.uniqueResult();
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
        .createCriteria(Community.class)
        .createCriteria("admins")
        .add(Restrictions.idEq(userId))
        .list();
  }

  @Override()
  public Community getCommunityForGroup(Long groupId) {
    Community rc =
        (Community)
            getSession()
                .createCriteria(Community.class)
                .createCriteria("labGroups")
                .add(Restrictions.idEq(groupId))
                .uniqueResult();
    return rc;
  }

  @Override
  public Community getCommunityWithGroupsAndAdmins(Long communityId) {
    Criteria query =
        getSession()
            .createCriteria(Community.class)
            .setFetchMode("admins", FetchMode.JOIN)
            .setFetchMode("labGroups", FetchMode.JOIN)
            .add(Restrictions.idEq(communityId));
    return (Community) query.uniqueResult();
  }

  @Override
  public Community getWithAdmins(Long communityId) {
    Criteria query =
        getSession()
            .createCriteria(Community.class)
            .setFetchMode("admins", FetchMode.JOIN)
            .add(Restrictions.idEq(communityId));
    return (Community) query.uniqueResult();
  }

  @Override
  public boolean hasCommunity(User admin) {
    Criteria countQuery =
        getSession()
            .createCriteria(Community.class)
            .createCriteria("admins")
            .add(Restrictions.eq("id", admin.getId()))
            .setProjection(Projections.count("id"));
    Long count = (Long) countQuery.uniqueResult();
    return count > 0;
  }
}
