package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.SampleDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.springframework.stereotype.Repository;

@Repository("sampleDao")
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
    // property name is STemplate: JavaBeans decapitalize keeps the leading double-uppercase of
    // getSTemplate()
    String parentTemplateQueryFragment =
        limitByParentTemplate ? "and STemplate.id=:parentTemplateId " : "";

    // get total count
    // raw DTYPE discriminator anchor (matching the instrument DAOs): keeps the WHERE clause
    // non-empty when deletedOption=INCLUDE makes the deleted fragment blank; redundant with the
    // discriminator Hibernate adds for the concrete entity
    Query<Long> countQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(s) from Sample s where "
                    + connectSqlConditionsWithAnd(deletedFragment, " DTYPE='Sample' ")
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
                    + connectSqlConditionsWithAnd(deletedFragment, " DTYPE='Sample' ")
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
    InventoryEntityField field =
        sessionFactory
            .getCurrentSession()
            .createQuery("from InventoryEntityField where id=:fieldId", InventoryEntityField.class)
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
    // property names are STemplate/STemplateLinkedVersion: JavaBeans keeps the leading
    // double-uppercase of the getter
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            FROM_SAMPLE_WHERE
                // "from Sample" already excludes templates; no template=false needed
                + " owner=:owner and deleted=false "
                + " and STemplate.id=:parentTemplateId "
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

  @Override
  public int saveIconId(SampleEntity sample, Long iconId) {
    // DML targets SampleEntity (the mapped superclass) so it applies to both samples and
    // templates. Using "update Sample ..." would silently skip template rows.
    Query<?> q = getSession().createQuery("update SampleEntity set iconId=:iconId where id = :id");
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

  @Override
  public List<Sample> getAllUsingImage(FileProperty fileProperty) {
    // Intentionally restricted to Sample rows (template=false). The template DAO's
    // getAllTemplatesUsingImage handles the template side; callers that need both invoke both DAOs.
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from Sample where imageFileProperty=:fileProperty OR"
                + " thumbnailFileProperty=:fileProperty",
            Sample.class)
        .setParameter("fileProperty", fileProperty)
        .list();
  }

  @Override
  public boolean entityNameExistsForUser(String name, User owner) {
    // Query SampleEntity to count across BOTH discriminator values (samples and templates),
    // preserving the legacy behaviour that treated them as a single name-uniqueness namespace.
    // The explicit editInfo.name path is required: unqualified embedded sub-properties (like
    // "name") resolve only on concrete-leaf persisters, not on the abstract hierarchy root.
    long count =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(se) from SampleEntity se where se.editInfo.name=:name and"
                    + " se.owner=:owner",
                Long.class)
            .setParameter("name", name)
            .setParameter("owner", owner)
            .getSingleResult();
    return count > 0;
  }
}
