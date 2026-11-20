package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.controller.InstrumentsApiController;
import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.controller.SamplesApiController;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentEntity;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryEntityField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.dao.customliquibaseupdates.BackfillInventoryTemplateFieldKeys_RSDEV1706;
import com.researchspace.dao.customliquibaseupdates.FieldKeyBackfillRunner;
import com.researchspace.model.User;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.field.LocalizedIllegalStateException;
import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.field.rule.FieldDisplayRuleValidator;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;

/**
 * Display rules through the Inventory managers: template validation and the key contract, and the
 * rule-aware mandatory checks on each trigger (create, update, update to latest template version)
 * and on the paths that reach them (POST validation, bulk operations, duplication).
 */
class InventoryFieldDisplayRuleManagerTest extends SpringTransactionalTest {

  private static final String GMO = "Is this a GMO?";
  private static final String PERMIT = "GMO permit number";
  private static final String PROTOCOL = "Protocol";
  private static final String LANDING_PAGE = "Landing page";
  private static final String MANDATORY_MISSING = "validation.inventoryField.invalidForFieldType";

  private @Autowired SamplesApiController samplesController;
  private @Autowired InstrumentsApiController instrumentsController;

  private User user;
  private String gmoKey;

  @BeforeEach
  void createUser() {
    user = createInitAndLoginAnyUser();
    gmoKey = UUID.randomUUID().toString();
  }

  // ------------------------------------------------------------------ templates

  @Test
  void newTemplateFieldsCanReferenceEachOtherAndGetKeys() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);

    ApiInventoryEntityField gmo = field(template, GMO);
    ApiInventoryEntityField permit = field(template, PERMIT);
    assertEquals(gmoKey, gmo.getFieldKey());
    assertEquals(showWhenGmoIs("Yes"), permit.getDisplayRule());
    assertNotNull(permit.getFieldKey());
  }

  @Test
  void templateWithARuleOnAMissingFieldIsRejected() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("dangling");
    post.getFields().add(permitField(showWhenGmoIs("Yes")));

    LocalizedIllegalArgumentException e =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> sampleApiMgr.createSampleTemplate(post, user));
    assertEquals(FieldDisplayRuleValidator.SOURCE_MISSING, e.getMessage());
  }

  @Test
  void templateUpdateCanAddASourceAndARuleUsingItInOneRequest() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("no rules yet");
    post.getFields().add(permitField(null));
    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(post, user);

    ApiSampleTemplate update = templateUpdate(created);
    ApiInventoryEntityField newGmo = gmoField(null);
    newGmo.setNewFieldRequest(true);
    update.getFields().add(newGmo);
    ApiInventoryEntityField permit = fieldUpdate(field(created, PERMIT));
    permit.setDisplayRule(showWhenGmoIs("Yes"));
    update.getFields().add(permit);
    ApiSampleTemplate updated = sampleApiMgr.updateApiSampleTemplate(update, user);

    assertEquals(gmoKey, field(updated, GMO).getFieldKey());
    assertEquals(showWhenGmoIs("Yes"), field(updated, PERMIT).getDisplayRule());
  }

  @Test
  void deletingASourceWithoutClearingItsRulesIsRejected() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);

    ApiSampleTemplate deleteOnly = templateUpdate(template);
    deleteOnly.getFields().add(deleteRequest(field(template, GMO)));
    LocalizedIllegalArgumentException e =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> sampleApiMgr.updateApiSampleTemplate(deleteOnly, user));
    assertEquals(FieldDisplayRuleValidator.SOURCE_MISSING, e.getMessage());
  }

  @Test
  void deletingASourceAndClearingItsRulesInOneRequestIsAccepted() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);

    ApiSampleTemplate deleteAndClear = templateUpdate(template);
    deleteAndClear.getFields().add(deleteRequest(field(template, GMO)));
    ApiInventoryEntityField clearRule = fieldUpdate(field(template, PERMIT));
    clearRule.setDisplayRule(null);
    deleteAndClear.getFields().add(clearRule);
    ApiSampleTemplate updated = sampleApiMgr.updateApiSampleTemplate(deleteAndClear, user);

    assertNull(field(updated, PERMIT).getDisplayRule());
  }

  @Test
  void ruleOmittedFromAnUpdateIsKept() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);

    ApiSampleTemplate rename = templateUpdate(template);
    ApiInventoryEntityField permit = fieldUpdate(field(template, PERMIT));
    permit.setName("Permit no.");
    rename.getFields().add(permit);
    ApiSampleTemplate updated = sampleApiMgr.updateApiSampleTemplate(rename, user);

    assertEquals(showWhenGmoIs("Yes"), field(updated, "Permit no.").getDisplayRule());
  }

  @Test
  void keyOfASoftDeletedFieldCannotBeReused() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);
    ApiSampleTemplate delete = templateUpdate(template);
    delete.getFields().add(deleteRequest(field(template, GMO)));
    ApiInventoryEntityField clearRule = fieldUpdate(field(template, PERMIT));
    clearRule.setDisplayRule(null);
    delete.getFields().add(clearRule);
    sampleApiMgr.updateApiSampleTemplate(delete, user);

    ApiSampleTemplate reuse = templateUpdate(template);
    ApiInventoryEntityField sameKey = gmoField(null);
    sameKey.setName("Reused key");
    sameKey.setNewFieldRequest(true);
    reuse.getFields().add(sameKey);

    LocalizedIllegalArgumentException e =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> sampleApiMgr.updateApiSampleTemplate(reuse, user));
    assertEquals(FieldDisplayRuleValidator.KEY_DUPLICATE, e.getMessage());
  }

  @Test
  void fieldKeyCannotBeChanged() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);
    ApiSampleTemplate update = templateUpdate(template);
    ApiInventoryEntityField gmo = fieldUpdate(field(template, GMO));
    gmo.setFieldKey(UUID.randomUUID().toString());
    update.getFields().add(gmo);

    LocalizedIllegalArgumentException e =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> sampleApiMgr.updateApiSampleTemplate(update, user));
    assertEquals("errors.inventory.field.keyChanged", e.getMessage());
  }

  @Test
  void duplicatedTemplateAndItsSamplesKeepRulesOnTheRightFields() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);

    ApiSampleTemplate duplicate = sampleApiMgr.duplicateTemplate(template.getId(), user);
    assertEquals(gmoKey, field(duplicate, GMO).getFieldKey());
    assertEquals(field(template, PERMIT).getFieldKey(), field(duplicate, PERMIT).getFieldKey());
    assertEquals(showWhenGmoIs("Yes"), field(duplicate, PERMIT).getDisplayRule());

    ApiSampleWithFullSubSamples sample = createSample(duplicate, "No", "");
    assertEquals(gmoKey, field(sample, GMO).getFieldKey());
    assertEquals(showWhenGmoIs("Yes"), field(sample, PERMIT).getDisplayRule());
    assertMandatoryMissing(() -> createSample(duplicate, "Yes", ""));
  }

  @Test
  void firstRuleOnABackfilledTemplateIsAccepted() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("created before display rules");
    post.getFields().add(gmoField(null));
    post.getFields().add(permitField(null));
    ApiSampleTemplate created = sampleApiMgr.createSampleTemplate(post, user);
    // as an upgraded database has it: no keys, then the changelog's backfill
    sessionFactory.getCurrentSession().flush();
    sessionFactory
        .getCurrentSession()
        .createNativeQuery("update InventoryEntityField set fieldKey = null where sample_id = :id")
        .setParameter("id", created.getId())
        .executeUpdate();
    FieldKeyBackfillRunner.run(new BackfillInventoryTemplateFieldKeys_RSDEV1706(), sessionFactory);

    ApiSampleTemplate backfilled = sampleApiMgr.getApiSampleTemplateById(created.getId(), user);
    String backfilledGmoKey = field(backfilled, GMO).getFieldKey();
    assertNotNull(backfilledGmoKey);
    assertNotEquals(gmoKey, backfilledGmoKey);
    ApiSampleTemplate update = templateUpdate(backfilled);
    ApiInventoryEntityField permit = fieldUpdate(field(backfilled, PERMIT));
    permit.setDisplayRule(
        new FieldDisplayRule(backfilledGmoKey, ConditionOperator.IS_ANY_OF, List.of("Yes")));
    update.getFields().add(permit);

    ApiSampleTemplate updated = sampleApiMgr.updateApiSampleTemplate(update, user);
    assertEquals(backfilledGmoKey, field(updated, PERMIT).getDisplayRule().sourceFieldKey());
  }

  // ------------------------------------------------------------------ samples

  @Test
  void sampleCreateRequiresOnlyShownMandatoryFields() {
    ApiSampleTemplate template = createGmoSampleTemplate("No");

    assertNotNull(createSampleFromDefaults(template));
    assertNotNull(createSample(template, "No", ""));
    assertMandatoryMissing(() -> createSample(template, "Yes", ""));
    assertNotNull(createSample(template, "Yes", "P-123"));

    ApiSampleTemplate shownByDefault = createGmoSampleTemplate("Yes");
    assertMandatoryMissing(() -> createSampleFromDefaults(shownByDefault));
  }

  @Test
  void samplePostValidationSkipsHiddenMandatoryFields() throws Exception {
    ApiSampleTemplate template = createGmoSampleTemplate(null);

    assertFalse(validateCreate(sampleRequest(template, "No", "")).hasErrors());
    BindingResult shown = validateCreate(sampleRequest(template, "Yes", ""));
    assertEquals(
        "errors.inventory.field.mandatoryFieldEmpty", shown.getFieldError("fields").getCode());
  }

  @Test
  void sampleUpdateRejectsRevealingAnEmptyMandatoryField() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);
    ApiSampleWithFullSubSamples sample = createSample(template, "No", "");

    ApiSample reveal = sampleUpdate(sample);
    reveal.getFields().add(optionsUpdate(field(sample, GMO), "Yes"));
    assertMandatoryMissing(() -> sampleApiMgr.updateApiSample(reveal, user));

    ApiSample revealAndFill = sampleUpdate(sample);
    revealAndFill.getFields().add(optionsUpdate(field(sample, GMO), "Yes"));
    revealAndFill.getFields().add(contentUpdate(field(sample, PERMIT), "P-1"));
    ApiSample updated = sampleApiMgr.updateApiSample(revealAndFill, user);
    assertEquals("P-1", field(updated, PERMIT).getContent());

    ApiSample empty = sampleUpdate(sample);
    empty.getFields().add(contentUpdate(field(sample, PERMIT), ""));
    assertMandatoryMissing(() -> sampleApiMgr.updateApiSample(empty, user));
  }

  @Test
  void updateToLatestChecksVisibilityAfterTheWholeSync() {
    // template versions bump once per transaction, so the samples are made before the only update
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("permit optional at first");
    ApiInventoryEntityField optionalPermit = permitField(showWhenGmoIs("Yes"));
    optionalPermit.setMandatory(false);
    post.getFields().add(optionalPermit);
    post.getFields().add(gmoField(null));
    ApiSampleTemplate template = sampleApiMgr.createSampleTemplate(post, user);
    ApiSampleWithFullSubSamples hidden = createSample(template, "No", "");
    ApiSampleWithFullSubSamples shown = createSample(template, "Yes", "");

    ApiSampleTemplate makeMandatory = templateUpdate(template);
    ApiInventoryEntityField mandatory = fieldUpdate(field(template, PERMIT));
    mandatory.setMandatory(true);
    makeMandatory.getFields().add(mandatory);
    sampleApiMgr.updateApiSampleTemplate(makeMandatory, user);

    ApiSample updatedHidden =
        sampleApiMgr.updateSampleToLatestTemplateVersion(hidden.getId(), user);
    assertTrue(field(updatedHidden, PERMIT).getMandatory());
    LocalizedIllegalStateException e =
        assertThrows(
            LocalizedIllegalStateException.class,
            () -> sampleApiMgr.updateSampleToLatestTemplateVersion(shown.getId(), user));
    assertEquals("validation.inventoryField.mandatoryInLatestTemplate", e.getMessage());
  }

  @Test
  void updateToLatestCopiesARuleAddedWithTheMandatoryFlag() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("rule added later");
    post.getFields().add(gmoField(null));
    ApiInventoryEntityField optionalPermit = permitField(null);
    optionalPermit.setMandatory(false);
    post.getFields().add(optionalPermit);
    ApiSampleTemplate template = sampleApiMgr.createSampleTemplate(post, user);
    ApiSampleWithFullSubSamples sample = createSample(template, "No", "");

    // one template version adds the rule and makes the field mandatory: hidden, so allowed
    ApiSampleTemplate addRule = templateUpdate(template);
    ApiInventoryEntityField permit = fieldUpdate(field(template, PERMIT));
    permit.setDisplayRule(showWhenGmoIs("Yes"));
    permit.setMandatory(true);
    addRule.getFields().add(permit);
    sampleApiMgr.updateApiSampleTemplate(addRule, user);

    ApiSample updated = sampleApiMgr.updateSampleToLatestTemplateVersion(sample.getId(), user);
    assertEquals(showWhenGmoIs("Yes"), field(updated, PERMIT).getDisplayRule());
    assertEquals(field(template, PERMIT).getFieldKey(), field(updated, PERMIT).getFieldKey());
    assertTrue(field(updated, PERMIT).getMandatory());

    ApiSample reveal = sampleUpdate(sample);
    reveal.getFields().add(optionsUpdate(field(updated, GMO), "Yes"));
    assertMandatoryMissing(() -> sampleApiMgr.updateApiSample(reveal, user));
  }

  @Test
  void emptyShownMandatoryFieldAddedByUpdateToLatestDoesNotBlockAnUnrelatedEdit() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("permit added later");
    post.getFields().add(gmoField(null));
    ApiSampleTemplate template = sampleApiMgr.createSampleTemplate(post, user);
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples();
    sample.setName("GMO sample");
    sample.setTemplateId(template.getId());
    sample.getFields().add(optionsValue("Yes"));
    sample = sampleApiMgr.createNewApiSample(sample, user);

    ApiSampleTemplate addPermit = templateUpdate(template);
    ApiInventoryEntityField permit = permitField(showWhenGmoIs("Yes"));
    permit.setNewFieldRequest(true);
    addPermit.getFields().add(permit);
    sampleApiMgr.updateApiSampleTemplate(addPermit, user);
    ApiSample updated = sampleApiMgr.updateSampleToLatestTemplateVersion(sample.getId(), user);
    assertNull(field(updated, PERMIT).getContent());

    ApiSample rename = sampleUpdate(sample);
    rename.setName("renamed");
    assertEquals("renamed", sampleApiMgr.updateApiSample(rename, user).getName());

    // the UI echoes every field, sending "" for empty content
    ApiSample echo = sampleUpdate(sample);
    echo.setName("renamed again");
    echo.getFields().add(optionsUpdate(field(updated, GMO), "Yes"));
    echo.getFields().add(contentUpdate(field(updated, PERMIT), ""));
    assertEquals("renamed again", sampleApiMgr.updateApiSample(echo, user).getName());
  }

  @Test
  void sampleUpdateToLatestDropsTheRuleOfAFieldDeletedButKept() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);
    ApiSampleWithFullSubSamples sample = createSample(template, "No", "");
    ApiSampleTemplate deletePermit = templateUpdate(template);
    deletePermit.getFields().add(deleteRequest(field(template, PERMIT)));
    sampleApiMgr.updateApiSampleTemplate(deletePermit, user);

    ApiSample updated = sampleApiMgr.updateSampleToLatestTemplateVersion(sample.getId(), user);
    assertNull(field(updated, PERMIT).getDisplayRule());
    assertFalse(field(updated, PERMIT).getMandatory());

    // the old rule would have revealed the empty, mandatory permit and blocked this edit
    ApiSample echo = sampleUpdate(sample);
    echo.getFields().add(optionsUpdate(field(updated, GMO), "Yes"));
    echo.getFields().add(contentUpdate(field(updated, PERMIT), ""));
    ApiSample saved = sampleApiMgr.updateApiSample(echo, user);
    assertEquals(List.of("Yes"), field(saved, GMO).getSelectedOptions());
    assertNull(field(saved, PERMIT).getDisplayRule());
  }

  @Test
  void ruledLinkFieldIsRequiredOnlyWhileShown() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("GMO with protocol");
    post.getFields().add(gmoField(null));
    post.getFields().add(protocolField());
    ApiSampleTemplate template = sampleApiMgr.createSampleTemplate(post, user);
    String target = createBasicSampleForUser(user).getGlobalId();

    assertMandatoryMissing(() -> createSampleWithProtocol(template, "Yes", null));
    ApiSampleWithFullSubSamples shown = createSampleWithProtocol(template, "Yes", target);
    ApiSampleWithFullSubSamples hidden = createSampleWithProtocol(template, "No", target);
    ApiSampleWithFullSubSamples hiddenEmpty = createSampleWithProtocol(template, "No", null);

    // a field update without a link clears an item's link
    ApiSample clearShown = sampleUpdate(shown);
    clearShown.getFields().add(fieldUpdate(field(shown, PROTOCOL)));
    assertMandatoryMissing(() -> sampleApiMgr.updateApiSample(clearShown, user));

    ApiSample clearHidden = sampleUpdate(hidden);
    clearHidden.getFields().add(fieldUpdate(field(hidden, PROTOCOL)));
    assertNull(field(sampleApiMgr.updateApiSample(clearHidden, user), PROTOCOL).getLink());

    ApiSample reveal = sampleUpdate(hiddenEmpty);
    reveal.getFields().add(optionsUpdate(field(hiddenEmpty, GMO), "Yes"));
    assertMandatoryMissing(() -> sampleApiMgr.updateApiSample(reveal, user));
  }

  @Test
  void nullSelectedOptionIsAFieldErrorNotACrash() {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("no rules");
    post.getFields().add(gmoField(null));
    ApiSampleTemplate template = sampleApiMgr.createSampleTemplate(post, user);
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples();
    sample.setName("null option");
    sample.setTemplateId(template.getId());
    ApiInventoryEntityField nullOption = new ApiInventoryEntityField();
    nullOption.setSelectedOptions(Collections.singletonList(null));
    sample.getFields().add(nullOption);

    BindingResult errors = validateCreate(sample);
    assertEquals(
        "validation.inventoryField.optionsNotAllowed", errors.getFieldError("fields").getCode());
  }

  @Test
  void bulkCreateAndUpdateApplyTheSameChecks() {
    ApiSampleTemplate template = createGmoSampleTemplate(null);
    InventoryBulkOperationConfig create =
        new InventoryBulkOperationConfig(
            BulkApiOperationType.CREATE,
            new ArrayList<>(
                List.of(sampleRequest(template, "No", ""), sampleRequest(template, "Yes", ""))),
            false,
            user);
    ApiInventoryBulkOperationResult created = inventoryBulkOpApiMgr.runBulkOperation(create);
    assertEquals(1, created.getSuccessCount());
    assertEquals(1, created.getErrorCount());

    ApiSampleWithFullSubSamples hidden =
        (ApiSampleWithFullSubSamples) created.getResults().get(0).getRecord();
    ApiSampleWithFullSubSamples reveal = new ApiSampleWithFullSubSamples();
    reveal.setId(hidden.getId());
    reveal.getFields().add(optionsUpdate(field(hidden, GMO), "Yes"));
    InventoryBulkOperationConfig update =
        new InventoryBulkOperationConfig(
            BulkApiOperationType.UPDATE, new ArrayList<>(List.of(reveal)), false, user);
    assertEquals(1, inventoryBulkOpApiMgr.runBulkOperation(update).getErrorCount());
  }

  // ------------------------------------------------------------------ instruments

  @Test
  void instrumentTemplatesAndInstrumentsApplyRules() {
    ApiInstrumentTemplate template = createGmoInstrumentTemplate(true);
    assertEquals(gmoKey, field(template, GMO).getFieldKey());
    assertEquals(showWhenGmoIs("Yes"), field(template, PERMIT).getDisplayRule());

    ApiInstrument hidden = createInstrument(template, "No", "");
    assertEquals(showWhenGmoIs("Yes"), field(hidden, PERMIT).getDisplayRule());
    assertMandatoryMissing(() -> createInstrument(template, "Yes", ""));

    ApiInstrument reveal = new ApiInstrument();
    reveal.setId(hidden.getId());
    reveal.getFields().add(optionsUpdate(field(hidden, GMO), "Yes"));
    assertMandatoryMissing(() -> instrumentApiMgr.updateApiInstrument(reveal, user));

    ApiInstrumentTemplate duplicate =
        instrumentApiMgr.duplicateInstrumentTemplate(template.getId(), user);
    assertEquals(gmoKey, field(duplicate, GMO).getFieldKey());
    assertEquals(showWhenGmoIs("Yes"), field(duplicate, PERMIT).getDisplayRule());
  }

  @Test
  void instrumentTemplateUpdateValidatesRules() {
    ApiInstrumentTemplate template = createGmoInstrumentTemplate(true);
    ApiInstrumentTemplate dangling = new ApiInstrumentTemplate();
    dangling.setId(template.getId());
    dangling.getFields().add(deleteRequest(field(template, GMO)));

    LocalizedIllegalArgumentException e =
        assertThrows(
            LocalizedIllegalArgumentException.class,
            () -> instrumentApiMgr.updateApiInstrumentTemplate(dangling, user));
    assertEquals(FieldDisplayRuleValidator.SOURCE_MISSING, e.getMessage());
  }

  @Test
  void instrumentUpdateToLatestIsRuleAware() {
    ApiInstrumentTemplate template = createGmoInstrumentTemplate(false);
    ApiInstrument hidden = createInstrument(template, "No", "");
    ApiInstrument shown = createInstrument(template, "Yes", "");
    ApiInstrumentTemplate makeMandatory = new ApiInstrumentTemplate();
    makeMandatory.setId(template.getId());
    ApiInventoryEntityField mandatory = fieldUpdate(field(template, PERMIT));
    mandatory.setMandatory(true);
    makeMandatory.getFields().add(mandatory);
    instrumentApiMgr.updateApiInstrumentTemplate(makeMandatory, user);

    assertDoesNotThrow(
        () -> instrumentApiMgr.updateInstrumentToLatestTemplateVersion(hidden.getId(), user));
    assertThrows(
        LocalizedIllegalStateException.class,
        () -> instrumentApiMgr.updateInstrumentToLatestTemplateVersion(shown.getId(), user));
  }

  @Test
  void instrumentUpdateToLatestDropsTheRuleOfAFieldDeletedButKept() {
    ApiInstrumentTemplate template = createGmoInstrumentTemplate(true);
    ApiInstrument instrument = createInstrument(template, "No", "");
    ApiInstrumentTemplate deletePermit = new ApiInstrumentTemplate();
    deletePermit.setId(template.getId());
    deletePermit.getFields().add(deleteRequest(field(template, PERMIT)));
    instrumentApiMgr.updateApiInstrumentTemplate(deletePermit, user);

    ApiInstrument updated =
        instrumentApiMgr.updateInstrumentToLatestTemplateVersion(instrument.getId(), user);
    assertNull(field(updated, PERMIT).getDisplayRule());
    assertFalse(field(updated, PERMIT).getMandatory());

    ApiInstrument echo = new ApiInstrument();
    echo.setId(instrument.getId());
    echo.getFields().add(optionsUpdate(field(updated, GMO), "Yes"));
    echo.getFields().add(contentUpdate(field(updated, PERMIT), ""));
    assertDoesNotThrow(() -> instrumentApiMgr.updateApiInstrument(echo, user));
  }

  @Test
  void blankLandingPageStaysExemptWhenItHasARule() {
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    post.setName("instrument with landing page rule");
    post.getFields().add(gmoField("Yes"));
    ApiInventoryEntityField landingPage = new ApiInventoryEntityField();
    landingPage.setName("Landing page");
    landingPage.setType(ApiFieldType.URI);
    landingPage.setContent("https://example.org/template-page");
    landingPage.setMandatory(true);
    landingPage.setDisplayRule(showWhenGmoIs("Yes"));
    post.getFields().add(landingPage);
    ApiInstrumentTemplate template = instrumentApiMgr.createInstrumentTemplate(post, user);

    ApiInstrument instrument = new ApiInstrument();
    instrument.setName("from template");
    instrument.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(instrument, user);

    assertNull(field(created, "Landing page").getContent());
  }

  @Test
  void blankLandingPageIsAcceptedOnCreateAtThePublicEndpoint() throws Exception {
    ApiInstrumentTemplate ruled = createLandingPageTemplate(showWhenGmoIs("Yes"));
    ApiInstrument created = postInstrument(ruled, "Yes", "");
    assertNull(field(created, LANDING_PAGE).getContent());

    ApiInstrumentTemplate unruled = createLandingPageTemplate(null);
    assertNull(field(postInstrument(unruled, "Yes", ""), LANDING_PAGE).getContent());
  }

  @Test
  void userCannotBlankAShownMandatoryLandingPage() throws Exception {
    ApiInstrumentTemplate template = createLandingPageTemplate(showWhenGmoIs("Yes"));
    ApiInstrument instrument = postInstrument(template, "Yes", "");
    putInstrument(instrument, contentUpdate(field(instrument, LANDING_PAGE), "https://lab.org/a"));

    assertMandatoryMissing(
        () -> {
          try {
            putInstrument(instrument, contentUpdate(field(instrument, LANDING_PAGE), ""));
          } catch (BindException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  @Test
  void deletingTheIdentifierThatWroteAShownMandatoryLandingPageClearsIt() throws Exception {
    ApiInstrumentTemplate template = createLandingPageTemplate(showWhenGmoIs("Yes"));
    ApiInstrument instrument = postInstrument(template, "Yes", "");
    String suffix = "abc123XYZ_-456789";
    putInstrument(
        instrument,
        contentUpdate(
            field(instrument, LANDING_PAGE),
            "https://rspace.example.com/public/inventory/" + suffix));
    Instrument entity =
        sessionFactory.getCurrentSession().get(Instrument.class, instrument.getId());
    DigitalObjectIdentifier doi = new DigitalObjectIdentifier("10.82316/test", "test", suffix);
    doi.setOwner(user);
    entity.addIdentifier(doi);
    sessionFactory.getCurrentSession().flush();

    ApiInstrument deleteIdentifier = new ApiInstrument();
    ApiInventoryDOI deleteRequest = new ApiInventoryDOI();
    deleteRequest.setId(doi.getId());
    deleteRequest.setDeleteIdentifierRequest(true);
    deleteIdentifier.setIdentifiers(List.of(deleteRequest));
    ApiInstrument updated =
        instrumentsController.updateInstrument(
            instrument.getId(), deleteIdentifier, bindingResult(deleteIdentifier), user);

    assertNull(field(updated, LANDING_PAGE).getContent());
  }

  // ------------------------------------------------------------------ helpers

  private ApiInventoryEntityField protocolField() {
    ApiInventoryEntityField protocol = new ApiInventoryEntityField();
    protocol.setName(PROTOCOL);
    protocol.setType(ApiFieldType.LINK);
    protocol.setMandatory(true);
    protocol.setDisplayRule(showWhenGmoIs("Yes"));
    return protocol;
  }

  /** Field values in template order: GMO, then the protocol link. */
  private ApiSampleWithFullSubSamples createSampleWithProtocol(
      ApiSampleTemplate template, String gmo, String targetGlobalId) {
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples();
    sample.setName("GMO sample with protocol");
    sample.setTemplateId(template.getId());
    sample.getFields().add(optionsValue(gmo));
    ApiInventoryEntityField protocol = new ApiInventoryEntityField();
    if (targetGlobalId != null) {
      ApiInventoryLink link = new ApiInventoryLink();
      link.setTargetGlobalId(targetGlobalId);
      link.setRelationType("References");
      protocol.setLink(link);
    }
    sample.getFields().add(protocol);
    return sampleApiMgr.createNewApiSample(sample, user);
  }

  /** GMO, then a mandatory Landing page with the given rule and a template value. */
  private ApiInstrumentTemplate createLandingPageTemplate(FieldDisplayRule rule) {
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    post.setName("instruments with a landing page");
    post.getFields().add(gmoField(null));
    ApiInventoryEntityField landingPage = new ApiInventoryEntityField();
    landingPage.setName(LANDING_PAGE);
    landingPage.setType(ApiFieldType.URI);
    landingPage.setContent("https://example.org/template-page");
    landingPage.setMandatory(true);
    landingPage.setDisplayRule(rule);
    post.getFields().add(landingPage);
    return instrumentApiMgr.createInstrumentTemplate(post, user);
  }

  /** Through the controller, so the request passes the POST validators as a client's would. */
  private ApiInstrument postInstrument(
      ApiInstrumentTemplate template, String gmo, String landingPage) throws BindException {
    ApiInstrument instrument = new ApiInstrument();
    instrument.setName("instrument");
    instrument.setTemplateId(template.getId());
    instrument.getFields().add(optionsValue(gmo));
    instrument.getFields().add(contentValue(landingPage));
    return instrumentsController.createNewInstrument(instrument, bindingResult(instrument), user);
  }

  private ApiInstrument putInstrument(ApiInstrument instrument, ApiInventoryEntityField fieldUpdate)
      throws BindException {
    ApiInstrument update = new ApiInstrument();
    update.getFields().add(fieldUpdate);
    return instrumentsController.updateInstrument(
        instrument.getId(), update, bindingResult(update), user);
  }

  private static BindingResult bindingResult(ApiInstrument instrument) {
    return new BeanPropertyBindingResult(instrument, "instrument");
  }

  private FieldDisplayRule showWhenGmoIs(String option) {
    return new FieldDisplayRule(gmoKey, ConditionOperator.IS_ANY_OF, List.of(option));
  }

  private ApiInventoryEntityField gmoField(String defaultOption) {
    ApiInventoryEntityField gmo = new ApiInventoryEntityField();
    gmo.setName(GMO);
    gmo.setType(ApiFieldType.RADIO);
    gmo.setDefinition(new ApiInventoryFieldDef(List.of("Yes", "No"), false));
    gmo.setFieldKey(gmoKey);
    if (defaultOption != null) {
      gmo.setSelectedOptions(List.of(defaultOption));
    }
    return gmo;
  }

  private ApiInventoryEntityField permitField(FieldDisplayRule rule) {
    ApiInventoryEntityField permit = new ApiInventoryEntityField();
    permit.setName(PERMIT);
    permit.setType(ApiFieldType.STRING);
    permit.setMandatory(true);
    permit.setDisplayRule(rule);
    return permit;
  }

  /** A template whose mandatory permit field is shown when GMO is "Yes"; GMO is listed second. */
  private ApiSampleTemplate createGmoSampleTemplate(String gmoDefault) {
    ApiSampleTemplatePost post = new ApiSampleTemplatePost();
    post.setName("GMO register");
    post.getFields().add(permitField(showWhenGmoIs("Yes")));
    post.getFields().add(gmoField(gmoDefault));
    return sampleApiMgr.createSampleTemplate(post, user);
  }

  private ApiInstrumentTemplate createGmoInstrumentTemplate(boolean permitMandatory) {
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    post.setName("GMO instruments");
    ApiInventoryEntityField permit = permitField(showWhenGmoIs("Yes"));
    permit.setMandatory(permitMandatory);
    post.getFields().add(permit);
    post.getFields().add(gmoField(null));
    return instrumentApiMgr.createInstrumentTemplate(post, user);
  }

  /** Field values in template order: permit, then GMO. */
  private ApiSampleWithFullSubSamples sampleRequest(
      ApiSampleTemplate template, String gmo, String permit) {
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples();
    sample.setName("GMO sample");
    sample.setTemplateId(template.getId());
    sample.getFields().add(contentValue(permit));
    sample.getFields().add(optionsValue(gmo));
    return sample;
  }

  private ApiSampleWithFullSubSamples createSample(
      ApiSampleTemplate template, String gmo, String permit) {
    return sampleApiMgr.createNewApiSample(sampleRequest(template, gmo, permit), user);
  }

  private ApiSampleWithFullSubSamples createSampleFromDefaults(ApiSampleTemplate template) {
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples();
    sample.setName("defaults");
    sample.setTemplateId(template.getId());
    return sampleApiMgr.createNewApiSample(sample, user);
  }

  private ApiInstrument createInstrument(
      ApiInstrumentTemplate template, String gmo, String permit) {
    ApiInstrument instrument = new ApiInstrument();
    instrument.setName("GMO instrument");
    instrument.setTemplateId(template.getId());
    instrument.getFields().add(contentValue(permit));
    instrument.getFields().add(optionsValue(gmo));
    return instrumentApiMgr.createNewApiInstrument(instrument, user);
  }

  private BindingResult validateCreate(ApiSampleWithFullSubSamples sample) {
    BindingResult errors = new BeanPropertyBindingResult(sample, "sample");
    try {
      samplesController.validateCreateSampleInput(sample, errors, user);
    } catch (BindException e) {
      return e.getBindingResult();
    }
    return errors;
  }

  private static ApiInventoryEntityField contentValue(String content) {
    ApiInventoryEntityField value = new ApiInventoryEntityField();
    value.setContent(content);
    return value;
  }

  private static ApiInventoryEntityField optionsValue(String option) {
    ApiInventoryEntityField value = new ApiInventoryEntityField();
    value.setSelectedOptions(List.of(option));
    return value;
  }

  private static ApiInventoryEntityField contentUpdate(
      ApiInventoryEntityField existing, String content) {
    ApiInventoryEntityField update = fieldUpdate(existing);
    update.setContent(content);
    return update;
  }

  private static ApiInventoryEntityField optionsUpdate(
      ApiInventoryEntityField existing, String option) {
    ApiInventoryEntityField update = fieldUpdate(existing);
    update.setSelectedOptions(List.of(option));
    return update;
  }

  private static ApiInventoryEntityField fieldUpdate(ApiInventoryEntityField existing) {
    ApiInventoryEntityField update = new ApiInventoryEntityField();
    update.setId(existing.getId());
    return update;
  }

  private static ApiInventoryEntityField deleteRequest(ApiInventoryEntityField existing) {
    ApiInventoryEntityField delete = fieldUpdate(existing);
    delete.setDeleteFieldRequest(true);
    return delete;
  }

  private static ApiSampleTemplate templateUpdate(ApiSampleTemplate template) {
    ApiSampleTemplate update = new ApiSampleTemplate();
    update.setId(template.getId());
    return update;
  }

  private static ApiSample sampleUpdate(ApiSampleWithoutSubSamples sample) {
    ApiSample update = new ApiSample();
    update.setId(sample.getId());
    return update;
  }

  private static ApiInventoryEntityField field(ApiSampleWithoutSubSamples record, String name) {
    return findField(record.getFields(), name);
  }

  private static ApiInventoryEntityField field(ApiInstrumentEntity record, String name) {
    return findField(record.getFields(), name);
  }

  private static ApiInventoryEntityField findField(
      List<ApiInventoryEntityField> fields, String name) {
    return fields.stream().filter(f -> name.equals(f.getName())).findFirst().orElseThrow();
  }

  private static void assertMandatoryMissing(Runnable action) {
    LocalizedIllegalArgumentException e =
        assertThrows(LocalizedIllegalArgumentException.class, action::run);
    assertEquals(MANDATORY_MISSING, e.getMessage());
  }
}
