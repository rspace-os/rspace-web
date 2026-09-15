package com.axiope.webapp.taglib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class PluralizeNounTagTest {
  PluralizeNounTag tag = new PluralizeNounTag();

  @Test
  public void testDoStartTag() {
    tag.setCount(2);
    tag.setInput("blog");
    assertEquals("blogs", tag.getString());

    tag.setCount(1);

    assertEquals("blog", tag.getString());
  }
}
