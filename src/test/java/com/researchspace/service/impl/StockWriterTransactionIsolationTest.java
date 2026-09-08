package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
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
    // recalculateTotalFromLockedRows by then, so without this rule those writes would be committed
    // alongside the 400 (Copilot review, PR #1090).
    assertEquals(
        "org.springframework.validation.BindException",
        txAdviceAttributeByMethodName(contextFile, "rollback-for").get("performOperation"),
        contextFile + " should roll performOperation back for BindException");
  }

  private static Map<String, String> txAdviceAttributeByMethodName(
      String contextFile, String attribute) throws Exception {
    Map<String, String> result = new HashMap<>();
    try (InputStream in =
        StockWriterTransactionIsolationTest.class
            .getClassLoader()
            .getResourceAsStream(contextFile)) {
      assertNotNull(in, contextFile + " must be on the test classpath");
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      Document document = factory.newDocumentBuilder().parse(in);
      NodeList advices = document.getElementsByTagNameNS(TX_NS, "advice");
      for (int i = 0; i < advices.getLength(); i++) {
        Element advice = (Element) advices.item(i);
        if (!"txAdvice".equals(advice.getAttribute("id"))) {
          continue;
        }
        NodeList methods = advice.getElementsByTagNameNS(TX_NS, "method");
        for (int j = 0; j < methods.getLength(); j++) {
          Element method = (Element) methods.item(j);
          result.put(method.getAttribute("name"), method.getAttribute(attribute));
        }
      }
    }
    return result;
  }
}
