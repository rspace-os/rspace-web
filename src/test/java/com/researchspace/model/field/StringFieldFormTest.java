package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.researchspace.core.testutil.ModelTestUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StringFieldFormTest {
  StringFieldForm sft;

  @BeforeEach
  public void setUp() throws Exception {
    sft = FieldTestUtils.createStringForm();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testValidate() {
    // no validatio non strings yet!
    assertFalse(sft.validate(null).hasErrorMessages());
    assertFalse(sft.validate("").hasErrorMessages());
    assertFalse(sft.validate("x").hasErrorMessages());
  }

  @Test
  public void testShallowCopy() throws IllegalArgumentException, IllegalAccessException {

    StringFieldForm copy = sft.shallowCopy();

    // use reflection help class to ensure fields are equals
    List<Class<? super StringFieldForm>> classesToConsider = new ArrayList<>();
    classesToConsider.add(StringFieldForm.class);
    classesToConsider.add(FieldForm.class);
    ModelTestUtils.assertCopiedFieldsAreEqual(copy, sft, Collections.EMPTY_SET, classesToConsider);
  }
}
