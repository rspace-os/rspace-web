package com.axiope.webapp.jsp;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EscapeXMLTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void test() {
    assertEquals("Bob&#039;s document", EscapeXml.escape("Bob's document"));

    assertEquals(
        "&lt;script&gt;alert(1)&lt;/script&gt;", EscapeXml.escape("<script>alert(1)</script>"));
  }
}
