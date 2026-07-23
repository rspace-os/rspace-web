package com.researchspace.service;

import static org.junit.Assert.assertEquals;

import com.ibm.icu.text.ListFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.springframework.context.i18n.LocaleContextHolder;

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

  @Test
  public void usesRequestLocale() {
    LocaleContextHolder.setLocale(Locale.FRENCH);
    try {
      assertEquals(
          "A, B ou C", ListFormatUtils.formatList(List.of("A", "B", "C"), ListFormatter.Type.OR));
    } finally {
      LocaleContextHolder.resetLocaleContext();
    }
  }
}
