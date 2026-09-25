package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.testutil.ModelTestUtils;
import com.researchspace.core.util.TransformerUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RadioFieldFormTest {
  private RadioFieldForm rft;

  @BeforeEach
  public void setUp() throws Exception {
    rft = new RadioFieldForm();
    rft.setRadioOption("a=b&a=c&a=d");
    rft.setDefaultRadioOption("b");
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testGetSummaryDoesNotThrowNPE() {

    System.err.println(rft.getSummary());
  }

  @Test
  public void testValidateData() {
    assertFalse(rft.validate("").hasErrorMessages());
    assertFalse(rft.validate("d").hasErrorMessages());
    assertTrue(rft.validate("x").hasErrorMessages());
  }

  @Test
  public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {

    RadioFieldForm copy = rft.shallowCopy();

    // use reflection help class to ensure fields are equals
    List<Class<? super RadioFieldForm>> classesToConsider = new ArrayList<>();
    classesToConsider.add(RadioFieldForm.class);
    classesToConsider.add(FieldForm.class);
    Set<String> toExclude = TransformerUtils.toSet("radioBehaviour");
    ModelTestUtils.assertCopiedFieldsAreEqual(copy, rft, toExclude, classesToConsider);
  }
}
