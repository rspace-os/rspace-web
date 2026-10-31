package com.researchspace.service.audit.search;

import com.researchspace.model.core.Person;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared audit line syntax; callers decide how to handle malformed lines and timestamps. */
public final class AuditLogLineFormat {
  private static final Pattern LINE =
      Pattern.compile(
          "^(.*?)- domain:(\\w+)\\s+action:(\\w+)\\s+\\[(.+)]\\s+"
              + "(["
              + Person.ALLOWED_USERNAME_CHARS
              + ">]+)\\((.+)\\)\\s*(?:description:\\[(.*)])?$");

  private AuditLogLineFormat() {}

  /** Returns the fields without interpreting the timestamp, invoking the policy on a mismatch. */
  public static Optional<Fields> parse(String line, Consumer<String> malformedLine) {
    Matcher matcher = LINE.matcher(line);
    if (!matcher.matches()) {
      malformedLine.accept(line);
      return Optional.empty();
    }
    String action = matcher.group(3);
    return Optional.of(
        new Fields(
            matcher.group(1),
            matcher.group(2),
            "COPY".equalsIgnoreCase(action) ? "DUPLICATE" : action,
            matcher.group(4),
            matcher.group(5),
            matcher.group(6),
            matcher.group(7)));
  }

  public record Fields(
      String timestamp,
      String domain,
      String action,
      String data,
      String username,
      String fullName,
      String description) {}
}
