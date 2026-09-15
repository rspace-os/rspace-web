package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TextFieldTest {

  private static final String RTFDATA = "RTFDATA";
  static final String DEFAULT = "default";

  @BeforeEach
  public void setUp() throws Exception {
    tf = FieldTestUtils.createTextField();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  static TextField tf;

  @Test
  public void testShallowCopy() {
    TextField copy = tf.shallowCopy();
    assertEquals(DEFAULT, copy.getDefaultValue());
    assertEquals(RTFDATA, copy.getFieldData());
  }

  @Test
  public void testIsFieldForm() {
    assertTrue(tf.isTextField());
  }
}
