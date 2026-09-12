package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Every transaction that takes a row lock after a plain read must run at READ COMMITTED
 * (RSDEV-1231). Under REPEATABLE READ the first plain read fixes the transaction's snapshot; with
 * MariaDB's {@code innodb_snapshot_isolation} (on by default from 11.6) a later {@code FOR UPDATE}
 * on a row another transaction committed since then fails with error 1020 instead of waiting, so
 * the second of two concurrent stock writers got a 409 rather than the serialised outcome the lock
 * order exists to give. At READ COMMITTED the read view closes after every statement, so a locking
 * read that waited sees the committed row. Both the production and the test service contexts
 * declare the advice, so both are pinned here.
 */
public class StockWriterTransactionIsolationTest {

  private static final String TX_NS = "http://www.springframework.org/schema/tx";
  private static final String AOP_NS = "http://www.springframework.org/schema/aop";

  private static final Path MAIN_SOURCES = Path.of("src/main/java");

  private static final Set<String> READ_COMMITTED_METHODS =
      Set.of(
          "performOperation",
          "deductStock",
          "createNewListOfMaterials",
          "updateListOfMaterials",
          "mergeUiJsonSetting");

  @ParameterizedTest
  @ValueSource(strings = {"applicationContext-service.xml", "applicationContext-test-service.xml"})
  void stockWritersAndThePreferenceMergeRunAtReadCommitted(String contextFile) throws Exception {
    Map<String, String> isolationByMethod = txAdviceAttributeByMethodName(contextFile, "isolation");
    for (String method : READ_COMMITTED_METHODS) {
      assertEquals(
          "READ_COMMITTED",
          isolationByMethod.get(method),
          contextFile + " should run " + method + " at READ_COMMITTED");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"applicationContext-service.xml", "applicationContext-test-service.xml"})
  void performOperationRollsBackForBindException(String contextFile) throws Exception {
    // A live-state rejection is a checked BindException, which Spring's default rules COMMIT on;
    // the live-state pass has already written each parent's recomputed total through
    // lockSiblingRowsAndRecalculateTotal by then, so without this rule those writes would be
    // committed
    // alongside the 400 (Copilot review, PR #1090).
    assertEquals(
        "org.springframework.validation.BindException",
        txAdviceAttributeByMethodName(contextFile, "rollback-for").get("performOperation"),
        contextFile + " should roll performOperation back for BindException");
  }

  /**
   * No two advised types may declare a method that {@code txAdvice} singles out by name.
   *
   * <p>{@code <tx:advice>} builds a {@code NameMatchTransactionAttributeSource}, which matches on
   * METHOD NAME ONLY: no class, no package, no signature. That one advice is referenced by seven
   * advisors, so each specially-declared name applies to every type any of those pointcuts reaches.
   * A future {@code DocumentTagManager.performOperation(...)}, nothing to do with inventory stock,
   * would silently run at READ_COMMITTED and roll back for {@code BindException}: no error, no
   * warning, no log line, and the only symptom is different behaviour under concurrency or work
   * disappearing on a validation path.
   *
   * <p>The other tests in this class read the DECLARATION and so cannot see this. Adding a sixth
   * {@code performOperation} somewhere leaves the XML byte-identical and leaves them green
   * (parallel review, L2-A).
   *
   * <p>KNOWN LIMITATION: a static approximation of Spring's AOP matching. It reads source files
   * rather than asking the container which beans an advisor actually advises, judges a type advised
   * by its package and name alone, and cannot see a collision introduced by a type outside {@code
   * src/main/java}. That is the trade-off the XML-parsing tests above already make, and it catches
   * the realistic case.
   */
  @Test
  void noTwoAdvisedTypesDeclareASpeciallyAdvisedMethodName() throws Exception {
    Set<String> advisedNames = speciallyAdvisedMethodNames("applicationContext-service.xml");
    assertFalse(
        advisedNames.isEmpty(), "no specially-advised tx:method names parsed from the service XML");

    List<Pattern> pointcuts = advisedTypePatterns("applicationContext-service.xml");
    assertFalse(pointcuts.isEmpty(), "no txAdvice advisor pointcuts parsed from the service XML");

    Map<String, Set<String>> declaringTypesByName = new TreeMap<>();
    for (String name : advisedNames) {
      declaringTypesByName.put(name, new LinkedHashSet<>());
    }
    for (Path source : javaSourcesUnder(MAIN_SOURCES)) {
      String fqn = fullyQualifiedName(source);
      if (pointcuts.stream().noneMatch(p -> p.matcher(fqn).matches())) {
        continue;
      }
      String body = stripComments(Files.readString(source));
      for (String name : advisedNames) {
        if (declaresMethod(body, name)) {
          declaringTypesByName.get(name).add(fqn);
        }
      }
    }

    for (Map.Entry<String, Set<String>> entry : declaringTypesByName.entrySet()) {
      assertEquals(
          1,
          entry.getValue().size(),
          "exactly one advised type may declare '"
              + entry.getKey()
              + "', because txAdvice matches on method name alone and applies its attributes to"
              + " every type the seven advisors reach; found "
              + entry.getValue());
    }
  }

  /**
   * The {@code tx:method} entries that carry attributes beyond their name, read from the XML rather
   * than from {@link #READ_COMMITTED_METHODS}, so a sixth entry added later is covered without
   * touching this test. {@code name="*"} is the catch-all and carries no attributes.
   */
  private static Set<String> speciallyAdvisedMethodNames(String contextFile) throws Exception {
    Set<String> names = new LinkedHashSet<>();
    for (Element method : txAdviceMethodElements(contextFile)) {
      String name = method.getAttribute("name");
      if ("*".equals(name) || name.isEmpty()) {
        continue;
      }
      if (method.getAttributes().getLength() > 1) {
        names.add(name);
      }
    }
    return names;
  }

  /**
   * One regex per advisor that references {@code txAdvice}, matching the fully-qualified names its
   * pointcut reaches. AspectJ's {@code *} does not cross a {@code .} and {@code ..} crosses any
   * number of package segments, so {@code *..service.*Manager} becomes {@code
   * ^[^.]*(?:\.[^.]+)*\.service\.[^.]*Manager$} and excludes {@code service.inventory.XManager}.
   * Derived from the XML so an eighth advisor is covered too.
   */
  private static List<Pattern> advisedTypePatterns(String contextFile) throws Exception {
    Pattern execution = Pattern.compile("execution\\(\\s*\\*\\s+(.+)\\.\\*\\(\\.\\.\\)\\s*\\)");
    List<Pattern> patterns = new ArrayList<>();
    Document document = parse(contextFile);
    NodeList advisors = document.getElementsByTagNameNS(AOP_NS, "advisor");
    for (int i = 0; i < advisors.getLength(); i++) {
      Element advisor = (Element) advisors.item(i);
      if (!"txAdvice".equals(advisor.getAttribute("advice-ref"))) {
        continue;
      }
      Matcher matcher = execution.matcher(advisor.getAttribute("pointcut"));
      assertTrue(
          matcher.matches(),
          "unrecognised pointcut on advisor "
              + advisor.getAttribute("id")
              + ": "
              + advisor.getAttribute("pointcut"));
      patterns.add(Pattern.compile(typePatternToRegex(matcher.group(1))));
    }
    return patterns;
  }

  private static String typePatternToRegex(String typePattern) {
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < typePattern.length(); i++) {
      char c = typePattern.charAt(i);
      if (c == '.' && i + 1 < typePattern.length() && typePattern.charAt(i + 1) == '.') {
        regex.append("(?:\\.[^.]+)*\\.");
        i++;
      } else if (c == '.') {
        regex.append("\\.");
      } else if (c == '*') {
        regex.append("[^.]*");
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return regex.append("$").toString();
  }

  /**
   * Whether the source DECLARES a method of this name: a type token, whitespace, the name, an open
   * paren. Requiring whitespace before the name is what excludes every call site, since a call is
   * {@code something.name(} with no space, and comments are stripped first so prose mentioning the
   * name cannot match. A self-call like {@code return name(} does match, which is harmless: the
   * result is a set of TYPES, and a type can only self-call a method it declares.
   */
  private static boolean declaresMethod(String source, String methodName) {
    return Pattern.compile(
            "(?<![.\\w])[\\w.$<>\\[\\],?]+\\s+" + Pattern.quote(methodName) + "\\s*\\(")
        .matcher(source)
        .find();
  }

  private static String stripComments(String source) {
    return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
  }

  private static List<Path> javaSourcesUnder(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(p -> p.toString().endsWith(".java")).toList();
    }
  }

  private static String fullyQualifiedName(Path source) {
    String relative = MAIN_SOURCES.relativize(source).toString();
    return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
  }

  private static Map<String, String> txAdviceAttributeByMethodName(
      String contextFile, String attribute) throws Exception {
    Map<String, String> result = new HashMap<>();
    for (Element method : txAdviceMethodElements(contextFile)) {
      result.put(method.getAttribute("name"), method.getAttribute(attribute));
    }
    return result;
  }

  /** Every {@code <tx:method>} of the {@code txAdvice} advice, in declaration order. */
  private static List<Element> txAdviceMethodElements(String contextFile) throws Exception {
    List<Element> elements = new ArrayList<>();
    Document document = parse(contextFile);
    NodeList advices = document.getElementsByTagNameNS(TX_NS, "advice");
    for (int i = 0; i < advices.getLength(); i++) {
      Element advice = (Element) advices.item(i);
      if (!"txAdvice".equals(advice.getAttribute("id"))) {
        continue;
      }
      NodeList methods = advice.getElementsByTagNameNS(TX_NS, "method");
      for (int j = 0; j < methods.getLength(); j++) {
        elements.add((Element) methods.item(j));
      }
    }
    return elements;
  }

  private static Document parse(String contextFile) throws Exception {
    try (InputStream in =
        StockWriterTransactionIsolationTest.class
            .getClassLoader()
            .getResourceAsStream(contextFile)) {
      assertNotNull(in, contextFile + " must be on the test classpath");
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      return factory.newDocumentBuilder().parse(in);
    }
  }
}
