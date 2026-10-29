package com.researchspace.linkedelements;

public class TagFilter {
  static final String regex =
      "</?(a|address|applet|area|b|base|basefont|big|blockquote|body|br|caption|center|cite|code|dd|dfn|dir|div|dl|dt|em|font|form|frame|frameset|h1|h2|h3|h4|h5|h6|head|hr|html|i|img|input|isindex|kbd|li|link|map|menu|meta|nobr|noframes|object|ol|option|p|param|pre|samp|script|select|small|span|strike|s|strong|style|sub|sup|table|td|textarea|th|title|tr|tt|u|ul|var)("
          + " [^>]*+)?/?>";

  public static String removeAllHTMLTags(String input) {
    // a tag ends at a '>', so nothing after the last one can match; limiting the search to the
    // text before it means every attribute scan ends in a match, keeping the cost linear
    int end = input.lastIndexOf('>') + 1;
    return input.substring(0, end).replaceAll(regex, "") + input.substring(end);
  }
}
