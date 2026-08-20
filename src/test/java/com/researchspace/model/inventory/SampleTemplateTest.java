package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.units.RSUnitDef;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SampleTemplateTest {

  private SampleTemplate template;
  private User anyUser;

  @BeforeEach
  public void setUp() {
    anyUser = TestFactory.createAnyUser("any");
    RecordFactory rf = new RecordFactory();
    template = rf.createSampleTemplate("test template", anyUser);
    template.setId(6L);
  }

  @Test
  public void testInitialProperties() {
    assertNotNull(template.getModificationDate());
    assertNotNull(template.getCreationDate());
    assertFalse(template.isDeleted());
    assertTrue(template.isTemplate());
    assertEquals(InventoryRecord.InventoryRecordType.SAMPLE_TEMPLATE, template.getType());
    assertTrue(template.isSampleTemplate());
    assertFalse(template.isSample());
    assertEquals("IT" + template.getId(), template.getOid().toString());
    assertEquals("IT" + template.getId() + "v1", template.getOidWithVersion().toString());
    // default subsample exists after factory creation
    assertEquals(1, template.getSubSamples().size());
    assertEquals(1, template.getActiveSubSamplesCount());
    // default subsample alias
    assertEquals(SubSampleName.ALIQUOT.getDisplayName(), template.getSubSampleAlias());
    // default unit
    assertEquals(RSUnitDef.MILLI_LITRE.getId(), template.getDefaultUnitId());
  }

  @Test
  @DisplayName("Quantifiable accessors return null when no quantity is set")
  public void quantityAccessorsReturnNullWhenNoQuantity() {
    // a SampleEntity may have a null quantityInfo (a template has none; recalculateTotalQuantity
    // also nulls it on a Sample with no subsample quantities). getUnitId()/getNumericValue() must
    // return null in that case rather than NPE when the entity is treated as a Quantifiable.
    assertNull(template.getQuantityInfo());
    assertNull(template.getUnitId());
    assertNull(template.getNumericValue());
  }

  @Test
  @DisplayName("getLinkedSampleTemplate is null for a template and a template-less sample")
  public void getLinkedSampleTemplateIsNullWithoutALinkedTemplate() {
    assertNull(template.getLinkedSampleTemplate());
    assertNull(new Sample().getLinkedSampleTemplate());
  }

  @Test
  @DisplayName("getLinkedSampleTemplate resolves the template for a sample and its subsamples")
  public void getLinkedSampleTemplateResolvesForSampleAndSubSamples() {
    Sample sampleFromTemplate = template.copyFromTemplate(anyUser);
    // Sample returns its own template; SubSample delegates to its parent sample - both via
    // polymorphic dispatch, so they resolve through a Hibernate proxy with no unproxy/cast
    assertEquals(template, sampleFromTemplate.getLinkedSampleTemplate());
    assertEquals(template, sampleFromTemplate.getSubSamples().get(0).getLinkedSampleTemplate());
  }

  @Test
  @DisplayName("Use OID to distinguish sample templates from samples")
  public void globalId() {
    assertTrue(template.getGlobalIdentifier().startsWith("IT"));
    assertEquals(GlobalIdPrefix.IT, template.getOid().getPrefix());

    Sample sampleFromTemplate = template.copyFromTemplate(anyUser);
    sampleFromTemplate.setId(7L);

    assertTrue(sampleFromTemplate.getGlobalIdentifier().startsWith("SA"));
    assertEquals(GlobalIdPrefix.SA, sampleFromTemplate.getOid().getPrefix());
  }

  @Test
  @DisplayName("Make sample from template")
  public void copyFromTemplate() {
    template.increaseVersion();

    Sample copiedSample = template.copyFromTemplate(anyUser);
    assertFalse(copiedSample.isTemplate());
    assertEquals(template, copiedSample.getSTemplate());
    assertEquals(template.getVersion(), copiedSample.getSTemplateLinkedVersion());
    assertEquals(1L, copiedSample.getVersion());
    assertEquals(1, copiedSample.getSubSamples().get(0).getVersion());
    copiedSample.setId(7L);
    assertTrue(copiedSample.getGlobalIdentifier().startsWith("SA"));
  }

  @Test
  @DisplayName("copyFromTemplate copies fields with templateField links set")
  public void copyFromTemplateFieldLinks() {
    InventoryTextField field = new InventoryTextField("material");
    field.setId(1L);
    template.addSampleField(field);

    Sample copiedSample = template.copyFromTemplate(anyUser);
    assertEquals(1, copiedSample.getActiveFields().size());
    InventoryEntityField copiedField = copiedSample.getActiveFields().get(0);
    assertEquals("material", copiedField.getName());
    // template field link should point back to the original template field
    assertEquals(field, copiedField.getTemplateField());
  }

  @Test
  public void copy() {
    addField(template, buildTextField(1L, 1, "manufacturer", "Acme"));

    SampleTemplate copied = template.copy(anyUser);
    assertTrue(copied.isTemplate());
    assertNull(copied.getGlobalIdentifier());
    assertEquals(template.getName() + "_COPY", copied.getName());
    assertEquals(1, copied.getActiveFields().size());
    assertEquals("manufacturer", copied.getActiveFields().get(0).getName());
    assertEquals("Acme", copied.getActiveFields().get(0).getFieldData());
    // template copy is NOT linked to the original via sTemplate
    // (the copy is a standalone template, not a sample instance)
  }

  @Test
  @DisplayName("SampleTemplate cannot be copied to template")
  public void copyToTemplateNotAllowed() {
    IllegalArgumentException iae =
        assertThrows(IllegalArgumentException.class, () -> template.copyToTemplate(anyUser));
    assertEquals("Only a Sample can be copied into a SampleTemplate", iae.getMessage());
  }

  @Test
  @DisplayName("Reserved field names differ from a plain Sample's")
  public void reservedFieldNames() {
    Sample plainSample = new Sample();

    // Template does NOT have the sample-specific fields "sample template", "storage temperature",
    // "total quantity", "subsamples" but DOES have template-specific ones
    assertFalse(template.getReservedFieldNames().contains("sample template"));
    assertFalse(template.getReservedFieldNames().contains("storage temperature"));
    assertFalse(template.getReservedFieldNames().contains("total quantity"));
    assertFalse(template.getReservedFieldNames().contains("subsamples"));
    assertTrue(template.getReservedFieldNames().contains("subsample alias"));
    assertTrue(template.getReservedFieldNames().contains("quantity units"));
    assertTrue(template.getReservedFieldNames().contains("fields"));
    assertTrue(template.getReservedFieldNames().contains("samples"));

    // Plain Sample has the sample-specific fields but not the template-specific ones
    assertTrue(plainSample.getReservedFieldNames().contains("sample template"));
    assertTrue(plainSample.getReservedFieldNames().contains("storage temperature"));
    assertTrue(plainSample.getReservedFieldNames().contains("total quantity"));
    assertTrue(plainSample.getReservedFieldNames().contains("subsamples"));
    assertFalse(plainSample.getReservedFieldNames().contains("subsample alias"));
    assertFalse(plainSample.getReservedFieldNames().contains("quantity units"));
    assertFalse(plainSample.getReservedFieldNames().contains("fields"));
    assertFalse(plainSample.getReservedFieldNames().contains("samples"));
  }

  @Test
  @DisplayName("Direct addAttachedFile on a template throws IllegalArgumentException")
  public void directAttachmentOnTemplateThrows() {
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> template.addAttachedFile(new InventoryFile(null, null)));
    assertEquals("Sample Templates don't support file attachments yet", iae.getMessage());
  }

  @Test
  @DisplayName("Duplicating a template with an attachment carries the attachment to the copy")
  public void copyTemplateWithAttachmentCarriesAttachment() throws IOException {
    // getFiles().add() bypasses doAddAttachedFile(), so the inventoryRecord link and
    // the active-files cache must be set up manually - the same state shallowCopyBasicFields() produces.
    InventoryFile invFile = buildAttachment(anyUser);
    invFile.setInventoryRecord(template);
    template.getFiles().add(invFile);
    template.refreshActiveAttachedFiles();

    assertEquals(1, template.getAttachedFiles().size());

    SampleTemplate copied = template.copy(anyUser);
    assertEquals(1, copied.getAttachedFiles().size(), "attachment should be carried to the copy");
  }

  @Test
  @DisplayName("copyToTemplate on a sample with an attachment carries the attachment to the template")
  public void copyToTemplateWithAttachmentCarriesAttachment() throws IOException {
    Sample sample = TestFactory.createBasicSampleInContainer(anyUser);
    InventoryFile invFile = buildAttachment(anyUser);
    sample.addAttachedFile(invFile);
    assertEquals(1, sample.getAttachedFiles().size());

    SampleTemplate derivedTemplate = sample.copyToTemplate(anyUser);
    assertEquals(
        1,
        derivedTemplate.getAttachedFiles().size(),
        "attachment from sample should be carried to the derived template");
  }

  // -- helpers --

  private InventoryTextField buildTextField(
      Long id, Integer columnIndex, String name, String data) {
    InventoryTextField field = new InventoryTextField(name);
    field.setId(id);
    field.setColumnIndex(columnIndex);
    field.setFieldData(data);
    return field;
  }

  private void addField(SampleEntity sampleEntity, InventoryEntityField field) {
    field.setInventoryRecord(sampleEntity);
    sampleEntity.getFields().add(field);
    sampleEntity.refreshActiveFieldsAndColumnIndex();
  }

  private InventoryFile buildAttachment(User owner) throws IOException {
    FileStoreRoot fsRoot = new FileStoreRoot("/some/path");
    FileProperty fp =
        TestFactory.createAFileProperty(File.createTempFile("att", ".txt"), owner, fsRoot);
    return new InventoryFile("test-attachment.txt", fp);
  }
}
