package com.researchspace.service;

import com.ibm.icu.text.ListFormatter;
import java.util.Collection;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

/** Formats items as a locale-aware natural-language list, such as "A, B, and C". */
public final class ListFormatUtils {

  private ListFormatUtils() {}

  public static String formatList(Collection<String> items) {
    return formatList(items, LocaleContextHolder.getLocale(), ListFormatter.Type.AND);
  }

  public static String formatList(Collection<String> items, ListFormatter.Type type) {
    return formatList(items, LocaleContextHolder.getLocale(), type);
  }

  public static String formatList(Collection<String> items, Locale locale) {
    return formatList(items, locale, ListFormatter.Type.AND);
  }

  public static String formatList(
      Collection<String> items, Locale locale, ListFormatter.Type type) {
    return ListFormatter.getInstance(locale, type, ListFormatter.Width.WIDE).format(items);
  }
}
