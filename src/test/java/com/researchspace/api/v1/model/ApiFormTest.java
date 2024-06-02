package com.researchspace.api.v1.model;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ApiFormTest {

  @Test
  public void retrieveFormIdFromApiForm() {
    ApiForm form = new ApiForm();
    // sample templates ok
    form.setGlobalId("IT12345");
    form.setId(12345L);
    assertEquals(12345L, form.retrieveFormIdFromApiForm().longValue());
    // id mismatch
    form.setId(111L);
    assertIllegalArgumentException(() -> form.retrieveFormIdFromApiForm());
    // forms ok
    form.setGlobalId("FM789");
    form.setId(789L);
    assertEquals(789L, form.retrieveFormIdFromApiForm().longValue());

    // invalid type
    form.setId(222L);
    form.setGlobalId("SD222");
    assertIllegalArgumentException(() -> form.retrieveFormIdFromApiForm());
  }
}
