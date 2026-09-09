package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryStringField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reconciling an operation's generated fields with the fields the created sample inherits from its
 * template.
 *
 * <p>A template may legitimately declare a field the operation also produces ("Passage number",
 * "Cryomedium"). Adding the generated one alongside it makes two fields with the same name, which
 * {@code InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames} rejects, so the
 * operation always failed for such a template and the wizard offered no way to repair the generated
 * name (Codex review, PR #1090). Renaming the generated field instead would break the Passage
 * counter, which finds the previous number by looking the field up by name.
 */
class SampleApiManagerImplTemplateFieldMergeTest {

  private static ApiExtraField operationField(String name, String content) {
    ApiExtraField field = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    field.setName(name);
    field.setContent(content);
    field.setNewFieldRequest(true);
    field.setOperationFieldKey("operations.passage.numberField");
    return field;
  }

  private static ApiSampleWithFullSubSamples sampleWith(ApiExtraField... extraFields) {
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples("Passaged");
    sample.setExtraFields(new ArrayList<>(List.of(extraFields)));
    return sample;
  }

  private static List<InventoryEntityField> inherited(String... names) {
    List<InventoryEntityField> fields = new ArrayList<>();
    for (String name : names) {
      fields.add(new InventoryStringField(name));
    }
    return fields;
  }

  @Test
  void writesTheGeneratedValueIntoTheInheritedFieldOfTheSameName() {
    ApiSampleWithFullSubSamples sample = sampleWith(operationField("Passage number", "4"));
    List<InventoryEntityField> templateFields = inherited("Passage number");

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    // The value lands in the inherited field, so the next Passage still finds it by name.
    assertEquals("4", templateFields.get(0).getFieldData());
    // and the duplicate is gone, so the uniqueness check passes.
    assertTrue(sample.getExtraFields().isEmpty(), "the generated duplicate should be absorbed");
  }

  @Test
  void matchesInheritedNamesTheWayTheUniquenessCheckDoes() {
    // The duplicate check trims and lowercases, so the merge has to agree with it or a name that
    // collides there would survive here and still be rejected.
    ApiSampleWithFullSubSamples sample = sampleWith(operationField("  passage NUMBER ", "7"));
    List<InventoryEntityField> templateFields = inherited("Passage number");

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals("7", templateFields.get(0).getFieldData());
    assertTrue(sample.getExtraFields().isEmpty());
  }

  @Test
  void leavesAGeneratedFieldAloneWhenTheTemplateDeclaresNoSuchName() {
    ApiSampleWithFullSubSamples sample = sampleWith(operationField("Passage number", "2"));
    List<InventoryEntityField> templateFields = inherited("Batch");

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals(1, sample.getExtraFields().size(), "no collision, so nothing to absorb");
    assertEquals("Passage number", sample.getExtraFields().get(0).getName());
  }

  @Test
  void leavesFieldsThatDidNotComeFromAnOperationAlone() {
    // A user's own extra field on POST /samples keeps the existing behaviour, a duplicate-name
    // rejection, rather than silently overwriting a template field's content.
    ApiExtraField userField = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    userField.setName("Passage number");
    userField.setContent("9");
    userField.setNewFieldRequest(true);
    ApiSampleWithFullSubSamples sample = sampleWith(userField);
    List<InventoryEntityField> templateFields = inherited("Passage number");

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals(1, sample.getExtraFields().size());
    assertNull(templateFields.get(0).getFieldData(), "template content must not be overwritten");
  }

  @Test
  void neverMergesIntoAnInheritedLinkField() {
    // A link field holds a structured InventoryLink, not text, so its content cannot be set from a
    // generated text field; leave the collision to the duplicate-name check rather than corrupt it.
    ApiSampleWithFullSubSamples sample = sampleWith(operationField("Related", "text"));
    List<InventoryEntityField> templateFields = new ArrayList<>();
    InventoryLinkField linkField = new InventoryLinkField();
    linkField.setName("Related");
    templateFields.add(linkField);

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals(1, sample.getExtraFields().size(), "a link field is not a merge target");
  }

  @Test
  void neverMergesAGeneratedLinkField() {
    ApiExtraField generatedLink = new ApiExtraField(ExtraFieldTypeEnum.LINK);
    generatedLink.setName("Passaged from");
    generatedLink.setNewFieldRequest(true);
    generatedLink.setOperationFieldKey("operations.passage.linkFieldName");
    ApiSampleWithFullSubSamples sample = sampleWith(generatedLink);
    List<InventoryEntityField> templateFields = inherited("Passaged from");

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals(1, sample.getExtraFields().size(), "a generated link is not merged into text");
  }
}
