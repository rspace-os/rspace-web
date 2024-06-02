package com.researchspace.service;

import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultContextTest {

  private DefaultRecordContext context = null;

  @Before
  public void setUp() throws Exception {
    context = new DefaultRecordContext();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testIgnoreUnpublishedForms() {
    assertFalse(context.ignoreUnpublishedForms());
    assertFalse(context.enableDirectTemplateCreationInTemplateFolder());
  }
}
