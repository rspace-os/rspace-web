package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SampleTemplateDaoTest extends SpringTransactionalTest {

  @Before
  public void setUp() {
    sampleTemplateDao.resetDefaultTemplateOwner();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    // don't leak an owner cached during this (rolled-back) transaction into later tests
    sampleTemplateDao.resetDefaultTemplateOwner();
  }

  @Test
  public void persistSampleTemplateSavesRadioAndChoiceDefinitions() {
    User user = createInitAndLoginAnyUser();
    long initialCount = sampleTemplateDao.getTemplateCount();

    // complex template contains radio & choice fields with transient definitions,
    // so the definition pre-save path of persistSampleTemplate is exercised
    SampleTemplate template =
        recordFactory.createComplexSampleTemplate("dao test template", "dao test desc", user);
    SampleTemplate savedTemplate = sampleTemplateDao.persistSampleTemplate(template);
    assertNotNull(savedTemplate.getId());
    assertTrue(savedTemplate.isTemplate());
    assertEquals(initialCount + 1, sampleTemplateDao.getTemplateCount().longValue());

    SampleTemplate retrievedTemplate = sampleTemplateDao.get(savedTemplate.getId());
    assertEquals(savedTemplate, retrievedTemplate);
    InventoryRadioField radioField =
        retrievedTemplate.getActiveFields().stream()
            .filter(f -> FieldType.RADIO.equals(f.getType()))
            .map(InventoryRadioField.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no radio field on complex template"));
    assertNotNull(radioField.getRadioDef().getId());
    InventoryChoiceField choiceField =
        retrievedTemplate.getActiveFields().stream()
            .filter(f -> FieldType.CHOICE.equals(f.getType()))
            .map(InventoryChoiceField.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no choice field on complex template"));
    assertNotNull(choiceField.getChoiceDef().getId());
  }

  @Test
  public void getTemplatesForUserSeesOwnAndDefaultOwnerTemplates() {
    User user = createInitAndLoginAnyUser();

    // On a fresh DB the test user may itself be the default-templates owner (it was the first user
    // initialised and createSampleTemplates() assigned the built-ins to it). On an aged DB an
    // older user owns them. Either case must pass.
    // @Before already called resetDefaultTemplateOwner(), so the cache is clear.
    String defaultOwner = sampleTemplateDao.getDefaultTemplatesOwner();
    // defaultOwner may be null (no templates yet) or any username – do NOT assume it differs
    // from user.getUsername().

    PaginationCriteria<SampleTemplate> pgCrit =
        PaginationCriteria.createDefaultForClass(SampleTemplate.class);
    pgCrit.setResultsPerPage(100);
    ISearchResults<SampleTemplate> initialTemplates =
        sampleTemplateDao.getTemplatesForUser(pgCrit, null, null, user);
    long initialCount = initialTemplates.getTotalHits();
    // All initially visible templates must be owned by either the default owner or the test user.
    if (defaultOwner != null) {
      final String finalDefaultOwner = defaultOwner;
      assertTrue(
          initialTemplates.getResults().stream()
              .allMatch(
                  t ->
                      t.getOwner().getUsername().equals(finalDefaultOwner)
                          || t.getOwner().getUsername().equals(user.getUsername())));
    }

    // a new template of the user's own must become visible to them
    SampleTemplate ownTemplate = recordFactory.createSampleTemplate("dao owned template", user);
    sampleTemplateDao.persistSampleTemplate(ownTemplate);
    ISearchResults<SampleTemplate> updatedTemplates =
        sampleTemplateDao.getTemplatesForUser(pgCrit, null, null, user);
    assertEquals(initialCount + 1, updatedTemplates.getTotalHits().longValue());
    assertTrue(
        updatedTemplates.getResults().stream()
            .anyMatch(t -> t.getId().equals(ownTemplate.getId())));

    // Compute the true default owner now that ownTemplate is persisted (cache may have been
    // populated before ownTemplate existed; reset to get an accurate picture).
    sampleTemplateDao.resetDefaultTemplateOwner();
    String currentDefaultOwner = sampleTemplateDao.getDefaultTemplatesOwner();

    User otherUser = createInitAndLoginAnyUser();
    ISearchResults<SampleTemplate> otherUserTemplates =
        sampleTemplateDao.getTemplatesForUser(pgCrit, null, null, otherUser);

    if (currentDefaultOwner != null && !currentDefaultOwner.equals(user.getUsername())) {
      // Aged-DB path: default templates pre-exist and are owned by a DIFFERENT user.
      // ownTemplate is a private template of `user`, so `otherUser` must NOT see it,
      // and the count for `otherUser` stays at `initialCount` (only default-owner templates).
      assertTrue(
          otherUserTemplates.getResults().stream()
              .noneMatch(t -> t.getId().equals(ownTemplate.getId())),
          "otherUser should not see user's private template");
      assertEquals(
          initialCount,
          otherUserTemplates.getTotalHits().longValue(),
          "otherUser count must equal the pre-existing default-owner template count");
      final String finalCurrentDefaultOwner = currentDefaultOwner;
      assertTrue(
          otherUserTemplates.getResults().stream()
              .anyMatch(t -> t.getOwner().getUsername().equals(finalCurrentDefaultOwner)),
          "otherUser must see at least one default-owner template");
    } else {
      // Fresh-DB path: `user` IS the default owner.
      // The visibility rule exposes ALL templates owned by the default user to every user,
      // so `ownTemplate` (owned by `user`) IS visible to `otherUser`.
      // What we can assert: `otherUser` sees at least the default templates (initialCount)
      // plus ownTemplate (initialCount + 1), and ownTemplate appears in the result.
      assertEquals(
          initialCount + 1,
          otherUserTemplates.getTotalHits().longValue(),
          "otherUser must see all default-owner templates plus ownTemplate");
      assertTrue(
          otherUserTemplates.getResults().stream()
              .anyMatch(t -> t.getId().equals(ownTemplate.getId())),
          "ownTemplate must be visible to otherUser when user is the default owner");
    }
  }

  @Test
  public void getTemplateCountIncludesAllUsersTemplates() {
    User user = createInitAndLoginAnyUser();
    long initialCount = sampleTemplateDao.getTemplateCount();
    assertTrue(initialCount > 0); // default templates at least

    SampleTemplate template = recordFactory.createSampleTemplate("dao count template", user);
    sampleTemplateDao.persistSampleTemplate(template);
    assertEquals(initialCount + 1, sampleTemplateDao.getTemplateCount().longValue());
  }

  @Test
  public void getDefaultTemplatesOwnerReturnsOwnerOfOldestTemplate() {
    createInitAndLoginAnyUser(); // guarantees default templates exist

    String defaultOwner = sampleTemplateDao.getDefaultTemplatesOwner();
    assertNotNull(defaultOwner);
    SampleTemplate oldestTemplate =
        sessionFactory
            .getCurrentSession()
            .createQuery("from SampleTemplate order by id asc", SampleTemplate.class)
            .setMaxResults(1)
            .getSingleResult();
    assertEquals(oldestTemplate.getOwner().getUsername(), defaultOwner);
    // value is cached until reset
    assertEquals(defaultOwner, sampleTemplateDao.getDefaultTemplatesOwner());
  }
}
