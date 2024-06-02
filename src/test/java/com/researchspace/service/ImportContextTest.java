package com.researchspace.service;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ImportContextTest {
  private ImportContext context = null;

  @Before
  public void setUp() throws Exception {
    context = new ImportContext();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testIgnoreUnpublishedForms() {
    assertTrue(context.ignoreUnpublishedForms());
    assertTrue(context.enableDirectTemplateCreationInTemplateFolder());
  }
}
