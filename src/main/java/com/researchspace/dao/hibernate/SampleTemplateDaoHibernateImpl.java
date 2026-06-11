package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.SampleTemplateDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.springframework.stereotype.Repository;

@Repository("sampleTemplateDao")
public class SampleTemplateDaoHibernateImpl extends InventoryDaoHibernate<SampleTemplate, Long>
    implements SampleTemplateDao {

  private String defaultTemplateOwner;

  public SampleTemplateDaoHibernateImpl(Class<SampleTemplate> persistentClass) {
    super(persistentClass);
  }

  public SampleTemplateDaoHibernateImpl() {
    super(SampleTemplate.class);
  }

  @Override
  public ISearchResults<SampleTemplate> getTemplatesForUser(
      PaginationCriteria<SampleTemplate> pgCrit,
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
      pgCrit = PaginationCriteria.createDefaultForClass(SampleTemplate.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedItemsOption);
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    // raw discriminator-column anchor (like DTYPE in the instrument DAOs): keeps the WHERE clause
    // non-empty when deletedOption=INCLUDE makes the deleted fragment blank; redundant with the
    // discriminator Hibernate adds for the concrete entity
    Query<Long> countQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(s) from SampleTemplate s where "
                    + connectSqlConditionsWithAnd(deletedFragment, " template=1 ")
                    + ownedByAndPermittedItemsQueryFragment,
                Long.class);
    Query<Long> countQueryWithParams =
        addQueryParams(
            ownedBy, user, countQueryBase, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    long allTemplatesCount = countQueryWithParams.getSingleResult();
    if (allTemplatesCount == 0) {
      return new SearchResultsImpl<>(new ArrayList<>(), pgCrit, 0);
    }

    Query<SampleTemplate> sampleQueryBase =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from SampleTemplate where "
                    + connectSqlConditionsWithAnd(deletedFragment, " template=1 ")
                    + ownedByAndPermittedItemsQueryFragment
                    + orderByFragment,
                SampleTemplate.class)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<SampleTemplate> sampleQueryWithParams =
        addQueryParams(
            ownedBy, user, sampleQueryBase, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    List<SampleTemplate> pagedTemplates = sampleQueryWithParams.list();
    return new SearchResultsImpl<>(pagedTemplates, pgCrit, allTemplatesCount);
  }

  @Override
  public String getDefaultTemplatesOwner() {
    if (defaultTemplateOwner == null) {
      defaultTemplateOwner =
          (String)
              sessionFactory
                  .getCurrentSession()
                  .createQuery(
                      // "from SampleTemplate" selects only template rows; no template=true needed
                      "select s.owner.username from SampleTemplate s order by s.id asc")
                  .setMaxResults(1)
                  .getSingleResult();
    }
    return defaultTemplateOwner;
  }

  @Override
  public SampleTemplate persistSampleTemplate(SampleTemplate template) {
    Session currentSession = sessionFactory.getCurrentSession();
    template.getActiveFields().stream()
        .filter(f -> FieldType.CHOICE.equals(f.getType()))
        .map(InventoryChoiceField.class::cast)
        .forEach(cf -> currentSession.save(cf.getChoiceDef()));
    template.getActiveFields().stream()
        .filter(f -> FieldType.RADIO.equals(f.getType()))
        .map(InventoryRadioField.class::cast)
        .forEach(cf -> currentSession.save(cf.getRadioDef()));
    currentSession.persist(template);
    return get(template.getId());
  }

  @Override
  public Long getTemplateCount() {
    return (long)
        sessionFactory
            .getCurrentSession()
            .createQuery("select count(s) from SampleTemplate s where s.deleted=false")
            .getSingleResult();
  }

  @Override
  public List<SampleTemplate> getAllTemplatesUsingImage(FileProperty fileProperty) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from SampleTemplate where imageFileProperty=:fileProperty OR"
                + " thumbnailFileProperty=:fileProperty",
            SampleTemplate.class)
        .setParameter("fileProperty", fileProperty)
        .list();
  }

  @Override
  public int saveIconId(SampleEntity sample, Long iconId) {
    // DML targets SampleEntity (the mapped superclass) so iconId can be set by whichever DAO is
    // invoked; a subclass-scoped update would silently miss rows of the other discriminator
    // value.
    Query<?> q = getSession().createQuery("update SampleEntity set iconId=:iconId where id = :id");
    q.setParameter("iconId", iconId);
    q.setParameter("id", sample.getId());
    return q.executeUpdate();
  }

  @Override
  public SampleTemplate saveAndReindexSubSamples(SampleTemplate sample) {
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
