package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.ContainerDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.record.IRecordFactory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.query.Query;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ContainerDaoHibernateImpl extends InventoryDaoHibernate<Container, Long>
    implements ContainerDao {

  @Autowired protected IRecordFactory recordFactory;

  public ContainerDaoHibernateImpl(Class<Container> persistentClass) {
    super(persistentClass);
  }

  public ContainerDaoHibernateImpl() {
    super(Container.class);
  }

  @Override
  public ISearchResults<Container> getTopContainersForUser(
      PaginationCriteria<Container> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      ContainerType type,
      User user) {

    return getContainerSearchResults(pgCrit, ownedBy, deletedItemsOption, type, true, user);
  }

  @Override
  public ISearchResults<Container> getAllContainersForUser(
      PaginationCriteria<Container> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user) {

    return getContainerSearchResults(pgCrit, ownedBy, deletedItemsOption, null, false, user);
  }

  private SearchResultsImpl<Container> getContainerSearchResults(
      PaginationCriteria<Container> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      ContainerType type,
      boolean onlyTopContainers,
      User user) {

    // prepare permission limiting query fragment
    InventoryReadQueryContext context = readQueryContext(user);
    String ownedByAndPermittedItemsQueryFragment = context.ownedByAndPermitted(this, ownedBy);

    // prepare type limiting fragment query
    String typeQueryFragment =
        (type == null) ? "and containerType!='WORKBENCH' " : "and containerType='" + type + "' ";

    // get the page of results
    if (pgCrit == null) {
      pgCrit = PaginationCriteria.createDefaultForClass(Container.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedItemsOption);
    String onlyTopContainersFragment = onlyTopContainers ? "parentLocation is null " : " ";
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    // find the total count
    Query<Long> countQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(s) from Container s where "
                    + connectSqlConditionsWithAnd(deletedFragment, onlyTopContainersFragment)
                    + ownedByAndPermittedItemsQueryFragment
                    + typeQueryFragment,
                Long.class);
    Query<Long> countQueryWithParams = context.bind(countQueryBase, ownedBy);
    long allContainersCount = countQueryWithParams.getSingleResult();
    if (allContainersCount == 0) {
      return new SearchResultsImpl<>(new ArrayList<>(), pgCrit, 0);
    }

    Query<Container> containerQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from Container where "
                    + connectSqlConditionsWithAnd(deletedFragment, onlyTopContainersFragment)
                    + ownedByAndPermittedItemsQueryFragment
                    + typeQueryFragment
                    + orderByFragment)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);

    Query<Container> containerPageQueryWithParams = context.bind(containerQueryBase, ownedBy);
    List<Container> pageOfContainers = containerPageQueryWithParams.list();
    return new SearchResultsImpl<>(pageOfContainers, pgCrit, allContainersCount);
  }

  @Override
  public Container getContainerByLocationId(Long id) {
    return (Container)
        sessionFactory
            .getCurrentSession()
            .createQuery("select c from Container c left join c.locations cl where cl.id=:id")
            .setParameter("id", id)
            .uniqueResult();
  }

  @Override
  public Container getWorkbenchForUser(User user) {
    Container workbench =
        (Container)
            sessionFactory
                .getCurrentSession()
                .createQuery("from Container where containerType = 'WORKBENCH' and owner=:owner")
                .setParameter("owner", user)
                .uniqueResult();

    if (workbench == null) {
      Container newWorkbench = recordFactory.createWorkbench(user);
      save(newWorkbench);
      workbench = getWorkbenchForUser(user);
    }
    return workbench;
  }

  @Override
  public Long getWorkbenchIdForUser(User user) {
    Long workbenchId =
        (Long)
            sessionFactory
                .getCurrentSession()
                .createNativeQuery(
                    "select id as containerId from Container where containerType = 'WORKBENCH' and"
                        + " owner_id=:userId")
                .addScalar("containerId", StandardBasicTypes.LONG)
                .setParameter("userId", user.getId())
                .uniqueResult();

    if (workbenchId == null) {
      workbenchId = getWorkbenchForUser(user).getId();
    }
    return workbenchId;
  }

  @Override
  public List<Container> getAllUsingImage(FileProperty fileProperty) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from Container where imageFileProperty=:fileProperty OR"
                + " thumbnailFileProperty=:fileProperty OR"
                + " locationsImageFileProperty=:fileProperty",
            Container.class)
        .setParameter("fileProperty", fileProperty)
        .list();
  }

  @Override
  public Set<Long> getReadableActiveContainerIds(Set<Long> containerIds, User actor) {
    if (containerIds.isEmpty()) {
      return Set.of();
    }
    if (actor.hasSysadminRole()) {
      return new LinkedHashSet<>(
          sessionFactory
              .getCurrentSession()
              .createQuery(
                  "select container.id from Container container "
                      + "where container.id in (:containerIds) and container.deleted=false",
                  Long.class)
              .setParameterList("containerIds", containerIds)
              .list());
    }
    InventoryReadQueryContext context = readQueryContext(actor);
    String readableContainerPredicate = context.readableContainerPredicate(this, "container");
    StringBuilder hql =
        new StringBuilder("select container.id from Container container ")
            .append("where container.id in (:containerIds) and container.deleted=false and ")
            .append(readableContainerPredicate);
    Query<Long> query =
        sessionFactory
            .getCurrentSession()
            .createQuery(hql.toString(), Long.class)
            .setParameterList("containerIds", containerIds);
    context.bind(query, null);
    return new LinkedHashSet<>(query.list());
  }
}
