package com.axiope.webapp.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class EscapeXMLTest {

  @Test
  public void test() {
    assertEquals("Bob&#039;s document", EscapeXml.escape("Bob's document"));

    assertEquals(
        "&lt;script&gt;alert(1)&lt;/script&gt;", EscapeXml.escape("<script>alert(1)</script>"));
  }
}
