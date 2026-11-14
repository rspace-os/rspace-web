package com.researchspace.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiV2FeatureIsolationTest {
  // Comments and character literals are consumed separately from identifiers and strings.
  private static final Pattern TOKEN =
      Pattern.compile(
          "(?<comment>//[^\\n"
              + "]*|/\\*.*?\\*/)|(?<string>\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\")"
              + "|(?<character>'(?:\\\\.|[^'\\\\])*')|(?<identifier>[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)",
          Pattern.DOTALL);

  @Test
  void sharedTransportNeverNamesAFeature() throws Exception {
    var failures = new ArrayList<String>();
    try (Stream<Path> sources = Files.walk(Path.of("src/main/java/com/researchspace/api/v2"))) {
      for (Path source :
          sources.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
        failures.addAll(violations(source, Files.readString(source)));
      }
    }
    Path webConfig = Path.of("src/main/java/com/researchspace/webapp/config/WebConfig.java");
    failures.addAll(violations(webConfig, Files.readString(webConfig)));
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  @Test
  void reportsIdentifiersImportsAndStringsWithTheirLinesButIgnoresComments() {
    assertEquals(
        List.of(
            "Example.java:2: booking",
            "Example.java:3: BookingThing",
            "Example.java:4: \"BOOKING\""),
        violations(
            Path.of("Example.java"),
            "// booking\n"
                + "import com.researchspace.booking.X;\n"
                + "BookingThing thing;\n"
                + "String s = \"BOOKING\"; /* booking */"));
  }

  private static List<String> violations(Path source, String text) {
    var failures = new ArrayList<String>();
    var tokens = TOKEN.matcher(text);
    while (tokens.find()) {
      if (tokens.group("comment") == null
          && tokens.group("character") == null
          && tokens.group().toLowerCase(Locale.ROOT).contains("booking")) {
        long line = text.substring(0, tokens.start()).chars().filter(c -> c == '\n').count() + 1;
        failures.add(source + ":" + line + ": " + tokens.group());
      }
    }
    return failures;
  }
}
