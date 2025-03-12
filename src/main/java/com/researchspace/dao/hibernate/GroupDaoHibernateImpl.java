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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Session;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.SimpleExpression;
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
    Criteria countCrit = session.createCriteria(Group.class);
    Criteria crit = session.createCriteria(Group.class);
    if (pgCrit.getSearchCriteria() != null) {
      GroupSearchCriteria filter = (GroupSearchCriteria) pgCrit.getSearchCriteria();
      Map<String, Object> searchTerms = null;
      try {
        searchTerms = filter.getSearchTermField2Values();
      } catch (Exception e) {
        log.error("problem with converting search critera to search terms", e);
        return createEmptyResultSet(pgCrit);
      }

      for (Entry<String, Object> entry : searchTerms.entrySet()) {
        if ("displayName".equals(entry.getKey())) {
          crit.add(
              Restrictions.ilike("displayName", entry.getValue().toString(), MatchMode.ANYWHERE));
          countCrit.add(
              Restrictions.ilike("displayName", entry.getValue().toString(), MatchMode.ANYWHERE));
        }
        if ("uniqueName".equals(entry.getKey())) {
          crit.add(
              Restrictions.ilike("uniqueName", entry.getValue().toString(), MatchMode.ANYWHERE));
          countCrit.add(
              Restrictions.ilike("uniqueName", entry.getValue().toString(), MatchMode.ANYWHERE));
        }
        if ("groupType".equals(entry.getKey())) {
          crit.add(Restrictions.eq("groupType", entry.getValue()));
          countCrit.add(Restrictions.eq("groupType", entry.getValue()));
        }
      }

      if (filter.getCommunityId() != null) {
        crit.createAlias("communities", "community")
            .add(Restrictions.eq("community.id", filter.getCommunityId()));
        countCrit
            .createAlias("communities", "community")
            .add(Restrictions.eq("community.id", filter.getCommunityId()));
      }

      if (filter.isOnlyPublicProfiles()) {
        SimpleExpression publicProfileRestriction = Restrictions.ne("privateProfile", true);
        crit.add(publicProfileRestriction);
        countCrit.add(publicProfileRestriction);
      }
    }
    countCrit.setProjection(Projections.countDistinct("id"));

    Long count = (Long) countCrit.uniqueResult();
    if (count == 0) {
      return createEmptyResultSet(pgCrit);
    }
    crit.setProjection(Projections.distinct(Projections.id()));
    crit.createAlias("owner", "owner");
    if (pgCrit.getOrderBy() != null && pgCrit.getOrderBy().equals("piname")) {
      crit.setMaxResults(pgCrit.getResultsPerPage());
      crit.setFirstResult(pgCrit.getFirstResultIndex());
      if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
        crit.addOrder(Order.asc("owner.lastName"));
      } else {
        crit.addOrder(Order.desc("owner.lastName"));
      }
    } else {
      DatabasePaginationUtils.addPaginationCriteriaToHibernateCriteria(pgCrit, crit);
    }
    List<Long> ids = crit.list();

    Criteria criteria2 =
        session
            .createCriteria(Group.class)
            .add(Restrictions.in("id", ids))
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    criteria2.createAlias("owner", "owner");
    if (pgCrit.getSearchCriteria() != null) {
      GroupSearchCriteria filter = (GroupSearchCriteria) pgCrit.getSearchCriteria();
      if (filter != null && filter.isLoadCommunity()) {
        criteria2.setFetchMode("communities", FetchMode.JOIN);
      }
    }
    if (pgCrit.getOrderBy() != null) {
      if (SortOrder.ASC.equals(pgCrit.getSortOrder())) {
        criteria2.addOrder(Order.asc(pgCrit.getOrderBy()));
      } else {
        criteria2.addOrder(Order.desc(pgCrit.getOrderBy()));
      }
    }

    return new SearchResultsImpl<Group>(criteria2.list(), pgCrit, count);
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
    Session session = getSessionFactory().getCurrentSession();
    Criteria criteria = session.createCriteria(Group.class, "group");
    criteria.add(Restrictions.ilike("displayName", term, MatchMode.ANYWHERE));
    criteria.addOrder(Order.asc("displayName"));
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    List<Group> result = criteria.list();
    return result;
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
