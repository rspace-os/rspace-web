package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StringFieldTest {

  StringField nf;

  @BeforeEach
  public void setUp() throws Exception {
    nf = new StringField(new StringFieldForm());
    nf.setColumnIndex(0);
    nf.setName("Name");
    nf.setFieldData("data");
    nf.setId(5L);
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testShallowCopy() {
    StringField copy = nf.shallowCopy();
    assertNull(copy.getId());
    assertEquals(nf.getFieldData(), copy.getFieldData());
  }
}
