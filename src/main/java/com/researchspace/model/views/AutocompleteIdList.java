package com.researchspace.model.views;

import java.util.Set;
import java.util.TreeSet;

/**
 * Parses the "Name &lt;id&gt;, Other name &lt;id&gt;," strings produced by the group and community
 * autocomplete boxes without a regular expression, so the cost stays linear in the input length.
 */
final class AutocompleteIdList {

  private AutocompleteIdList() {}

  /** Collects the ids of every well-formed "name&lt;id&gt;" item, ignoring anything else. */
  static Set<Long> parseIds(String input, boolean allowNegative) {
    Set<Long> ids = new TreeSet<>();
    if (input == null) {
      return ids;
    }
    for (String item : input.trim().split(">")) {
      String id = idOf(item);
      if (isId(id, allowNegative)) {
        ids.add(Long.parseLong(id));
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
    boolean foundItem = false;
    for (int i = 0; i < items.length(); i++) {
      char current = items.charAt(i);
      if (current == '<') {
        open = i;
      } else if (current == '>' && open >= itemStart) {
        String id = items.substring(open + 1, i);
        if (isId(id, allowNegative)) {
          String name = items.substring(itemStart, open);
          if (name.startsWith(",")) {
            name = name.substring(1);
          }
          if (name.isBlank()) {
            return false;
          }
          itemStart = i + 1;
          open = -1;
          foundItem = true;
        } else {
          open = -1;
        }
      }
    }
    return foundItem && itemStart == items.length();
  }

  private static String idOf(String item) {
    int open = item.lastIndexOf('<');
    return open < 0 ? "" : item.substring(open + 1);
  }

  private static boolean isId(String text, boolean allowNegative) {
    int start = allowNegative && text.startsWith("-") ? 1 : 0;
    if (text.length() == start) {
      return false;
    }
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    try {
      Long.parseLong(text);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
