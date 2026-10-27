package com.researchspace.model.views;

import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses the "Name &lt;id&gt;, Other name &lt;id&gt;," strings produced by the group and community
 * autocomplete boxes without a regular expression, so the cost stays linear in the input length.
 */
@Slf4j
final class AutocompleteIdList {

  private AutocompleteIdList() {}

  /** Collects the ids of every well-formed "name&lt;id&gt;" item, ignoring anything else. */
  static Set<Long> parseIds(String input, boolean allowNegative) {
    Set<Long> ids = new TreeSet<>();
    if (input == null) {
      return ids;
    }
    // Keep unmatched pieces as part of the next name, including a leading <number>.
    String[] items = input.trim().split(">", -1);
    int itemStart = 0;
    for (int i = 0; i < items.length - 1; i++) {
      int open = items[i].lastIndexOf('<');
      if (open < 0) {
        continue;
      }
      String id = items[i].substring(open + 1);
      if (isId(id, allowNegative) && (i > itemStart || hasName(items[i], 0, open))) {
        ids.add(Long.parseLong(id));
        itemStart = i + 1;
      }
    }
    return ids;
  }

  /**
   * True when the whole input is one or more "name&lt;id&gt;" items, separated by optional commas
   * and whitespace, with at most one trailing comma.
   */
  static boolean isValid(String input, boolean allowNegative) {
    if (input == null) {
      return false;
    }
    String items = input.trim();
    if (items.endsWith(",")) {
      items = items.substring(0, items.length() - 1);
    }
    if (!items.endsWith(">")) {
      return false;
    }
    int itemStart = 0;
    int open = -1;
    for (int i = 0; i < items.length(); i++) {
      char current = items.charAt(i);
      if (current == '<') {
        open = i;
      } else if (current == '>' && open >= itemStart) {
        String id = items.substring(open + 1, i);
        if (isId(id, allowNegative) && hasName(items, itemStart, open)) {
          itemStart = i + 1;
        }
        open = -1;
      }
    }
    return itemStart == items.length();
  }

  private static boolean hasName(String input, int start, int end) {
    String name = input.substring(start, end);
    if (name.startsWith(",")) {
      name = name.substring(1);
    }
    // A leading <number> is part of the name, not an id with an empty name.
    return !name.isBlank();
  }

  private static boolean isId(String text, boolean allowNegative) {
    if (!text.matches(allowNegative ? "-?[0-9]+" : "[0-9]+")) {
      return false;
    }
    try {
      Long.parseLong(text);
      return true;
    } catch (NumberFormatException e) {
      log.warn("Autocomplete id is outside the supported numeric range");
      return false;
    }
  }
}
