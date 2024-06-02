package com.researchspace.slack;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlackMarkupToHTMLFormatter {
  private static Pattern SLACK_USER_MENTION = Pattern.compile("<@([^<>]+)>");
  private static Pattern SLACK_CHANNEL_MENTION = Pattern.compile("<#[^<>|]+[|]([^<>]+)>");

  public static String format(String text, Map<String, SlackUser> userMap) {
    // User mentions
    Matcher matcher = SLACK_USER_MENTION.matcher(text);
    while (matcher.find()) {
      String userId = matcher.group(1);
      text = text.replace(matcher.group(), userMap.get(userId).getProfile().getRealName());
    }
    // Channel mentions
    matcher = SLACK_CHANNEL_MENTION.matcher(text);
    while (matcher.find()) {
      String channelName = matcher.group(1);
      text = text.replace(matcher.group(), '#' + channelName);
    }
    // Links
    text = text.replaceAll("<([^<>|]+)(?:[|]([^<>|]+))?>", "<a href=\"$1\">$2</a>");
    // Bold text
    text = text.replaceAll("[*]([^*]+)[*]", "<strong>$1</strong>");
    // Italic text
    text = text.replaceAll("_([^_]+)_", "<i>$1</i>");
    // Code
    text = text.replaceAll("`([^`]+)`", "<code>$1</code>");

    return text;
  }
}
