package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TimeFieldTest {

  TimeField nf;

  @BeforeEach
  public void setUp() throws Exception {
    TimeFieldForm tft = new TimeFieldForm();
    tft.setDefaultTime(123L);
    tft.setMaxTime(345);
    tft.setMinTime(123);
    tft.setColumnIndex(0);

    nf = new TimeField(tft);

    nf.setName("Name");
    nf.setFieldData("data");

    nf.setId(5L);
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testShallowCopy() {
    TimeField copy = nf.shallowCopy();
    assertNull(copy.getId());
    assertEquals(nf.getFieldData(), copy.getFieldData());
    assertEquals(nf.getDefaultTime(), copy.getDefaultTime());
    assertEquals(nf.getMaxTime(), copy.getMaxTime());
    assertEquals(nf.getMinTime(), copy.getMinTime());
  }
}
