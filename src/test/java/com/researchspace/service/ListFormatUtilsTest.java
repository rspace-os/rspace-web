package com.researchspace.service;

import static org.junit.Assert.assertEquals;

import com.ibm.icu.text.ListFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class ListFormatUtilsTest {

  @Test
  public void formatsConjunctionByDefault() {
    assertEquals("A", ListFormatUtils.formatList(List.of("A"), Locale.US));
    assertEquals("A and B", ListFormatUtils.formatList(List.of("A", "B"), Locale.US));
    assertEquals("A, B, and C", ListFormatUtils.formatList(List.of("A", "B", "C"), Locale.US));
  }

  @Test
  public void formatsDisjunctionWhenRequested() {
    assertEquals(
        "A, B, or C",
        ListFormatUtils.formatList(List.of("A", "B", "C"), Locale.US, ListFormatter.Type.OR));
  }
}
