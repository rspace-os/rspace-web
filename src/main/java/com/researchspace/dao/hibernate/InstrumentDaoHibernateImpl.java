package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.inventory.Instrument;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
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
  public Optional<Instrument> lockById(Long id) {
    // Flush before eviction so pending instrument and child changes remain persisted.
    getSession().flush();
    Instrument cached = getSession().get(Instrument.class, id);
    if (cached != null) {
      getSession().evict(cached);
    }
    // A locking HQL entity read reloads the instrument's current permission columns without
    // joining eager associations. A locking refresh would also lock owners and containers,
    // before a batch has acquired all its instrument rows.
    return getSession()
        .createQuery(
            "from Instrument instrument where instrument.id = :id and type(instrument) ="
                + " Instrument",
            Instrument.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public boolean hasLockedTransferAuthority(User subject, User owner) {
    List<UserGroup> subjectMemberships =
        getSession()
            .createQuery(
                "from UserGroup membership "
                    + "where membership.user.id = :subjectId "
                    + "and membership.roleInGroup in :transferRoles",
                UserGroup.class)
            .setParameter("subjectId", subject.getId())
            .setParameterList("transferRoles", List.of(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN))
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    if (subjectMemberships.isEmpty()) {
      return false;
    }
    Set<Long> subjectGroupIds =
        subjectMemberships.stream()
            .map(membership -> membership.getGroup().getId())
            .collect(Collectors.toSet());
    List<UserGroup> ownerMemberships =
        getSession()
            .createQuery(
                "from UserGroup membership "
                    + "where membership.user.id = :ownerId and membership.group.id in :groupIds",
                UserGroup.class)
            .setParameter("ownerId", owner.getId())
            .setParameterList("groupIds", subjectGroupIds)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    return !ownerMemberships.isEmpty();
  }

  @Override
  public ISearchResults<Instrument> getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      String searchTerm,
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
    String nameFragment =
        StringUtils.isNotBlank(searchTerm) ? " lower(name) like lower(:searchTerm) " : "";
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    Query<Long> countQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(i) from Instrument i where "
                    + connectSqlConditionsWithAnd(
                        deletedFragment, " type(i) = Instrument ", nameFragment)
                    + permittedFragment,
                Long.class);
    Query<Long> countQueryWithParams =
        addQueryParams(
            ownedBy, user, countQuery, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    if (StringUtils.isNotBlank(searchTerm)) {
      countQueryWithParams.setParameter("searchTerm", "%" + searchTerm + "%");
    }
    long totalCount = countQueryWithParams.getSingleResult();
    if (totalCount == 0) {
      return new SearchResultsImpl<>(new ArrayList<>(), pgCrit, 0);
    }

    Query<Instrument> pageQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from Instrument i where "
                    + connectSqlConditionsWithAnd(
                        deletedFragment, " type(i) = Instrument ", nameFragment)
                    + permittedFragment
                    + orderByFragment,
                Instrument.class)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<Instrument> pageQueryWithParams =
        addQueryParams(
            ownedBy, user, pageQuery, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    if (StringUtils.isNotBlank(searchTerm)) {
      pageQueryWithParams.setParameter("searchTerm", "%" + searchTerm + "%");
    }
    List<Instrument> page = pageQueryWithParams.list();
    return new SearchResultsImpl<>(page, pgCrit, totalCount);
  }

  @Override
  public List<Instrument> getAllUsingImage(FileProperty fileProperty) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from Instrument where imageFileProperty=:fileProperty"
                + " OR thumbnailFileProperty=:fileProperty",
            Instrument.class)
        .setParameter("fileProperty", fileProperty)
        .list();
  }

  @Override
  public List<Instrument> findInstrumentsByName(String name, User user) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from Instrument where editInfo.name=:name and owner=:owner", Instrument.class)
        .setParameter("name", name)
        .setParameter("owner", user)
        .list();
  }

  @Override
  public ISearchResults<Instrument> getInstrumentsForTemplate(
      PaginationCriteria<Instrument> pgCrit,
      Long templateId,
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
                    + connectSqlConditionsWithAnd(
                        deletedFragment,
                        " type(i) = Instrument ",
                        " instrumentTemplate.id=:templateId ")
                    + permittedFragment,
                Long.class)
            .setParameter("templateId", templateId);
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
                "from Instrument i where "
                    + connectSqlConditionsWithAnd(
                        deletedFragment,
                        " type(i) = Instrument ",
                        " instrumentTemplate.id=:templateId ")
                    + permittedFragment
                    + orderByFragment,
                Instrument.class)
            .setParameter("templateId", templateId)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<Instrument> pageQueryWithParams =
        addQueryParams(
            ownedBy, user, pageQuery, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    List<Instrument> page = pageQueryWithParams.list();
    return new SearchResultsImpl<>(page, pgCrit, totalCount);
  }

  @Override
  public List<Instrument> getInstrumentsLinkingOlderTemplateVersionForUser(
      Long templateId, Long version, User user) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from Instrument where owner=:owner and deleted=false"
                + " and instrumentTemplate.id=:parentTemplateId"
                + " and templateLinkedVersion < :parentTemplateMaxVersion",
            Instrument.class)
        .setParameter("owner", user)
        .setParameter("parentTemplateId", templateId)
        .setParameter("parentTemplateMaxVersion", version)
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
