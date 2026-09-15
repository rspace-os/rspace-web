package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.record.TestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DateFieldTest {

  DateField nf;

  @BeforeEach
  public void setUp() throws Exception {
    nf = new DateField(TestFactory.createDateFieldForm());

    nf.setFieldData("1976-05-05");

    nf.setId(5L);
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testShallowCopy() {
    DateField copy = nf.shallowCopy();
    assertNull(copy.getId());
    assertEquals(nf.getFieldData(), copy.getFieldData());
    assertEquals(nf.getDefaultDate(), copy.getDefaultDate());
    assertEquals(nf.getMaxValue(), copy.getMaxValue());
    assertEquals(nf.getMinValue(), copy.getMinValue());
  }
}
