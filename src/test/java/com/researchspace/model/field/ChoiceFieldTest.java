package com.researchspace.model.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChoiceFieldTest {

  ChoiceField nf;

  @BeforeEach
  public void setUp() throws Exception {
    ChoiceFieldForm cft = new ChoiceFieldForm();

    cft.setMultipleChoice(true);
    cft.setDefaultChoiceOption("x=2");
    cft.setChoiceOptions("x=2&x=3");
    nf = new ChoiceField(cft);
    nf.setColumnIndex(0);
    nf.setName("Name");
    nf.setFieldData("x=2");
    nf.setId(5L);
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testShallowCopy() {
    ChoiceField copy = nf.shallowCopy();
    assertNull(copy.getId());
    assertEquals(nf.getFieldData(), copy.getFieldData());
  }

  @Test
  public void getRadioOptionAsListTest() {
    assertEquals("2", nf.getChoiceOptionSelectedAsString());
    assertThat(nf.getChoiceOptionAsList()).hasSize(2);
  }

  @Test
  public void singleFieldShowsCheckboxesOnlyWithSeveralStoredValues() {
    ChoiceFieldForm single = new ChoiceFieldForm();
    single.setChoiceOptions("x=2&x=3");
    ChoiceField field = new ChoiceField(single);
    field.setFieldData("x=2");
    assertFalse(field.isShownAsCheckboxes());
    field.setFieldData("x=2&x=3");
    assertTrue(field.isShownAsCheckboxes());
    assertTrue(nf.isShownAsCheckboxes());
  }
}
