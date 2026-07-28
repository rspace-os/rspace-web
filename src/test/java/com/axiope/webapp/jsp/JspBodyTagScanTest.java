package com.axiope.webapp.jsp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * SiteMesh 3 parses view output strictly: everything destined for the decorator's body slot must
 * sit inside a single well-formed {@code <body>} element. A duplicated or unpaired tag is easy to
 * introduce when two branches add the same structural fix and are then merged, and the result is
 * silently broken page content rather than an error. This scan fails the build instead.
 *
 * <p>Tags are counted case-insensitively and comments (HTML and JSP) are stripped first, so a
 * commented-out body tag neither trips nor satisfies the check. Fragment JSPs with no body element
 * at all are fine; what fails is more than one body element, or an open without its close (and vice
 * versa) in the same file.
 */
class JspBodyTagScanTest {

  private static final Path PAGES_DIR = Path.of("src", "main", "webapp", "WEB-INF", "pages");
  private static final Pattern OPEN_BODY = Pattern.compile("<body[\\s>]", Pattern.CASE_INSENSITIVE);
  private static final Pattern CLOSE_BODY =
      Pattern.compile("</body\\s*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMMENTS = Pattern.compile("<!--.*?-->|<%--.*?--%>", Pattern.DOTALL);

  @Test
  void everyJspHasAtMostOneWellFormedBodyElement() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(PAGES_DIR)) {
      files
          .filter(p -> p.toString().endsWith(".jsp"))
          .forEach(
              jsp -> {
                String content = COMMENTS.matcher(read(jsp)).replaceAll("");
                int opens = count(OPEN_BODY, content);
                int closes = count(CLOSE_BODY, content);
                if (opens > 1 || closes > 1 || opens != closes) {
                  offenders.add(jsp + " (open=" + opens + ", close=" + closes + ")");
                }
              });
    }
    assertTrue(
        offenders.isEmpty(),
        "JSPs with duplicated or unpaired <body>/</body> tags (breaks SiteMesh 3 body extraction): "
            + offenders);
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new RuntimeException("Could not read " + file, e);
    }
  }

  private static int count(Pattern pattern, String content) {
    Matcher matcher = pattern.matcher(content);
    int n = 0;
    while (matcher.find()) {
      n++;
    }
    return n;
  }
}
