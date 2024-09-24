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

    // prepare fragment limiting to non-template subsamples
    String nonTemplateFragment = "ss.sample.template = false ";

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
}
