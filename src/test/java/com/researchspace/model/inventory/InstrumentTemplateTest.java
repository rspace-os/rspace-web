package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.record.TestFactory;
import javax.persistence.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class InstrumentTemplateTest {

  private static final String TEMPLATE_MOVE_NOT_ALLOWED =
      "InstrumentTemplate cannot be moved or attached to containers";

  private InstrumentTemplate template;
  private User anyUser;

  @BeforeEach
  public void setUp() {
    anyUser = TestFactory.createAnyUser("any");
    Instrument instrument = TestFactory.createBasicInstrumentOutsideContainer(anyUser);
    template = (InstrumentTemplate) instrument.copyToTemplate(anyUser);
    template.setId(6L);
  }

  @Test
  public void testInitialProperties() {
    assertNotNull(template.getModificationDate());
    assertNotNull(template.getCreationDate());
    assertFalse(template.isDeleted());
    assertTrue(template.isTemplate());
    assertEquals(InventoryRecord.InventoryRecordType.INSTRUMENT_TEMPLATE, template.getType());
    assertEquals("NT" + template.getId(), template.getOid().toString());
    assertEquals("NT" + template.getId() + "v1", template.getOidWithVersion().toString());
  }

  @Test
  @DisplayName("Use OID to distinguish instrument templates from instruments")
  public void globalId() {
    assertTrue(template.getGlobalIdentifier().startsWith("NT"));
    assertEquals(GlobalIdPrefix.NT, template.getOid().getPrefix());

    Instrument instrumentFromTemplate = (Instrument) template.copyFromTemplate(anyUser);
    instrumentFromTemplate.setId(7L);

    assertTrue(instrumentFromTemplate.getGlobalIdentifier().startsWith("IN"));
    assertEquals(GlobalIdPrefix.IN, instrumentFromTemplate.getOid().getPrefix());
  }

  @Test
  public void copy() {
    addField(template, buildTextField(1L, 1, "manufacturer", "Acme"));

    InstrumentTemplate copied = (InstrumentTemplate) template.copy(anyUser);
    assertTrue(copied.isTemplate());
    assertNull(copied.getGlobalIdentifier());
    assertEquals(template.getName() + "_COPY", copied.getName());
    assertEquals(1, copied.getActiveFields().size());
    assertEquals("manufacturer", copied.getActiveFields().get(0).getName());
    assertEquals("Acme", copied.getActiveFields().get(0).getFieldData());
    assertNull(copied.getParentLocation());
    assertNull(copied.getParentId());
  }

  @Test
  @DisplayName("InstrumentTemplate cannot be copied to template")
  public void copyToTemplateNotAllowed() {
    IllegalArgumentException iae =
        assertThrows(IllegalArgumentException.class, () -> template.copyToTemplate(anyUser));
    assertEquals("Only an Instrument can be copied into an InstrumentTemplate", iae.getMessage());
  }

  @Test
  @DisplayName("Make instrument from template")
  public void copyFromTemplate() {
    template.increaseVersion();

    Instrument copiedInstrument = (Instrument) template.copyFromTemplate(anyUser);
    assertFalse(copiedInstrument.isTemplate());
    assertEquals(template, copiedInstrument.getInstrumentTemplate());
    assertEquals(template.getVersion(), copiedInstrument.getTemplateLinkedVersion());
    assertEquals(1L, copiedInstrument.getVersion());
  }

  @Test
  @DisplayName("An instrument template is editable by default")
  public void isEditableTrueByDefault() {
    assertTrue(template.isEditable());
  }

  @Test
  @DisplayName("Duplicating a locked (non-editable) template yields an editable copy")
  public void copyOfNonEditableTemplateIsEditable() {
    template.setEditable(false);
    assertFalse(template.isEditable());

    InstrumentTemplate copied = (InstrumentTemplate) template.copy(anyUser);
    assertTrue(copied.isEditable(), "a duplicate of a locked template must be editable");
  }

  @Test
  @DisplayName("An instrument can still be created from a locked template")
  public void instrumentCanBeCreatedFromNonEditableTemplate() {
    template.setEditable(false);

    // instruments carry no editability flag at all (it is template-only), so an instrument built
    // from a locked template is an ordinary instrument
    Instrument fromTemplate = (Instrument) template.copyFromTemplate(anyUser);
    assertEquals(template, fromTemplate.getInstrumentTemplate());
  }

  @Test
  @DisplayName("isEditable is mapped non-nullable so existing rows never hydrate as locked")
  public void isEditableColumnIsMappedNonNullable() throws NoSuchMethodException {
    Column column = InstrumentTemplate.class.getMethod("isEditable").getAnnotation(Column.class);
    assertNotNull(column, "isEditable() must carry an explicit @Column mapping");
    assertEquals("isEditable", column.name());
    assertFalse(
        column.nullable(),
        "isEditable must be non-nullable to match the not-null, default-1 schema so pre-existing"
            + " rows stay editable rather than hydrating a NULL as false");
  }

  @Test
  public void movementOperationsNotAllowed() {
    Container container = Container.createListContainer(true, true, true);
    ContainerLocation location = new ContainerLocation(container);

    IllegalArgumentException iae =
        assertThrows(IllegalArgumentException.class, () -> template.moveToNewParent(container));
    assertEquals(TEMPLATE_MOVE_NOT_ALLOWED, iae.getMessage());

    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> template.moveToNewParentWithCoords(container, 1, 1));
    assertEquals(TEMPLATE_MOVE_NOT_ALLOWED, iae.getMessage());

    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> template.moveToNewParentAndLocation(container, location));
    assertEquals(TEMPLATE_MOVE_NOT_ALLOWED, iae.getMessage());

    iae = assertThrows(IllegalArgumentException.class, () -> template.removeFromCurrentParent());
    assertEquals(TEMPLATE_MOVE_NOT_ALLOWED, iae.getMessage());

    iae =
        assertThrows(
            IllegalArgumentException.class, () -> template.setLastNonWorkbenchParent(container));
    assertEquals(TEMPLATE_MOVE_NOT_ALLOWED, iae.getMessage());
  }

  private InventoryTextField buildTextField(Long id, Integer columnIndex, String name, String data) {
    InventoryTextField field = new InventoryTextField(name);
    field.setId(id);
    field.setColumnIndex(columnIndex);
    field.setFieldData(data);
    return field;
  }

  private void addField(InstrumentEntity instrumentEntity, InventoryEntityField field) {
    field.setInventoryRecord(instrumentEntity);
    instrumentEntity.getFields().add(field);
    instrumentEntity.refreshActiveFieldsAndColumnIndex();
  }
}

