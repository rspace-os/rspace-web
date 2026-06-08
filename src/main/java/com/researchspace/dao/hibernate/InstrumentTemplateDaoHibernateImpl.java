package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;

@Repository(value = "instrumentTemplateDao")
public class InstrumentTemplateDaoHibernateImpl
    extends InventoryDaoHibernate<InstrumentTemplate, Long> implements InstrumentTemplateDao {

  private String defaultTemplateOwner;

  public InstrumentTemplateDaoHibernateImpl(Class<InstrumentTemplate> persistentClass) {
    super(persistentClass);
  }

  public InstrumentTemplateDaoHibernateImpl() {
    super(InstrumentTemplate.class);
  }

  @Override
  public ISearchResults<InstrumentTemplate> getTemplatesForUser(
      PaginationCriteria<InstrumentTemplate> pgCrit,
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
      pgCrit = PaginationCriteria.createDefaultForClass(InstrumentTemplate.class);
    }
    String orderByFragment = getOrderBySqlFragmentForInventoryRecord(pgCrit);
    String deletedFragment = getDeletedSqlFragmentForInventoryRecord(deletedOption);
    int startPosition = pgCrit.getFirstResultIndex();
    int maxResult = pgCrit.getResultsPerPage();

    Query<Long> countQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(t) from InstrumentTemplate t where "
                    + connectSqlConditionsWithAnd(deletedFragment, " DTYPE='InstrumentTemplate' ")
                    + permittedFragment,
                Long.class);
    Query<Long> countQueryWithParams =
        addQueryParams(
            ownedBy, user, countQuery, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    long totalCount = countQueryWithParams.getSingleResult();
    if (totalCount == 0) {
      return new SearchResultsImpl<>(new ArrayList<>(), pgCrit, 0);
    }

    Query<InstrumentTemplate> pageQuery =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from InstrumentTemplate where "
                    + connectSqlConditionsWithAnd(deletedFragment, " DTYPE='InstrumentTemplate' ")
                    + permittedFragment
                    + orderByFragment,
                InstrumentTemplate.class)
            .setFirstResult(startPosition)
            .setMaxResults(maxResult);
    Query<InstrumentTemplate> pageQueryWithParams =
        addQueryParams(
            ownedBy, user, pageQuery, visibleOwners, userGroupMembers, userGroupsUniqueNames);
    List<InstrumentTemplate> page = pageQueryWithParams.list();
    return new SearchResultsImpl<>(page, pgCrit, totalCount);
  }

  @Override
  public List<InstrumentTemplate> findInstrumentTemplatesByName(String name, User user) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from InstrumentTemplate where name=:name and owner=:owner", InstrumentTemplate.class)
        .setParameter("name", name)
        .setParameter("owner", user)
        .list();
  }

  @Override
  public InstrumentTemplate persistInstrumentTemplate(InstrumentTemplate template) {
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
