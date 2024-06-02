package com.researchspace.testsandbox;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(classes = PropertyWithSpaces.class)
@TestPropertySource(
    properties = {
      "licenseserver.poll.cron:2/15\\ *\\ *\\ *\\ *\\ ?",
      "licenseserver.poll.cron2:2/15 * * * * ?"
    })
public class PropertyWithSpaces extends AbstractJUnit4SpringContextTests {

  @Autowired Environment env;
  final String expected = "2/15 * * * * ?";

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void spacesInPropertyValuesWorkWhetherEscapedOrNot() {
    assertEquals(expected, env.getProperty("licenseserver.poll.cron"));
    assertEquals(expected, env.getProperty("licenseserver.poll.cron2"));
  }
}
