package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NumberFieldTest {

  NumberField nf;

  @BeforeEach
  public void setUp() throws Exception {
    NumberFieldForm nft = FieldTestUtils.createANumberFieldForm();
    nf = new NumberField(nft);

    nf.setId(5L);
    nf.setFieldData("122");
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testShallowCopy() {
    NumberField copy = nf.shallowCopy();
    assertNull(copy.getId());
    assertEquals(copy.getMinNumberValue(), nf.getMinNumberValue());
    assertEquals(copy.getMaxNumberValue(), nf.getMaxNumberValue());
    assertEquals(copy.getDefaultNumberValue(), nf.getDefaultNumberValue());
    assertEquals(copy.getDecimalPlaces(), nf.getDecimalPlaces());
  }
}
