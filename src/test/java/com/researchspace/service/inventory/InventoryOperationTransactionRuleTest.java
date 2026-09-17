package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * InventoryOperationManager#performBiobankOperation} would silently drop it with no test going red.
 * This test matches the declared patterns the way {@code NameMatchTransactionAttributeSource} does,
 * against the interface's real methods, so a rename fails here.
 */
class InventoryOperationTransactionRuleTest {

  private static final Path PRODUCTION =
      Path.of("src/main/resources/applicationContext-service.xml");
  private static final Path TEST_MIRROR =
      Path.of("src/test/resources/applicationContext-test-service.xml");

  private static final String BIND_EXCEPTION = "org.springframework.validation.BindException";

  /** Each {@code tx:method} of the shared {@code txAdvice}, in declaration order. */
  private static Map<String, String> txAdviceRollbackRules(Path context)
      throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document document = factory.newDocumentBuilder().parse(context.toFile());
    NodeList advices = document.getElementsByTagNameNS("*", "advice");
    Map<String, String> rules = new LinkedHashMap<>();
    for (int i = 0; i < advices.getLength(); i++) {
      Element advice = (Element) advices.item(i);
      if (!"txAdvice".equals(advice.getAttribute("id"))) {
        continue;
      }
      NodeList methods = advice.getElementsByTagNameNS("*", "method");
      for (int j = 0; j < methods.getLength(); j++) {
        Element method = (Element) methods.item(j);
        rules.put(method.getAttribute("name"), method.getAttribute("rollback-for"));
      }
      return rules;
    }
    return rules;
  }

  /** Each {@code tx:method} of the shared {@code txAdvice} with its declared timeout, if any. */
  private static Map<String, String> txAdviceTimeouts(Path context)
      throws IOException, SAXException, ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document document = factory.newDocumentBuilder().parse(context.toFile());
    NodeList advices = document.getElementsByTagNameNS("*", "advice");
    Map<String, String> timeouts = new LinkedHashMap<>();
    for (int i = 0; i < advices.getLength(); i++) {
      Element advice = (Element) advices.item(i);
      if (!"txAdvice".equals(advice.getAttribute("id"))) {
        continue;
      }
      NodeList methods = advice.getElementsByTagNameNS("*", "method");
      for (int j = 0; j < methods.getLength(); j++) {
        Element method = (Element) methods.item(j);
        timeouts.put(method.getAttribute("name"), method.getAttribute("timeout"));
      }
      return timeouts;
    }
    return timeouts;
  }

  /** The pattern Spring would apply: an exact name wins, otherwise the longest match does. */
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

  /**
   * No {@code tx:method} may declare a timeout. txAdvice is shared by every {@code *Manager}
   * advisor, so one here would cap unrelated manager calls; and the in-flight claim is released by
   * its owner rather than on age, so nothing depends on an operation being bounded (RSDEV-1231).
   */
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
}
