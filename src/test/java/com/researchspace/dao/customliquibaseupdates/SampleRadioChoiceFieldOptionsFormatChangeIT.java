package com.researchspace.dao.customliquibaseupdates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryChoiceFieldDef;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.SampleField;
import com.researchspace.model.record.IRecordFactory;
import java.io.IOException;
import java.util.List;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SampleRadioChoiceFieldOptionsFormatChangeIT extends AbstractDBHelpers {

  private @Autowired IRecordFactory recordFactory;

  private @Autowired ContainerDao containerDao;

  private @Autowired SampleDao sampleDao;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkFormatUpdatedWithLiquibaseUpdate()
      throws IOException, SetupException, CustomChangeException, IllegalAccessException {

    User user = createInitAndLoginAnyUser();

    // save sample in old format
    openTransaction();
    Sample newSample = createSampleWithOldFormatRadioChoiceFields(user);
    Sample persistedSample = sampleDao.persistSampleTemplate(newSample);
    assertNotNull(persistedSample);
    List<SampleField> persistedFields = persistedSample.getActiveFields();
    commitTransaction();

    // confirm sample saved
    assertEquals(5, persistedFields.size());
    // confirm old content unparseable by current model methods
    // old radio format
    IllegalArgumentException iae =
        assertThrows(IllegalArgumentException.class, () -> persistedFields.get(0).getAllOptions());
    assertEquals(
        "couldn't convert [no-default-radio=option1&no-default-radio=option2] to options list",
        iae.getMessage());
    assertEquals(
        List.of("option1"),
        persistedFields.get(0).getSelectedOptions()); // old radio field content format is fine
    iae =
        assertThrows(IllegalArgumentException.class, () -> persistedFields.get(1).getAllOptions());
    assertEquals(
        "couldn't convert [v=Invitrogen&v=NEB&v=Amersham&v=Sigma] to options list",
        iae.getMessage());
    assertEquals(
        List.of("Sigma"),
        persistedFields.get(1).getSelectedOptions()); // old radio field content format is fine
    // old choice format
    iae =
        assertThrows(IllegalArgumentException.class, () -> persistedFields.get(2).getAllOptions());
    assertEquals(
        "couldn't convert [choiceField=optionA&choiceField=optionB] to options list",
        iae.getMessage());
    iae =
        assertThrows(
            IllegalArgumentException.class, () -> persistedFields.get(2).getSelectedOptions());
    assertEquals(
        "couldn't convert [fieldSelectedChoices=optionA] to options list", iae.getMessage());
    iae =
        assertThrows(IllegalArgumentException.class, () -> persistedFields.get(3).getAllOptions());
    assertEquals(
        "couldn't convert [choice & Field =optionA&choice & Field =optionB&choice & Field =and C]"
            + " to options list",
        iae.getMessage());
    iae =
        assertThrows(
            IllegalArgumentException.class, () -> persistedFields.get(3).getSelectedOptions());
    assertEquals(
        "couldn't convert [fieldSelectedChoices=optionB&fieldSelectedChoices=and C] to options"
            + " list",
        iae.getMessage());
    iae =
        assertThrows(IllegalArgumentException.class, () -> persistedFields.get(4).getAllOptions());
    assertEquals("couldn't convert [v=4&v=6&v=8&v=other] to options list", iae.getMessage());
    iae =
        assertThrows(
            IllegalArgumentException.class, () -> persistedFields.get(4).getSelectedOptions());
    assertEquals("couldn't convert [v=4&v=6] to options list", iae.getMessage());

    // run liquibase update
    SampleRadioChoiceFieldOptionsFormatChange_1_73 updater =
        new SampleRadioChoiceFieldOptionsFormatChange_1_73();
    updater.setUp();
    updater.execute(null);

    // retrieve the sample
    openTransaction();
    Sample retrievedSample = sampleDao.get(persistedSample.getId());
    assertNotNull(retrievedSample);
    List<SampleField> updatedFields = retrievedSample.getActiveFields();
    commitTransaction();

    // confirm sample updated fine and content now parseable
    assertEquals(5, updatedFields.size());
    // old radio fields
    assertEquals(List.of("option1", "option2"), updatedFields.get(0).getAllOptions());
    assertEquals(List.of("option1"), updatedFields.get(0).getSelectedOptions());
    assertEquals(
        List.of("Invitrogen", "NEB", "Amersham", "Sigma"), updatedFields.get(1).getAllOptions());
    assertEquals(List.of("Sigma"), updatedFields.get(1).getSelectedOptions());
    // old choice fields
    assertEquals(List.of("optionA", "optionB"), updatedFields.get(2).getAllOptions());
    assertEquals(List.of("optionA"), updatedFields.get(2).getSelectedOptions());
    assertEquals(List.of("optionA", "optionB", "and C"), updatedFields.get(3).getAllOptions());
    assertEquals(List.of("optionB", "and C"), updatedFields.get(3).getSelectedOptions());
    assertEquals(List.of("4", "6", "8", "other"), updatedFields.get(4).getAllOptions());
    assertEquals(List.of("4", "6"), updatedFields.get(4).getSelectedOptions());
  }

  /*
   * Creates a simplified sample template with choice/radio fields that use old format
   * for storing defined options and selected options.
   */
  private Sample createSampleWithOldFormatRadioChoiceFields(User user)
      throws IllegalAccessException {

    // create sample template
    Sample newSample = recordFactory.createSample("old-format options sample", user);
    newSample.setTemplate(true);
    // put subsample in workbench
    Container workbench = containerDao.getWorkbenchForUser(user);
    newSample.getSubSamples().get(0).moveToNewParent(workbench);

    // create radio fields with old format
    // format used in RSpace 1.70
    InventoryRadioFieldDef radioDef1 = new InventoryRadioFieldDef();
    FieldUtils.writeField(
        radioDef1, "radioOptions", "no-default-radio=option1&no-default-radio=option2", true);
    SampleField radioField1 = new InventoryRadioField(radioDef1, "radioField v1.70");
    radioField1.setData("option1");
    newSample.addSampleField(radioField1);
    // format used in RSpace 1.71
    InventoryRadioFieldDef radioDef2 = new InventoryRadioFieldDef();
    FieldUtils.writeField(radioDef2, "radioOptions", "v=Invitrogen&v=NEB&v=Amersham&v=Sigma", true);
    SampleField radioField2 = new InventoryRadioField(radioDef2, "radioField v1.71");
    radioField2.setData("Sigma");
    newSample.addSampleField(radioField2);

    // create choice field in old format
    // format used in RSpace 1.70
    InventoryChoiceFieldDef choiceDef1 = new InventoryChoiceFieldDef();
    FieldUtils.writeField(
        choiceDef1, "choiceOptions", "choiceField=optionA&choiceField=optionB", true);
    SampleField choiceField1 = new InventoryChoiceField(choiceDef1, "choiceField v1.70 a");
    choiceField1.setData("fieldSelectedChoices=optionA");
    newSample.addSampleField(choiceField1);
    // format used in RSpace 1.70, raised in RSINV-470
    InventoryChoiceFieldDef choiceDef2 = new InventoryChoiceFieldDef();
    FieldUtils.writeField(
        choiceDef2,
        "choiceOptions",
        "choice & Field =optionA&choice & Field =optionB&choice & Field =and C",
        true);
    SampleField choiceField2 = new InventoryChoiceField(choiceDef2, "choiceField v1.70 b");
    choiceField2.setData("fieldSelectedChoices=optionB&fieldSelectedChoices=and C");
    newSample.addSampleField(choiceField2);
    // format used in RSpace 1.71
    InventoryChoiceFieldDef choiceDef3 = new InventoryChoiceFieldDef();
    FieldUtils.writeField(choiceDef3, "choiceOptions", "v=4&v=6&v=8&v=other", true);
    SampleField choiceField3 = new InventoryChoiceField(choiceDef3, "choiceField v1.71");
    choiceField3.setData("v=4&v=6");
    newSample.addSampleField(choiceField3);

    return newSample;
  }
}
