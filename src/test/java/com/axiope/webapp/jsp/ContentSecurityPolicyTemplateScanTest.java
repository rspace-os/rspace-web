package com.axiope.webapp.jsp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards the authored template constraints required by the nonce-based CSP. */
class ContentSecurityPolicyTemplateScanTest {

  private static final Path WEBAPP = Path.of("src", "main", "webapp");
  private static final Path TINYMCE_DOCUMENTS =
      WEBAPP.resolve(Path.of("scripts", "externalTinymcePlugins"));
  private static final Path EGNYTE_DIRECTORY = TINYMCE_DOCUMENTS.resolve("egnyte");
  private static final Pattern COMMENTS =
      Pattern.compile("<!--.*?-->|<%--.*?--%>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern SCRIPT_START =
      Pattern.compile("<script\\b([^>]*)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern SCRIPT_BLOCK =
      Pattern.compile(
          "<script\\b[^>]*>.*?</script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern STYLE_START =
      Pattern.compile("<style\\b([^>]*)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern STYLE_BLOCK =
      Pattern.compile("<style\\b[^>]*>.*?</style\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern NONCE_ATTRIBUTE =
      Pattern.compile(
          "\\bnonce\\s*=\\s*([\"'])\\$\\{requestScope\\.cspNonce}\\1", Pattern.CASE_INSENSITIVE);
  private static final Pattern SRC_ATTRIBUTE =
      Pattern.compile("\\bsrc\\s*=", Pattern.CASE_INSENSITIVE);
  private static final Pattern TYPE_ATTRIBUTE =
      Pattern.compile("\\btype\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern INLINE_EVENT_ATTRIBUTE =
      Pattern.compile("<[^>]*\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  @Test
  void renderedJspScriptsAndStylesUseTheRequestNonce() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(WEBAPP)) {
      files
          .filter(ContentSecurityPolicyTemplateScanTest::isRenderedJspOrTag)
          .forEach(
              file -> {
                String content = withoutComments(read(file));
                Matcher scripts = SCRIPT_START.matcher(content);
                while (scripts.find()) {
                  String attributes = scripts.group(1);
                  if (isExecutableScript(attributes)
                      && !NONCE_ATTRIBUTE.matcher(attributes).find()) {
                    offenders.add(
                        location(file, content, scripts.start()) + " executable <script>");
                  }
                }

                Matcher styles = STYLE_START.matcher(content);
                while (styles.find()) {
                  if (!NONCE_ATTRIBUTE.matcher(styles.group(1)).find()) {
                    offenders.add(location(file, content, styles.start()) + " <style>");
                  }
                }

                addInlineEventOffenders(file, content, offenders);
              });
    }

    assertThat(offenders).as("Rendered JSP/tag CSP violations: " + offenders).isEmpty();
  }

  @Test
  void staticTinyMceDocumentsContainNoInlineExecutableCode() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(TINYMCE_DOCUMENTS)) {
      files
          .filter(ContentSecurityPolicyTemplateScanTest::isHtmlDocument)
          .forEach(
              file -> {
                String content = withoutComments(read(file));
                Matcher scripts = SCRIPT_START.matcher(content);
                while (scripts.find()) {
                  String attributes = scripts.group(1);
                  if (isExecutableScript(attributes) && !SRC_ATTRIBUTE.matcher(attributes).find()) {
                    offenders.add(location(file, content, scripts.start()) + " inline <script>");
                  }
                }
                Matcher styles = STYLE_START.matcher(content);
                while (styles.find()) {
                  offenders.add(location(file, content, styles.start()) + " inline <style>");
                }
                addInlineEventOffenders(file, content, offenders);
              });
    }

    assertThat(offenders).as("Static TinyMCE document CSP violations: " + offenders).isEmpty();
  }

  @Test
  void javaScriptEscaperIsDeclaredInEachTranslationUnit() throws IOException {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(WEBAPP)) {
      files
          .filter(ContentSecurityPolicyTemplateScanTest::isRenderedJspOrTag)
          .forEach(
              file -> {
                String content = read(file);
                if (content.contains("f:escapeJavaScript") && !declaresJavaScriptEscaper(content)) {
                  offenders.add(file.toString());
                }
              });
    }

    assertThat(offenders)
        .as("Templates missing the JavaScript escaper taglib: " + offenders)
        .isEmpty();
  }

  @Test
  void egnytePickerLoadsItsBundleAndGeneratedStylesWithoutDynamicExecution() {
    String dialog = read(EGNYTE_DIRECTORY.resolve("dialog.html"));
    String dialogScript = read(EGNYTE_DIRECTORY.resolve("script.js"));
    String pickerScript = read(EGNYTE_DIRECTORY.resolve("egnyte.js"));
    String pickerStyles = read(EGNYTE_DIRECTORY.resolve("egnyte-picker.css"));

    assertThat(dialog)
        .contains("/scripts/externalTinymcePlugins/egnyte/egnyte.js")
        .contains("/scripts/externalTinymcePlugins/egnyte/egnyte-picker.css");
    assertThat(dialogScript).doesNotContain("$.getScript(");
    assertThat(pickerScript).doesNotContain("document.createElement('style')");
    assertThat(pickerStyles).contains(".eg-picker-back", ".eg-picker-item");
  }

  private static boolean isRenderedJspOrTag(Path file) {
    String name = file.getFileName().toString();
    return name.endsWith(".jsp") || name.endsWith(".tag");
  }

  private static boolean isHtmlDocument(Path file) {
    String name = file.getFileName().toString();
    return name.endsWith(".html") || name.endsWith(".htm");
  }

  private static boolean declaresJavaScriptEscaper(String content) {
    return content.contains("include file=\"/common/taglibs.jsp\"")
        || (content.contains("uri=\"http://researchspace.com/functions\"")
            && content.contains("prefix=\"f\""));
  }

  private static boolean isExecutableScript(String attributes) {
    Matcher type = TYPE_ATTRIBUTE.matcher(attributes);
    if (!type.find()) {
      return true;
    }
    String value = type.group(2).trim().toLowerCase(Locale.ROOT);
    return value.equals("module")
        || value.equals("text/javascript")
        || value.equals("application/javascript")
        || value.equals("text/ecmascript")
        || value.equals("application/ecmascript");
  }

  private static void addInlineEventOffenders(Path file, String content, List<String> offenders) {
    String markupWithoutCode =
        STYLE_BLOCK.matcher(SCRIPT_BLOCK.matcher(content).replaceAll("")).replaceAll("");
    Matcher inlineEvents = INLINE_EVENT_ATTRIBUTE.matcher(markupWithoutCode);
    while (inlineEvents.find()) {
      offenders.add(location(file, markupWithoutCode, inlineEvents.start()) + " inline event");
    }
  }

  private static String withoutComments(String content) {
    return COMMENTS.matcher(content).replaceAll("");
  }

  private static String location(Path file, String content, int offset) {
    long line =
        1 + content.substring(0, offset).chars().filter(character -> character == '\n').count();
    return file + ":" + line;
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new RuntimeException("Could not read " + file, e);
    }
  }
}
