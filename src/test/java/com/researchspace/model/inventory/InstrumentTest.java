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
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.record.TestFactory;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class InstrumentTest {

  private Instrument instrument;
  private User anyUser;

  @BeforeEach
  public void setUp() {
    anyUser = TestFactory.createAnyUser("any");
    instrument = TestFactory.createBasicInstrumentOutsideContainer(anyUser);
    instrument.setId(5L);
  }

  @Test
  public void testInitialProperties() {
    assertNotNull(instrument.getModificationDate());
    assertNotNull(instrument.getCreationDate());
    assertFalse(instrument.isDeleted());
    assertFalse(instrument.isTemplate());
    assertEquals(InventoryRecord.InventoryRecordType.INSTRUMENT, instrument.getType());
    assertEquals("IN" + instrument.getId(), instrument.getOid().toString());
    assertEquals("IN" + instrument.getId() + "v1", instrument.getOidWithVersion().toString());
  }

  @Test
  @DisplayName("Use OID to distinguish instruments from instrument templates")
  public void globalId() {
    assertTrue(instrument.getGlobalIdentifier().startsWith("IN"));
    assertEquals(GlobalIdPrefix.IN, instrument.getOid().getPrefix());

    InstrumentTemplate template = (InstrumentTemplate) instrument.copyToTemplate(anyUser);
    template.setId(6L);

    assertTrue(template.getGlobalIdentifier().startsWith("NT"));
    assertEquals(GlobalIdPrefix.NT, template.getOid().getPrefix());
    assertFalse(template.getOid().toString().endsWith("v1"));
    assertTrue(template.getOidWithVersion().toString().endsWith("v1"));
  }

  @Test
  public void copy() {
    Instrument copied = (Instrument) instrument.copy(anyUser);
    assertFalse(copied.isTemplate());
    assertNull(copied.getGlobalIdentifier());
    assertEquals(instrument.getName() + "_COPY", copied.getName());
    assertEquals(0, copied.getActiveFields().size());
    assertNull(copied.getInstrumentTemplate());
    assertNull(copied.getTemplateLinkedVersion());
  }

  @Test
  @DisplayName("Make instrument template from instrument")
  public void copyToTemplate() {
    InstrumentTemplate template = (InstrumentTemplate) instrument.copyToTemplate(anyUser);
    assertTrue(template.isTemplate());
    assertEquals(InventoryRecord.InventoryRecordType.INSTRUMENT_TEMPLATE, template.getType());

    Instrument justCopy = (Instrument) instrument.copy(anyUser);
    assertFalse(justCopy.isTemplate());
    IllegalStateException iae =
        assertThrows(IllegalStateException.class, () -> justCopy.copyFromTemplate(anyUser));
    assertEquals("Only an InstrumentTemplate can be used to copy from a template", iae.getMessage());

    InstrumentTemplate anotherTemplate = (InstrumentTemplate) justCopy.copyToTemplate(anyUser);
    assertTrue(anotherTemplate.isTemplate());
  }

  @Test
  @DisplayName("Make instrument from template")
  public void copyFromTemplate() {
    InstrumentTemplate template = (InstrumentTemplate) instrument.copyToTemplate(anyUser);
    template.increaseVersion();
    template.increaseVersion();

    Instrument copiedInstrument = (Instrument) template.copyFromTemplate(anyUser);
    assertFalse(copiedInstrument.isTemplate());
    assertEquals(template, copiedInstrument.getInstrumentTemplate());
    assertEquals(template.getVersion(), copiedInstrument.getTemplateLinkedVersion());
    assertEquals(1L, copiedInstrument.getVersion());
  }

  @Test
  @DisplayName("Update instrument to latest template definition")
  public void updateToLatestTemplateDefinition() {
    InstrumentTemplate template = (InstrumentTemplate) instrument.copyToTemplate(anyUser);
    InventoryTextField textField = buildTextField(1L, 2, "text", "default-text");
    addField(template, textField);

    InventoryRadioFieldDef radioDef = new InventoryRadioFieldDef();
    radioDef.setId(10L);
    radioDef.setRadioOptionsList(Arrays.asList("b", "c", "d", "e"));
    InventoryRadioField radioField = new InventoryRadioField(radioDef, "radio");
    radioField.setId(2L);
    radioField.setColumnIndex(1);
    addField(template, radioField);

    template.refreshActiveFieldsAndColumnIndex();
    template.increaseVersion();

    Instrument newInstrument = (Instrument) template.copyFromTemplate(anyUser);
    assertEquals(2, newInstrument.getTemplateLinkedVersion());

    boolean updateResult = newInstrument.updateToLatestTemplateVersion();
    assertFalse(updateResult);

    textField.setName("text updated");
    addField(template, buildTextField(3L, 3, "serial", "serial-default"));
    template.increaseVersion();

    updateResult = newInstrument.updateToLatestTemplateVersion();
    assertTrue(updateResult);
    assertEquals(3, newInstrument.getTemplateLinkedVersion());

    assertFalse(newInstrument.updateToLatestTemplateVersion());
  }

  @Test
  public void updateToLatestTemplateVersionOnNonTemplatedInstrumentThrowsException() {
    IllegalStateException ise =
        assertThrows(IllegalStateException.class, () -> instrument.updateToLatestTemplateVersion());
    assertEquals("The instrument is not based on any template", ise.getMessage());
  }

  @Test
  public void initiallyHasNoParent() {
    assertNull(instrument.getParentContainer());
    assertNull(instrument.getParentId());
    assertNull(instrument.getParentLocation());
    assertFalse(instrument.isStoredInContainer());
    assertNull(instrument.getLastNonWorkbenchParent());
    assertNull(instrument.getLastMoveDate());
  }

  @Test
  public void moveToContainerAndBack() {
    Container container = Container.createListContainer(true, true, true);
    container.setId(10L);

    instrument.moveToNewParent(container);

    assertEquals(container.getId(), instrument.getParentId());
    assertNotNull(instrument.getParentLocation());
    assertTrue(instrument.isStoredInContainer());
    assertNotNull(instrument.getLastMoveDate());
    assertNull(instrument.getLastNonWorkbenchParent()); // first move, no previous non-workbench parent
    assertEquals(1, container.getContentCount());

    instrument.removeFromCurrentParent();

    assertNull(instrument.getParentContainer());
    assertNull(instrument.getParentId());
    assertFalse(instrument.isStoredInContainer());
    assertEquals(container.getId(), instrument.getLastNonWorkbenchParent().getId());
    assertEquals(0, container.getContentCount());
  }

  @Test
  public void moveBetweenContainers() {
    Container container1 = Container.createListContainer(true, true, true);
    container1.setId(10L);
    Container container2 = Container.createListContainer(true, true, true);
    container2.setId(11L);

    instrument.moveToNewParent(container1);
    assertEquals(container1.getId(), instrument.getParentId());
    assertEquals(1, container1.getContentCount());

    instrument.moveToNewParent(container2);
    assertEquals(container2.getId(), instrument.getParentId());
    assertEquals(0, container1.getContentCount());
    assertEquals(1, container2.getContentCount());
    assertEquals(container1.getId(), instrument.getLastNonWorkbenchParent().getId());
  }

  @Test
  public void moveToWorkbenchDoesNotUpdateLastNonWorkbenchParent() {
    Container realContainer = Container.createListContainer(true, true, true);
    realContainer.setId(10L);
    Container workbench = TestFactory.createWorkbench(anyUser);
    workbench.setId(99L);

    // establish a non-workbench parent first
    instrument.moveToNewParent(realContainer);
    assertEquals(realContainer.getId(), instrument.getParentId());

    // move to workbench — lastNonWorkbenchParent should be set to realContainer
    instrument.moveToNewParent(workbench);
    assertEquals(workbench.getId(), instrument.getParentId());
    assertEquals(realContainer.getId(), instrument.getLastNonWorkbenchParent().getId());

    // move back out — lastNonWorkbenchParent should still be realContainer (workbench is skipped)
    instrument.removeFromCurrentParent();
    assertNull(instrument.getParentContainer());
    assertEquals(realContainer.getId(), instrument.getLastNonWorkbenchParent().getId());
  }

  @Test
  public void setLastNonWorkbenchParentRejectsWorkbench() {
    Container workbench = TestFactory.createWorkbench(anyUser);
    IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
        () -> instrument.setLastNonWorkbenchParent(workbench));
    assertEquals("Can't set workbench as lastNonWorkbenchParent", iae.getMessage());
  }

  @Test
  public void copiedInstrumentHasNoParent() {
    Container container = Container.createListContainer(true, true, true);
    container.setId(10L);
    instrument.moveToNewParent(container);

    Instrument copy = (Instrument) instrument.copy(anyUser);

    assertNull(copy.getParentLocation());
    assertNull(copy.getParentId());
    assertNull(copy.getId());
    assertFalse(copy.isStoredInContainer());
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






