package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Session;
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
    prefetchParentSampleFields(limitedSubSamples);

    return new SearchResultsImpl<>(limitedSubSamples, pgCrit, allSubSamplesCount);
  }

  /**
   * The API listing serialises every parent sample's fields and extra fields, which would otherwise
   * load lazily one sample at a time. This loads them for the whole page in a fixed number of
   * queries. Both collections are bags, so they need separate queries (fetching two bags at once
   * throws MultipleBagFetchException). Each field's eager template field is joined in, as the lazy
   * collection load would, and attachment fields' file lists are loaded the same way.
   */
  private void prefetchParentSampleFields(List<SubSample> subSamples) {
    List<Long> sampleIds =
        subSamples.stream().map(ss -> ss.getSample().getId()).distinct().toList();
    if (sampleIds.isEmpty()) {
      return;
    }
    Session session = sessionFactory.getCurrentSession();
    session
        .createQuery(
            "select s from SampleEntity s left join fetch s.fields f"
                + " left join fetch f.templateField where s.id in :ids",
            SampleEntity.class)
        .setParameter("ids", sampleIds)
        .list();
    session
        .createQuery(
            "select s from SampleEntity s left join fetch s.extraFields where s.id in :ids",
            SampleEntity.class)
        .setParameter("ids", sampleIds)
        .list();
    session
        .createQuery(
            "select f from InventoryAttachmentField f left join fetch f.files"
                + " where f.sample.id in :ids",
            InventoryAttachmentField.class)
        .setParameter("ids", sampleIds)
        .list();
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
