package com.researchspace.model.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.testutil.ModelTestUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChoiceFieldFormTest {
  private ChoiceFieldForm cft;

  @BeforeEach
  public void setUp() throws Exception {
    cft = new ChoiceFieldForm();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testGetDefaultChoiceOptionAsList() {
    assertThat(cft.getDefaultChoiceOptionAsList()).isEmpty();
    cft.setDefaultChoiceOption("");
    assertThat(cft.getDefaultChoiceOptionAsList()).isEmpty();
    cft.setDefaultChoiceOption("a=b");
    assertThat(cft.getDefaultChoiceOptionAsList()).hasSize(1);
  }

  @Test
  public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {
    populateFields();
    ChoiceFieldForm copy = cft.shallowCopy();

    // use reflection help class to ensure fields are equals
    List<Class<? super ChoiceFieldForm>> classesToConsider = new ArrayList<>();
    classesToConsider.add(ChoiceFieldForm.class);
    classesToConsider.add(FieldForm.class);
    ModelTestUtils.assertCopiedFieldsAreEqual(copy, cft, Collections.EMPTY_SET, classesToConsider);
  }

  public void populateFields() {
    cft.setChoiceOptions("a=b&a=d");
    cft.setDefaultChoiceOption("a=b");
    cft.setColumnIndex(5);
    cft.setName("cft");
    cft.setMultipleChoice(true);
  }

  @Test
  public void testGetSummaryDoesNotThrowNPE() {

    System.err.println(cft.getSummary());
  }

  @Test
  public void testValidateData() {
    assertFalse(cft.validate("").hasErrorMessages());
    assertFalse(cft.validate("a=b").hasErrorMessages());
    assertFalse(cft.validate("a=b&c=d").hasErrorMessages());
    assertTrue(cft.validate("x").hasErrorMessages());
  }
}
