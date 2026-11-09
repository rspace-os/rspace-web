package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttributeEditor;
import org.w3c.dom.Element;

class OAuthTokenTransactionRuleTest {
  @Test
  void bothUiMintOverloadsSuspendTheOuterTransactionInBothContexts() throws Exception {
    var methods =
        Arrays.stream(OAuthTokenManager.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("createUiToken"))
            .toList();
    assertEquals(2, methods.size());
    for (String path :
        new String[] {
          "src/main/resources/applicationContext-service.xml",
          "src/test/resources/applicationContext-test-service.xml"
        }) {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      var document = factory.newDocumentBuilder().parse(Path.of(path).toFile());
      var advices = document.getElementsByTagNameNS("*", "advice");
      var source = new NameMatchTransactionAttributeSource();
      for (int i = 0; i < advices.getLength(); i++) {
        var advice = (Element) advices.item(i);
        if (!"txAdvice".equals(advice.getAttribute("id"))) continue;
        var rules = advice.getElementsByTagNameNS("*", "method");
        for (int j = 0; j < rules.getLength(); j++) {
          var rule = (Element) rules.item(j);
          String propagation = rule.getAttribute("propagation");
          var editor = new TransactionAttributeEditor();
          editor.setAsText("PROPAGATION_" + (propagation.isEmpty() ? "REQUIRED" : propagation));
          source.addTransactionalMethod(
              rule.getAttribute("name"),
              (org.springframework.transaction.interceptor.TransactionAttribute) editor.getValue());
        }
      }
      for (var method : methods) {
        assertEquals(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED,
            source
                .getTransactionAttribute(method, OAuthTokenManager.class)
                .getPropagationBehavior(),
            path + ": " + method);
      }
    }
  }
}
