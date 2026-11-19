package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.field.LocalizedIllegalStateException;
import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.field.rule.FieldDisplayRuleValidator;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.InventoryFieldDisplayRules.RecordBeforeUpdate;
import com.researchspace.model.record.TestFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryFieldDisplayRulesTest {

  private static final String GMO_KEY = "6f1c2e0a-8d5b-4c51-9a3e-2b7f1d0c4e9a";
  private static final String PERMIT_KEY = "0b7e2c5d-3a41-4f6e-8c19-5d2a7e4b1f30";
  private static final String NOTES_KEY = "9a3c1e7f-2b4d-4e85-a6c0-1f8d3b5e7a92";

  private User user;
  private SampleTemplate template;
  private InventoryRadioField gmo;
  private InventoryStringField permit;

  @BeforeEach
  void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    template =
        (SampleTemplate)
            TestFactory.createSampleWithSubSamplesAndEverything(user, 1).copyToTemplate(user);
    gmo = radio(1L, "Is this a GMO?", GMO_KEY, List.of("Yes", "No"));
    template.addSampleField(gmo);
    permit = string(2L, "GMO permit number", PERMIT_KEY);
    permit.setMandatory(true);
    permit.setDisplayRule(showWhen(GMO_KEY, "Yes"));
    template.addSampleField(permit);
  }

  @Test
  void copiesCarryKeyAndRule() {
    InventoryEntityField copy = permit.shallowCopy();

    assertEquals(PERMIT_KEY, copy.getFieldKey());
    assertEquals(permit.getDisplayRule(), copy.getDisplayRule());
  }

  @Test
  void templateDuplicationThenRecordCreationKeepsRulesOnTheRightFields() {
    SampleTemplate duplicate = template.copy(user);
    Sample sample = duplicate.copyFromTemplate(user);

    InventoryEntityField sampleGmo = sample.getActiveFields().get(0);
    InventoryEntityField samplePermit = sample.getActiveFields().get(1);
    assertEquals(GMO_KEY, sampleGmo.getFieldKey());
    assertEquals(GMO_KEY, samplePermit.getDisplayRule().sourceFieldKey());
    assertEquals(
        List.of(true, false), InventoryFieldDisplayRules.visibility(sample.getActiveFields()));

    sampleGmo.setSelectedOptions(List.of("Yes"));
    assertEquals(
        List.of(true, true), InventoryFieldDisplayRules.visibility(sample.getActiveFields()));
  }

  @Test
  void setterSkipsMandatoryCheckOnlyForFieldsWithARule() {
    Sample sample = template.copyFromTemplate(user);
    InventoryEntityField samplePermit = sample.getActiveFields().get(1);
    assertDoesNotThrow(() -> samplePermit.setFieldData(""));

    samplePermit.setDisplayRule(null);
    assertThrows(IllegalArgumentException.class, () -> samplePermit.setFieldData(""));
  }

  @Test
  void validTemplateRulesPass() {
    assertDoesNotThrow(template::assertFieldDisplayRulesValid);
  }

  @Test
  void newFieldsCanReferenceEachOtherInOneSave() {
    // both new, and the dependent is listed before the source it depends on
    InventoryStringField detail = string(3L, "Detail", UUID.randomUUID().toString());
    detail.setDisplayRule(showWhen(NOTES_KEY, "Late"));
    template.addSampleField(detail);
    InventoryRadioField stage = radio(4L, "Stage", NOTES_KEY, List.of("Early", "Late"));
    stage.setDisplayRule(showWhen(GMO_KEY, "Yes"));
    template.addSampleField(stage);

    assertDoesNotThrow(template::assertFieldDisplayRulesValid);
  }

  @Test
  void ruleOnAStringSourceIsRejected() {
    InventoryStringField notes = string(3L, "Notes", NOTES_KEY);
    notes.setDisplayRule(new FieldDisplayRule(PERMIT_KEY, ConditionOperator.HAS_VALUE, null));
    template.addSampleField(notes);

    assertRejected(FieldDisplayRuleValidator.SOURCE_TYPE, "Notes");
  }

  @Test
  void deletingASourceLeavesADanglingRuleThatIsRejected() {
    template.deleteSampleField(gmo, false);

    assertRejected(FieldDisplayRuleValidator.SOURCE_MISSING, "GMO permit number");

    permit.setDisplayRule(null);
    assertDoesNotThrow(template::assertFieldDisplayRulesValid);
  }

  @Test
  void keyOfASoftDeletedFieldCannotBeReused() {
    template.deleteSampleField(gmo, false);
    permit.setDisplayRule(null);
    InventoryRadioField reused = radio(3L, "Reused", GMO_KEY, List.of("A"));
    template.addSampleField(reused);

    assertRejected(FieldDisplayRuleValidator.KEY_DUPLICATE, "Reused");
  }

  @Test
  void upperCaseKeyAndUnknownOptionAndCycleAreRejected() {
    permit.setFieldKey(PERMIT_KEY.toUpperCase());
    permit.setDisplayRule(null);
    assertRejected(FieldDisplayRuleValidator.KEY_INVALID, "GMO permit number");

    permit.setFieldKey(PERMIT_KEY);
    permit.setDisplayRule(showWhen(GMO_KEY, "Maybe"));
    assertRejected(FieldDisplayRuleValidator.VALUE_UNKNOWN, "GMO permit number");

    InventoryRadioField other = radio(3L, "Other", NOTES_KEY, List.of("A"));
    other.setDisplayRule(showWhen(GMO_KEY, "Yes"));
    gmo.setDisplayRule(showWhen(NOTES_KEY, "A"));
    permit.setDisplayRule(null);
    template.addSampleField(other);
    assertRejected(FieldDisplayRuleValidator.CYCLE, "Is this a GMO?");
  }

  @Test
  void createRequiresOnlyShownMandatoryFields() {
    Sample sample = template.copyFromTemplate(user);
    List<InventoryEntityField> fields = sample.getActiveFields();
    assertDoesNotThrow(
        () -> InventoryFieldDisplayRules.assertShownMandatoryFieldsFilled(fields, f -> false));

    fields.get(0).setSelectedOptions(List.of("Yes"));
    assertMandatoryMissing(
        () -> InventoryFieldDisplayRules.assertShownMandatoryFieldsFilled(fields, f -> false));
    assertDoesNotThrow(
        () -> InventoryFieldDisplayRules.assertShownMandatoryFieldsFilled(fields, f -> true));

    fields.get(1).setFieldData("P-1");
    assertDoesNotThrow(
        () -> InventoryFieldDisplayRules.assertShownMandatoryFieldsFilled(fields, f -> false));
  }

  @Test
  void updateRejectsRevealingOrEmptyingAShownMandatoryField() {
    Sample sample = template.copyFromTemplate(user);
    List<InventoryEntityField> fields = sample.getActiveFields();

    // revealing an empty mandatory field
    RecordBeforeUpdate before = InventoryFieldDisplayRules.beforeUpdate(fields);
    fields.get(0).setSelectedOptions(List.of("Yes"));
    assertMandatoryMissing(() -> before.assertShownMandatoryFieldsFilled(fields, f -> false));

    // emptying a shown mandatory field
    fields.get(1).setFieldData("P-1");
    RecordBeforeUpdate filled = InventoryFieldDisplayRules.beforeUpdate(fields);
    fields.get(1).setFieldData("");
    assertMandatoryMissing(() -> filled.assertShownMandatoryFieldsFilled(fields, f -> false));
  }

  @Test
  void updateAllowsAnUnrelatedEditBesideAnEmptyShownMandatoryField() {
    Sample sample = template.copyFromTemplate(user);
    List<InventoryEntityField> fields = sample.getActiveFields();
    fields.get(0).setSelectedOptions(List.of("Yes"));
    // shown and empty, as update to the latest template version may leave it

    RecordBeforeUpdate before = InventoryFieldDisplayRules.beforeUpdate(fields);
    sample.setName("renamed");
    assertDoesNotThrow(() -> before.assertShownMandatoryFieldsFilled(fields, f -> false));
  }

  @Test
  void updateTreatsABlankWrittenOverAnEmptyFieldAsUnchanged() {
    Sample sample = template.copyFromTemplate(user);
    List<InventoryEntityField> fields = sample.getActiveFields();
    fields.get(0).setSelectedOptions(List.of("Yes"));
    // as update to the latest template version leaves a field it adds
    fields.get(1).setData(null);

    RecordBeforeUpdate before = InventoryFieldDisplayRules.beforeUpdate(fields);
    // clients send "" for empty content
    fields.get(1).setFieldData("");
    assertDoesNotThrow(() -> before.assertShownMandatoryFieldsFilled(fields, f -> false));
  }

  @Test
  void ruledLinkIsJudgedOnItsLink() {
    InventoryLinkField related = new InventoryLinkField("Related protocol");
    related.setId(3L);
    related.setFieldKey(NOTES_KEY);
    related.setMandatory(true);
    related.setDisplayRule(showWhen(GMO_KEY, "Yes"));
    template.addSampleField(related);
    Sample sample = template.copyFromTemplate(user);
    List<InventoryEntityField> fields = sample.getActiveFields();
    InventoryLinkField sampleLink = (InventoryLinkField) fields.get(2);

    // create: hidden, so an empty link is allowed; shown, it must be filled
    assertDoesNotThrow(
        () -> InventoryFieldDisplayRules.assertShownMandatoryFieldsFilled(fields, f -> false));
    fields.get(0).setSelectedOptions(List.of("Yes"));
    fields.get(1).setFieldData("P-1");
    assertMandatoryMissing(
        () -> InventoryFieldDisplayRules.assertShownMandatoryFieldsFilled(fields, f -> false));
    sampleLink.setLink(link());
    assertDoesNotThrow(
        () -> InventoryFieldDisplayRules.assertShownMandatoryFieldsFilled(fields, f -> false));

    // update: clearing a shown mandatory link is rejected
    RecordBeforeUpdate shownAndFilled = InventoryFieldDisplayRules.beforeUpdate(fields);
    sampleLink.setLink(null);
    assertMandatoryMissing(
        () -> shownAndFilled.assertShownMandatoryFieldsFilled(fields, f -> false));

    // update: revealing an empty mandatory link is rejected
    fields.get(0).setSelectedOptions(List.of("No"));
    RecordBeforeUpdate hiddenAndEmpty = InventoryFieldDisplayRules.beforeUpdate(fields);
    fields.get(0).setSelectedOptions(List.of("Yes"));
    assertMandatoryMissing(
        () -> hiddenAndEmpty.assertShownMandatoryFieldsFilled(fields, f -> false));

    // update: clearing a hidden one is allowed
    fields.get(0).setSelectedOptions(List.of("No"));
    sampleLink.setLink(link());
    RecordBeforeUpdate hiddenAndFilled = InventoryFieldDisplayRules.beforeUpdate(fields);
    sampleLink.setLink(null);
    assertDoesNotThrow(() -> hiddenAndFilled.assertShownMandatoryFieldsFilled(fields, f -> false));
  }

  @Test
  void updateCountsTransitiveReveals() {
    InventoryRadioField stage = radio(3L, "Stage", NOTES_KEY, List.of("Early", "Late"));
    stage.setDisplayRule(showWhen(GMO_KEY, "Yes"));
    template.addSampleField(stage);
    permit.setDisplayRule(showWhen(NOTES_KEY, "Late"));
    Sample sample = template.copyFromTemplate(user);
    List<InventoryEntityField> fields = sample.getActiveFields();
    fields.get(2).setSelectedOptions(List.of("Late")); // stage, hidden because GMO is unset

    RecordBeforeUpdate before = InventoryFieldDisplayRules.beforeUpdate(fields);
    fields.get(0).setSelectedOptions(List.of("Yes"));
    assertMandatoryMissing(() -> before.assertShownMandatoryFieldsFilled(fields, f -> false));
  }

  @Test
  void updateToLatestChecksBecomingMandatoryAfterTheWholeSync() {
    permit.setMandatory(false);
    permit.setDisplayRule(null);
    Sample sample = template.copyFromTemplate(user);

    // one template version adds a rule and makes the field mandatory: hidden, so allowed
    permit.setMandatory(true);
    permit.setDisplayRule(showWhen(GMO_KEY, "Yes"));
    template.increaseVersion();
    assertDoesNotThrow(sample::updateToLatestTemplateVersion);
    assertTrue(sample.getActiveFields().get(1).isMandatory());

    // shown and empty when it becomes mandatory: rejected
    Sample shown = sampleOfOlderVersionWithOptionalPermit();
    shown.getActiveFields().get(0).setSelectedOptions(List.of("Yes"));
    LocalizedIllegalStateException e =
        assertThrows(LocalizedIllegalStateException.class, shown::updateToLatestTemplateVersion);
    assertEquals("validation.inventoryField.mandatoryInLatestTemplate", e.getMessage());
  }

  @Test
  void updateToLatestLetsAnEmptyFieldBecomeOptional() {
    Sample sample = template.copyFromTemplate(user);
    sample.getActiveFields().get(0).setSelectedOptions(List.of("Yes"));
    // shown, mandatory and empty in the record's version; optional in the latest
    permit.setMandatory(false);
    template.increaseVersion();

    assertDoesNotThrow(sample::updateToLatestTemplateVersion);
    assertFalse(sample.getActiveFields().get(1).isMandatory());
  }

  @Test
  void updateToLatestStillRejectsAnEmptyFieldWithoutARuleBecomingMandatory() {
    permit.setDisplayRule(null);
    Sample sample = sampleOfOlderVersionWithOptionalPermit();

    assertThrows(LocalizedIllegalStateException.class, sample::updateToLatestTemplateVersion);
  }

  @Test
  void updateToLatestGivesLegacyRecordFieldsTheTemplateKeyAndRule() {
    Sample sample = template.copyFromTemplate(user);
    sample.getActiveFields().forEach(f -> f.setFieldKey(null));
    sample.getActiveFields().forEach(f -> f.setDisplayRule(null));
    template.increaseVersion();

    sample.updateToLatestTemplateVersion();

    assertEquals(GMO_KEY, sample.getActiveFields().get(0).getFieldKey());
    assertEquals(PERMIT_KEY, sample.getActiveFields().get(1).getFieldKey());
    assertEquals(permit.getDisplayRule(), sample.getActiveFields().get(1).getDisplayRule());
  }

  @Test
  void updateToLatestShowsAFieldDeletedFromTheTemplateButKept() {
    Sample sample = template.copyFromTemplate(user);
    // "No" hides the permit under its rule, so it was left empty while mandatory
    sample.getActiveFields().get(0).setSelectedOptions(List.of("No"));
    template.deleteSampleField(permit, false);
    template.increaseVersion();

    sample.updateToLatestTemplateVersion();

    InventoryEntityField keptPermit = sample.getActiveFields().get(1);
    assertNull(keptPermit.getDisplayRule());
    assertFalse(keptPermit.isMandatory());
    assertEquals(
        List.of(true, true), InventoryFieldDisplayRules.visibility(sample.getActiveFields()));
    assertDoesNotThrow(() -> keptPermit.setFieldData(""));
  }

  @Test
  void updateToLatestLeavesTheRuleAndMandatoryOfAFieldRemovedWithItsTemplateField() {
    Sample sample = template.copyFromTemplate(user);
    InventoryEntityField samplePermit = sample.getActiveFields().get(1);
    template.deleteSampleField(permit, true);
    template.increaseVersion();

    sample.updateToLatestTemplateVersion();

    assertTrue(samplePermit.isDeleted());
    assertEquals(showWhen(GMO_KEY, "Yes"), samplePermit.getDisplayRule());
    assertTrue(samplePermit.isMandatory());
  }

  @Test
  void updateToLatestSkippingVersionsLeavesTheRuleAndMandatoryOfARemovedField() {
    Sample sample = template.copyFromTemplate(user);
    InventoryEntityField samplePermit = sample.getActiveFields().get(1);
    // a later version makes the permit optional and unconditional, a later one removes it
    permit.setMandatory(false);
    permit.setDisplayRule(null);
    template.increaseVersion();
    template.deleteSampleField(permit, true);
    template.increaseVersion();

    sample.updateToLatestTemplateVersion();

    assertTrue(samplePermit.isDeleted());
    assertEquals(PERMIT_KEY, samplePermit.getFieldKey());
    assertEquals(showWhen(GMO_KEY, "Yes"), samplePermit.getDisplayRule());
    assertTrue(samplePermit.isMandatory());
  }

  @Test
  void updateToLatestKeepsMandatoryOnAKeptFieldThatHadNoRule() {
    InventoryStringField notes = string(3L, "Notes", NOTES_KEY);
    notes.setMandatory(true);
    template.addSampleField(notes);
    Sample sample = template.copyFromTemplate(user);
    template.deleteSampleField(notes, false);
    template.increaseVersion();

    sample.updateToLatestTemplateVersion();

    InventoryEntityField keptNotes = sample.getActiveFields().get(2);
    assertEquals("Notes", keptNotes.getName());
    assertNull(keptNotes.getDisplayRule());
    assertTrue(keptNotes.isMandatory());
  }

  @Test
  void safetyNetGivesTemplateFieldsAKeyButNotRecordFields() {
    InventoryStringField templateField = string(3L, "No key", null);
    template.addSampleField(templateField);
    templateField.validateBeforeSave();
    assertNotNull(templateField.getFieldKey());
    assertDoesNotThrow(() -> UUID.fromString(templateField.getFieldKey()));

    Sample sample = template.copyFromTemplate(user);
    InventoryEntityField recordField = sample.getActiveFields().get(0);
    recordField.setFieldKey(null);
    recordField.validateBeforeSave();
    assertNull(recordField.getFieldKey());
  }

  /** A sample of an older template version in which "GMO permit number" was optional. */
  private Sample sampleOfOlderVersionWithOptionalPermit() {
    permit.setMandatory(false);
    Sample sample = template.copyFromTemplate(user);
    permit.setMandatory(true);
    template.increaseVersion();
    return sample;
  }

  private void assertRejected(String code, String fieldName) {
    LocalizedIllegalArgumentException e =
        assertThrows(
            LocalizedIllegalArgumentException.class, template::assertFieldDisplayRulesValid);
    assertEquals(code, e.getMessage());
    assertEquals(code + "|" + fieldName, e.resolve((c, args) -> c + "|" + args[0]));
  }

  private static void assertMandatoryMissing(Runnable check) {
    LocalizedIllegalArgumentException e =
        assertThrows(LocalizedIllegalArgumentException.class, check::run);
    assertEquals("validation.inventoryField.invalidForFieldType", e.getMessage());
  }

  private static InventoryLink link() {
    InventoryLink link = new InventoryLink();
    link.setTargetGlobalId("SA42");
    link.setRelationType("References");
    return link;
  }

  private static FieldDisplayRule showWhen(String sourceKey, String value) {
    return new FieldDisplayRule(sourceKey, ConditionOperator.IS_ANY_OF, List.of(value));
  }

  private static InventoryRadioField radio(Long id, String name, String key, List<String> options) {
    InventoryRadioFieldDef def = new InventoryRadioFieldDef();
    def.setRadioOptionsList(options);
    InventoryRadioField field = new InventoryRadioField(def, name);
    field.setId(id);
    field.setFieldKey(key);
    return field;
  }

  private static InventoryStringField string(Long id, String name, String key) {
    InventoryStringField field = new InventoryStringField(name);
    field.setId(id);
    field.setFieldKey(key);
    return field;
  }
}
