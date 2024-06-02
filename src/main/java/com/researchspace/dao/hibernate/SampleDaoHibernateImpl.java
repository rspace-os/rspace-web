package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.SampleDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.SampleField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang.Validate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.springframework.stereotype.Repository;

@Repository
public class SampleDaoHibernateImpl extends InventoryDaoHibernate<Sample, Long>
    implements SampleDao {

  private static final String PARENT_TEMPLATE_ID = "parentTemplateId";
  private static final String FROM_SAMPLE_WHERE = "from Sample where ";

  public SampleDaoHibernateImpl(Class<Sample> persistentClass) {
    super(persistentClass);
  }

  public SampleDaoHibernateImpl() {
    super(Sample.class);
  }

  @Override
  public List<Sample> findSamplesByName(String name, User user) {

    return sessionFactory
        .getCurrentSession()
        .createQuery("from Sample where name=:name and owner=:owner", Sample.class)
        .setParameter("name", name)
        .setParameter("owner", user)
        .list();
  }

  @Override
  public ISearchResults<Sample> getSamplesForUser(
      PaginationCriteria<Sample> pgCrit,
      Long parentTemplateId,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user) {

    // prepare owner and permission limiting query fragment
    List<String> userGroupMembers =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(user);
    List<String> userGroupsUniqueNames =
        user.getGroups().stream().map(Group::getUniqueName).collect(Collectors.toList());
    List<String> visibleOwners = invPermissionUtils.getOwnersVisibleWithUserRole(user);
    String ownedByAndPermittedItemsQueryFragment =
        getOwnedByAndPermittedItemsSqlQueryFragment(
            ownedBy, user, userGroupMembers, userGroupsUniqueNames, visibleOwners);

    // get the page of results
    if (pgCrit == null) {
      pgCrit = PaginationCriteria.createDefaultForClass(Sample.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedItemsOption);
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    boolean limitByParentTemplate = parentTemplateId != null;
    String parentTemplateQueryFragment =
        limitByParentTemplate ? "and STemplate_id=:parentTemplateId " : "";

    // get total count
    Query<Long> countQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(s) from Sample s where "
                    + connectSqlConditionsWithAnd(deletedFragment, " template=false ")
                    + parentTemplateQueryFragment
                    + ownedByAndPermittedItemsQueryFragment,
                Long.class);
    if (limitByParentTemplate) {
      countQueryBase.setParameter(PARENT_TEMPLATE_ID, parentTemplateId);
    }
    Query<Long> countQueryWithParams =
        addQueryParams(
            ownedBy, user, countQueryBase, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    long allSamplesCount = countQueryWithParams.getSingleResult();
    if (allSamplesCount == 0) {
      return new SearchResultsImpl<>(new ArrayList<>(), pgCrit, 0);
    }

    // get a page of samples
    Query<Sample> samplePageQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                FROM_SAMPLE_WHERE
                    + connectSqlConditionsWithAnd(deletedFragment, " template=false ")
                    + parentTemplateQueryFragment
                    + ownedByAndPermittedItemsQueryFragment
                    + orderByFragment,
                Sample.class)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    if (limitByParentTemplate) {
      samplePageQueryBase.setParameter(PARENT_TEMPLATE_ID, parentTemplateId);
    }
    Query<Sample> samplePageQueryWithParams =
        addQueryParams(
            ownedBy,
            user,
            samplePageQueryBase,
            visibleOwners,
            userGroupMembers,
            userGroupsUniqueNames);
    List<Sample> pageOfSamples = samplePageQueryWithParams.list();
    return new SearchResultsImpl<>(pageOfSamples, pgCrit, allSamplesCount);
  }

  @Override
  public GlobalIdentifier getSampleGlobalIdFromFieldId(Long fieldId) {
    SampleField field =
        sessionFactory
            .getCurrentSession()
            .createQuery("from SampleField where id=:fieldId", SampleField.class)
            .setParameter("fieldId", fieldId)
            .getSingleResult();
    if (field == null) {
      return null;
    }
    return field.getSample().getOid();
  }

  @Override
  public List<Sample> getSamplesLinkingOlderTemplateVersionForUser(
      Long templateId, Long version, User user) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            FROM_SAMPLE_WHERE
                + " owner=:owner and template=false and deleted=false "
                + " and STemplate_id=:parentTemplateId "
                + " and STemplateLinkedVersion < :parentTemplateMaxVersion",
            Sample.class)
        .setParameter("owner", user)
        .setParameter(PARENT_TEMPLATE_ID, templateId)
        .setParameter("parentTemplateMaxVersion", version)
        .list();
  }

  @Override
  public Sample persistNewSample(Sample sample) {
    /*
     * we use 'persist' here rather than standard 'merge' to avoid
     * ConstraintViolationException thrown by Sample.subSamples @Size annotation.
     *
     * more details: https://stackoverflow.com/q/37595918/639863
     */
    getSession().persist(sample);
    return get(sample.getId());
  }

  @SuppressWarnings("unchecked")
  @Override
  public ISearchResults<Sample> getTemplatesForUser(
      PaginationCriteria<Sample> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user) {

    // prepare permission limiting query fragment
    List<String> userGroupMembers =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(user);
    List<String> userGroupsUniqueNames =
        user.getGroups().stream().map(Group::getUniqueName).collect(Collectors.toList());
    List<String> visibleOwners = invPermissionUtils.getOwnersVisibleWithUserRole(user);
    visibleOwners.add(getDefaultTemplatesOwner()); // can also see templates of a default user
    String ownedByAndPermittedItemsQueryFragment =
        getOwnedByAndPermittedItemsSqlQueryFragment(
            ownedBy, user, userGroupMembers, userGroupsUniqueNames, visibleOwners);

    // get the page of results
    if (pgCrit == null) {
      pgCrit = PaginationCriteria.createDefaultForClass(Sample.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedItemsOption);
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    // find the total count
    Query<Long> countQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(s) from Sample s where "
                    + connectSqlConditionsWithAnd(deletedFragment, " template=true ")
                    + ownedByAndPermittedItemsQueryFragment);
    Query<Long> countQueryWithParams =
        addQueryParams(
            ownedBy, user, countQueryBase, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    long allTemplatesCount = countQueryWithParams.getSingleResult();
    if (allTemplatesCount == 0) {
      return new SearchResultsImpl<>(new ArrayList<>(), pgCrit, 0);
    }

    Query<Sample> sampleQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                FROM_SAMPLE_WHERE
                    + connectSqlConditionsWithAnd(deletedFragment, " template=true ")
                    + ownedByAndPermittedItemsQueryFragment
                    + orderByFragment)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<Sample> sampleQueryWithParams =
        addQueryParams(
            ownedBy, user, sampleQueryBase, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    List<Sample> pagedTemplates = sampleQueryWithParams.list();
    return new SearchResultsImpl<>(pagedTemplates, pgCrit, allTemplatesCount);
  }

  private String defaultTemplateOwner;

  @Override
  public String getDefaultTemplatesOwner() {
    if (defaultTemplateOwner == null) {
      defaultTemplateOwner =
          (String)
              sessionFactory
                  .getCurrentSession()
                  .createQuery(
                      "select owner.username from Sample s where template=true order by id asc")
                  .setMaxResults(1)
                  .getSingleResult();
    }
    return defaultTemplateOwner;
  }

  private long countTemplates() {
    return (long)
        sessionFactory
            .getCurrentSession()
            .createQuery("select count(s) from Sample s where deleted=false and template=true")
            .getSingleResult();
  }

  @Override
  public Sample persistSampleTemplate(Sample template) {
    assertIsSampleTemplate(template);
    Session currentSession = sessionFactory.getCurrentSession();
    template.getActiveFields().stream()
        .filter(f -> FieldType.CHOICE.equals(f.getType()))
        .map(InventoryChoiceField.class::cast)
        .forEach(cf -> currentSession.save(cf.getChoiceDef()));
    template.getActiveFields().stream()
        .filter(f -> FieldType.RADIO.equals(f.getType()))
        .map(InventoryRadioField.class::cast)
        .forEach(cf -> currentSession.save(cf.getRadioDef()));
    return persistNewSample(template);
  }

  private void assertIsSampleTemplate(Sample sampleToCheck) {
    Validate.isTrue(sampleToCheck.isTemplate(), "Was expecting a template, but found a sample");
  }

  @Override
  public Long getTemplateCount() {
    return countTemplates();
  }

  @Override
  public int saveIconId(Sample sample, Long iconId) {
    Query<?> q = getSession().createQuery("update Sample set iconId=:iconId where id = :id");
    q.setParameter("iconId", iconId);
    q.setParameter("id", sample.getId());
    return q.executeUpdate();
  }

  @Override
  public Sample saveAndReindexSubSamples(Sample sample) {
    Session ssnx = sessionFactory.getCurrentSession();
    FullTextSession fssn = Search.getFullTextSession(ssnx);
    sample.getSubSamples().forEach(fssn::index);
    return save(sample);
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
