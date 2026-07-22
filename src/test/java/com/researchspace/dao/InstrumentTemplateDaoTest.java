package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InstrumentTemplateDaoTest extends SpringTransactionalTest {

  @Autowired private InstrumentTemplateDao instrumentTemplateDao;

  @Before
  public void setUp() {
    // the owner cache lives on the singleton DAO bean; clear it so tests don't leak into each other
    instrumentTemplateDao.resetDefaultTemplateOwner();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    instrumentTemplateDao.resetDefaultTemplateOwner();
  }

  /**
   * Creates a template for the (currently logged-in) owner and marks it locked (isEditable=false).
   */
  private InstrumentTemplate persistLockedTemplateFor(User owner, String name) {
    ApiInstrumentTemplate api = createBasicInstrumentTemplateForUser(owner, name);
    InstrumentTemplate entity = instrumentTemplateDao.get(api.getId());
    entity.setEditable(false);
    return instrumentTemplateDao.save(entity);
  }

  private InstrumentTemplate persistEditableTemplateFor(User owner, String name) {
    ApiInstrumentTemplate api = createBasicInstrumentTemplateForUser(owner, name);
    return instrumentTemplateDao.get(api.getId());
  }

  @Test
  public void getDefaultTemplatesOwnerReturnsOwnerOfOldestLockedTemplate() {
    User owner = createInitAndLoginAnyUser();
    persistLockedTemplateFor(owner, "locked default template");
    instrumentTemplateDao.resetDefaultTemplateOwner();

    String defaultOwner = instrumentTemplateDao.getDefaultTemplatesOwner();
    assertNotNull(defaultOwner);

    // must equal the owner of the OLDEST locked (editable=false) template, not merely the oldest
    // row
    InstrumentTemplate oldestLocked =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from InstrumentTemplate where editable = false order by id asc",
                InstrumentTemplate.class)
            .setMaxResults(1)
            .getSingleResult();
    assertEquals(oldestLocked.getOwner().getUsername(), defaultOwner);

    // value is cached until reset
    assertEquals(defaultOwner, instrumentTemplateDao.getDefaultTemplatesOwner());
  }

  @Test
  public void getDefaultTemplatesOwnerIgnoresEditableTemplates() {
    instrumentTemplateDao.resetDefaultTemplateOwner();
    String before = instrumentTemplateDao.getDefaultTemplatesOwner();

    // a fresh user's EDITABLE template must not make them the default owner
    User user = createInitAndLoginAnyUser();
    persistEditableTemplateFor(user, "ordinary editable template");
    instrumentTemplateDao.resetDefaultTemplateOwner();

    assertEquals(
        before,
        instrumentTemplateDao.getDefaultTemplatesOwner(),
        "an editable template must not change the default templates owner");
  }

  @Test
  public void getTemplatesForUserExposesDefaultOwnerTemplatesButNotOthers() {
    // ensure a default (locked) template owner exists
    instrumentTemplateDao.resetDefaultTemplateOwner();
    String defaultOwner = instrumentTemplateDao.getDefaultTemplatesOwner();
    if (defaultOwner == null) {
      User lockOwner = createInitAndLoginAnyUser();
      persistLockedTemplateFor(lockOwner, "locked default template");
      instrumentTemplateDao.resetDefaultTemplateOwner();
      defaultOwner = instrumentTemplateDao.getDefaultTemplatesOwner();
    }
    assertNotNull(defaultOwner);

    // a third user's private editable template (that user is not the default owner)
    User thirdUser = createInitAndLoginAnyUser();
    InstrumentTemplate privateTemplate =
        persistEditableTemplateFor(thirdUser, "third user private");

    // the observer: yet another independent user (no shared groups, not an admin)
    User observer = createInitAndLoginAnyUser();
    PaginationCriteria<InstrumentTemplate> pgCrit =
        PaginationCriteria.createDefaultForClass(InstrumentTemplate.class);
    pgCrit.setResultsPerPage(1000);
    final String finalDefaultOwner = defaultOwner;
    ISearchResults<InstrumentTemplate> results =
        instrumentTemplateDao.getTemplatesForUser(pgCrit, null, null, null, observer);

    assertTrue(
        results.getResults().stream()
            .anyMatch(t -> t.getOwner().getUsername().equals(finalDefaultOwner)),
        "a non-owner must see the default owner's (locked) template");
    assertTrue(
        results.getResults().stream().noneMatch(t -> t.getId().equals(privateTemplate.getId())),
        "a non-owner must not see a non-default-owner's private template");
  }
}
