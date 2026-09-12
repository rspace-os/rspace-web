package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class SubSampleDaoHibernateImpl extends InventoryDaoHibernate<SubSample, Long>
    implements SubSampleDao {

  public SubSampleDaoHibernateImpl(Class<SubSample> persistentClass) {
    super(persistentClass);
  }

  public SubSampleDaoHibernateImpl() {
    super(SubSample.class);
  }

  @Override
  public ISearchResults<SubSample> getSubSamplesForUser(
      PaginationCriteria<SubSample> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user) {

    // prepare permission limiting query fragment
    List<String> userGroupMembers =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(user);
    List<String> userGroupsUniqueNames =
        user.getGroups().stream().map(Group::getUniqueName).collect(Collectors.toList());
    List<String> visibleOwners = invPermissionUtils.getOwnersVisibleWithUserRole(user);
    String ownedByAndPermittedItemsQueryFragment =
        getOwnedByAndPermittedItemsSqlQueryFragment(
            ownedBy, user, userGroupMembers, userGroupsUniqueNames, visibleOwners, "ss.sample.");

    /*
     * Fragment limiting to non-template subsamples. With the Sample/SampleTemplate single-table
     * hierarchy the legacy 'template' boolean property no longer exists; the discriminator-based
     * '.class' comparison expresses the same restriction. The 'type(ss.sample) = Sample' form is
     * NOT used because Hibernate 5's classic HQL translator cannot apply type() to an
     * implicit-join path ("could not resolve property: class").
     */
    String nonTemplateFragment = "ss.sample.class = Sample ";

    if (pgCrit == null) {
      pgCrit = PaginationCriteria.createDefaultForClass(SubSample.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedItemsOption);
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    Query<Long> countQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(ss) from SubSample ss where "
                    + connectSqlConditionsWithAnd(nonTemplateFragment, deletedFragment)
                    + ownedByAndPermittedItemsQueryFragment,
                Long.class);
    Query<Long> countQueryWithParams =
        addQueryParams(
            ownedBy, user, countQueryBase, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    long allSubSamplesCount = countQueryWithParams.getSingleResult();
    if (allSubSamplesCount == 0) {
      return new SearchResultsImpl<>(new ArrayList<>(), pgCrit, 0);
    }

    // get a page of subsamples
    Query<SubSample> subSampleQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from SubSample ss where "
                    + connectSqlConditionsWithAnd(nonTemplateFragment, deletedFragment)
                    + ownedByAndPermittedItemsQueryFragment
                    + orderByFragment,
                SubSample.class)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<SubSample> subSampleQueryWithParams =
        addQueryParams(
            ownedBy,
            user,
            subSampleQueryBase,
            visibleOwners,
            userGroupMembers,
            userGroupsUniqueNames);
    List<SubSample> limitedSubSamples = subSampleQueryWithParams.list();

    return new SearchResultsImpl<>(limitedSubSamples, pgCrit, allSubSamplesCount);
  }

  @Override
  public List<SubSample> getAllUsingImage(FileProperty fileProperty) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from SubSample where imageFileProperty=:fileProperty OR"
                + " thumbnailFileProperty=:fileProperty",
            SubSample.class)
        .setParameter("fileProperty", fileProperty)
        .list();
  }

  @Override
  public List<QuantityInfo> getActiveQuantitiesForUpdate(Long sampleId, boolean sampleDeleted) {
    return getSession()
        .createQuery(
            "select ss.quantityInfo.numericValue, ss.quantityInfo.unitId from SubSample ss"
                + " where ss.sample.id = :sampleId"
                + " and ss.quantityInfo.numericValue is not null"
                + " and (ss.deleted = false or (:sampleDeleted = true and"
                + " ss.deletedOnSampleDeletion = true))",
            Object[].class)
        .setParameter("sampleId", sampleId)
        .setParameter("sampleDeleted", sampleDeleted)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .list()
        .stream()
        .map(row -> new QuantityInfo((BigDecimal) row[0], ((Number) row[1]).intValue()))
        .collect(Collectors.toList());
  }

  @Override
  public QuantityInfo getQuantityForUpdate(Long subSampleId) {
    Object[] row =
        getSession()
            .createQuery(
                "select ss.quantityInfo.numericValue, ss.quantityInfo.unitId from SubSample ss"
                    + " where ss.id = :id",
                Object[].class)
            .setParameter("id", subSampleId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .uniqueResult();
    if (row == null || row[0] == null) {
      return null;
    }
    return new QuantityInfo((BigDecimal) row[0], ((Number) row[1]).intValue());
  }

  @Override
  public Long getVersionForUpdate(Long subSampleId) {
    return getSession()
        .createQuery("select ss.version from SubSample ss where ss.id = :id", Long.class)
        .setParameter("id", subSampleId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResult();
  }
}
