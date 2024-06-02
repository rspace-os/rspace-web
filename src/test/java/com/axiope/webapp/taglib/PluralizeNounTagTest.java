package com.axiope.webapp.taglib;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PluralizeNounTagTest {
  PluralizeNounTag tag = new PluralizeNounTag();

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testDoStartTag() {
    tag.setCount(2);
    tag.setInput("blog");
    assertEquals("blogs", tag.getString());

    tag.setCount(1);

    assertEquals("blog", tag.getString());
  }
}
