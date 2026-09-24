package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.service.inventory.operations.OperationFieldNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A template may legitimately declare a field the operation also produces ("Passage number",
 * "Cryomedium"). Adding the generated one alongside it makes two fields with the same name, which
 * {@code InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames} rejects; renaming the
 * generated one instead would break the Passage counter, which finds the previous number by name.
 */
class SampleApiManagerImplTemplateFieldMergeTest {

  private static ApiExtraField operationField(String name, String content) {
    ApiExtraField field = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    field.setName(name);
    field.setContent(content);
    field.setNewFieldRequest(true);
    // The key is what distinguishes a generated field from a user's own, and only the server's
    // request builder can set one (the DTO property is READ_ONLY).
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

  private static ApiExtraField generatedLink(String name, String targetGlobalId) {
    ApiExtraField field = new ApiExtraField(ExtraFieldTypeEnum.LINK);
    field.setName(name);
    field.setNewFieldRequest(true);
    field.setOperationFieldKey(OperationFieldNames.DOCUMENTATION_LINK_KEY);
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType("IsDocumentedBy");
    link.setTargetGlobalId(targetGlobalId);
    field.setLink(link);
    return field;
  }

  @Test
  void renamesAGeneratedLinkThatCollidesWithAnInheritedFieldName() {
    ApiSampleWithFullSubSamples sample = sampleWith(generatedLink("Documented by", "SD123"));
    List<InventoryEntityField> templateFields = inherited("Documented by");

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals(1, sample.getExtraFields().size(), "a link is never absorbed into a text field");
    assertNotEquals(
        "documented by",
        sample.getExtraFields().get(0).getName().trim().toLowerCase(Locale.ROOT),
        "the generated link must not keep a name the uniqueness check will reject");
    assertNull(templateFields.get(0).getFieldData(), "the inherited field is left untouched");
  }

  @Test
  void writesTheGeneratedValueIntoTheInheritedFieldOfTheSameName() {
    ApiSampleWithFullSubSamples sample = sampleWith(operationField("Passage number", "4"));
    List<InventoryEntityField> templateFields = inherited("Passage number");

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals("4", templateFields.get(0).getFieldData());
    assertTrue(sample.getExtraFields().isEmpty(), "the generated duplicate should be absorbed");
  }

  @Test
  void matchesInheritedNamesTheWayTheUniquenessCheckDoes() {
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

  @Test
  void leavesAGeneratedFieldAloneWhenTheInheritedFieldRejectsItsContent() {
    ApiSampleWithFullSubSamples sample = sampleWith(operationField("Cryomedium", "10% DMSO"));
    List<InventoryEntityField> templateFields = new ArrayList<>();
    templateFields.add(new InventoryNumberField("Cryomedium"));

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals(1, sample.getExtraFields().size(), "the field must survive to be rejected");
    assertNull(templateFields.get(0).getFieldData(), "template content must not be overwritten");
  }

  @Test
  void stillMergesWhenTheInheritedFieldAcceptsTheGeneratedContent() {
    ApiSampleWithFullSubSamples sample = sampleWith(operationField("Passage number", "4"));
    List<InventoryEntityField> templateFields = new ArrayList<>();
    templateFields.add(new InventoryNumberField("Passage number"));

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals(0, sample.getExtraFields().size(), "the generated field is absorbed");
    assertEquals("4", templateFields.get(0).getFieldData());
  }

  @Test
  void acceptsAnImmutableExtraFieldListFromTheCaller() {
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples("Passaged");
    sample.setExtraFields(List.of(operationField("Passage number", "4")));

    List<InventoryEntityField> templateFields = inherited("Passage number");

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(sample, templateFields);

    assertEquals("4", templateFields.get(0).getFieldData());
    assertTrue(sample.getExtraFields().isEmpty(), "the generated duplicate should be absorbed");
  }

  @Test
  void acceptsAnImmutableExtraFieldListWhenNothingIsMerged() {
    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples("Complex");
    sample.setExtraFields(List.of(operationField("Passage number", "4")));

    SampleApiManagerImpl.mergeOperationFieldsIntoInheritedTemplateFields(
        sample, inherited("Batch"));

    assertEquals(1, sample.getExtraFields().size(), "no collision, so nothing to absorb");
  }
}
