package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.DevOpsManager;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.util.PatternMatchUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Pins the one transaction rule the Inventory operations depend on, which nothing else tests: a
 * {@link org.springframework.validation.BindException} is checked, so Spring's default rules COMMIT
 * on it. An operation that rejects a request after marking its transaction rollback-only (an
 * unresolvable documentation target does exactly that) then answers 500 in place of the 400.
 *
 * <p>The rule lives in XML and binds by method NAME, so renaming {@link
 * InventoryOperationManager#performOperation} would silently drop it with no test going red. This
 * test matches the declared patterns the way {@code NameMatchTransactionAttributeSource} does,
 * against the interface's real methods, so a rename fails here.
 *
 * <p>It also pins the read-only rules for {@link DevOpsManager}'s annotated-image report, which
 * must not flush the document state Hibernate changes on load (RSDEV-1837).
 */
class InventoryOperationTransactionRuleTest {

  private static final Path PRODUCTION =
      Path.of("src/main/resources/applicationContext-service.xml");
  private static final Path TEST_MIRROR =
      Path.of("src/test/resources/applicationContext-test-service.xml");

  private static final String BIND_EXCEPTION = "org.springframework.validation.BindException";

  private static Map<String, String> txAdviceAttribute(Path context, String attribute)
      throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document document = factory.newDocumentBuilder().parse(context.toFile());
    NodeList advices = document.getElementsByTagNameNS("*", "advice");
    Map<String, String> values = new LinkedHashMap<>();
    for (int i = 0; i < advices.getLength(); i++) {
      Element advice = (Element) advices.item(i);
      if (!"txAdvice".equals(advice.getAttribute("id"))) {
        continue;
      }
      NodeList methods = advice.getElementsByTagNameNS("*", "method");
      for (int j = 0; j < methods.getLength(); j++) {
        Element method = (Element) methods.item(j);
        values.put(method.getAttribute("name"), method.getAttribute(attribute));
      }
      return values;
    }
    return values;
  }

  private static Map<String, String> txAdviceRollbackRules(Path context)
      throws IOException, SAXException, ParserConfigurationException {
    return txAdviceAttribute(context, "rollback-for");
  }

  private static Map<String, String> txAdviceTimeouts(Path context)
      throws IOException, SAXException, ParserConfigurationException {
    return txAdviceAttribute(context, "timeout");
  }

  private static Map<String, String> txAdviceReadOnly(Path context)
      throws IOException, SAXException, ParserConfigurationException {
    return txAdviceAttribute(context, "read-only");
  }

  private static String bestMatch(Map<String, String> rules, String methodName) {
    if (rules.containsKey(methodName)) {
      return methodName;
    }
    String best = null;
    for (String pattern : rules.keySet()) {
      if (PatternMatchUtils.simpleMatch(pattern, methodName)
          && (best == null || pattern.length() > best.length())) {
        best = pattern;
      }
    }
    return best;
  }

  @Test
  void everyManagerEntryPointRollsBackForARejection() throws Exception {
    Map<String, String> rules = txAdviceRollbackRules(PRODUCTION);
    assertTrue(rules.size() > 1, "txAdvice declares no method rules: " + PRODUCTION);

    List<String> unprotected = new ArrayList<>();
    for (Method method : InventoryOperationManager.class.getDeclaredMethods()) {
      String pattern = bestMatch(rules, method.getName());
      assertNotNull(pattern, "no tx:method matches " + method.getName());
      if (!BIND_EXCEPTION.equals(rules.get(pattern))) {
        unprotected.add(method.getName() + " -> tx:method name=\"" + pattern + "\"");
      }
    }
    assertTrue(
        unprotected.isEmpty(),
        "InventoryOperationManager methods whose tx:method does not declare rollback-for "
            + BIND_EXCEPTION
            + ", so a rejection would COMMIT: "
            + unprotected);
  }

  @Test
  void noTransactionRuleDeclaresATimeout() throws Exception {
    List<String> bounded =
        txAdviceTimeouts(PRODUCTION).entrySet().stream()
            .filter(rule -> !rule.getValue().isBlank())
            .map(Map.Entry::getKey)
            .toList();
    assertTrue(bounded.isEmpty(), "txAdvice is shared by every *Manager advisor: " + bounded);
  }

  @Test
  void theTestContextMirrorsTheProductionTransactionRules() throws Exception {
    assertEquals(
        txAdviceRollbackRules(PRODUCTION),
        txAdviceRollbackRules(TEST_MIRROR),
        "the test context's txAdvice has drifted from production, so every Spring test would be"
            + " asserting rules the application does not have");
    assertEquals(
        txAdviceTimeouts(PRODUCTION),
        txAdviceTimeouts(TEST_MIRROR),
        "the test context's txAdvice timeouts have drifted from production");
  }

  @Test
  void devOpsAnnotatedImageReportRunsReadOnlyInProductionAndTests() throws Exception {
    List<String> devOpsMethods =
        Arrays.stream(DevOpsManager.class.getDeclaredMethods()).map(Method::getName).toList();
    for (Path context : List.of(PRODUCTION, TEST_MIRROR)) {
      Map<String, String> readOnly = txAdviceReadOnly(context);
      for (String methodName :
          List.of("findFieldIdsWithAnnotatedImages", "reportAnnotatedImagesInField")) {
        // a renamed interface method would silently lose its rule
        assertTrue(devOpsMethods.contains(methodName), "DevOpsManager has no " + methodName);
        String pattern = bestMatch(readOnly, methodName);
        assertEquals(
            "true",
            readOnly.get(pattern),
            methodName + " is not read-only in " + context + " (tx:method \"" + pattern + "\")");
      }
      assertNotEquals(
          "true",
          readOnly.get(bestMatch(readOnly, "relinkAnnotatedImagesInField")),
          "the repair must run in a writable transaction: " + context);
    }
  }
}
