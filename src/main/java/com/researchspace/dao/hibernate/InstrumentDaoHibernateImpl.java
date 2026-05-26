package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository(value = "instrumentDao")
public class InstrumentDaoHibernateImpl extends InventoryDaoHibernate<Instrument, Long>
    implements InstrumentDao {

  private String defaultTemplateOwner;

  public InstrumentDaoHibernateImpl(Class<Instrument> persistentClass) {
    super(persistentClass);
  }

  public InstrumentDaoHibernateImpl() {
    super(Instrument.class);
  }

  @Override
  public ISearchResults<Instrument> getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user) {

    List<String> userGroupMembers =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(user);
    List<String> userGroupsUniqueNames =
        user.getGroups().stream().map(Group::getUniqueName).collect(Collectors.toList());
    List<String> visibleOwners = invPermissionUtils.getOwnersVisibleWithUserRole(user);
    String permittedFragment =
        getOwnedByAndPermittedItemsSqlQueryFragment(
            ownedBy, user, userGroupMembers, userGroupsUniqueNames, visibleOwners);

    if (pgCrit == null) {
      pgCrit = PaginationCriteria.createDefaultForClass(Instrument.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedOption);
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    Query<Long> countQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(i) from Instrument i where "
                    + connectSqlConditionsWithAnd(deletedFragment, " DTYPE='Instrument' ")
                    + permittedFragment,
                Long.class);
    Query<Long> countQueryWithParams =
        addQueryParams(
            ownedBy, user, countQuery, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    long totalCount = countQueryWithParams.getSingleResult();
    if (totalCount == 0) {
      return new SearchResultsImpl<>(new ArrayList<>(), pgCrit, 0);
    }

    Query<Instrument> pageQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from Instrument where "
                    + connectSqlConditionsWithAnd(deletedFragment, " DTYPE='Instrument' ")
                    + permittedFragment
                    + orderByFragment,
                Instrument.class)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<Instrument> pageQueryWithParams =
        addQueryParams(
            ownedBy, user, pageQuery, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    List<Instrument> page = pageQueryWithParams.list();
    return new SearchResultsImpl<>(page, pgCrit, totalCount);
  }

  @Override
  public List<Instrument> findInstrumentsByName(String name, User user) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from Instrument where name=:name and owner=:owner", Instrument.class)
        .setParameter("name", name)
        .setParameter("owner", user)
        .list();
  }

  /*
   * ============
   *  for tests
   * ============
   */
  @Override
  public void resetDefaultTemplateOwner() {
    defaultTemplateOwner = null;
  }
}
