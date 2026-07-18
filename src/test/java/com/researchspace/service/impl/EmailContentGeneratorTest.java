package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.EmailContent;
import com.researchspace.testutils.TestFactory;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailContentGeneratorTest {

  private EmailContentGenerator generator;

  @BeforeEach
  void setUp() {
    VelocityEngine velocity = new VelocityEngine();
    velocity.setProperty("resource.loaders", "class");
    velocity.setProperty(
        "resource.loader.class.class",
        "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
    velocity.setProperty("velocimacro.library", "velocityTemplates/VM_global_library.vm");
    velocity.init();

    generator = new EmailContentGenerator();
    ReflectionTestUtils.setField(generator, "velocity", velocity);
  }

  @Test
  void rendersCompleteAlternativesWithoutMutatingTheModel() {
    Map<String, Object> model = new HashMap<>();
    EmailContent content =
        generator.generatePlainTextAndHtmlContent(
            "velocityTemplates/messageAndNotificationEmails/testMessage.vm", model, Locale.GERMANY);

    assertFalse(model.containsKey("msg"));
    assertTrue(content.htmlContent().startsWith("<html lang=\"de-DE\">\n<body>\n"));
    assertTrue(content.htmlContent().endsWith("\n</body>\n</html>"));
    assertTrue(content.plainTextContent().startsWith("Welcome to ResearchSpace"));
  }

  @Test
  void createsBothAlternativesFromRenderedBodyFragment() {
    EmailContent content =
        generator.fromHtmlFragment(
            null, "  <p>Hello <a href=\"https://example.com\">there</a></p>  ", Locale.UK);

    assertEquals(
        "<html lang=\"en-GB\">\n"
            + "<body>\n"
            + "<p>Hello <a href=\"https://example.com\">there</a></p>\n"
            + "</body>\n"
            + "</html>",
        content.htmlContent());
    assertEquals("Hello there (https://example.com)", content.plainTextContent());
  }

  @Test
  void rendersEmailFragmentsWithoutNestedHeadAndPreservesAdminContact() {
    var user = TestFactory.createAnyUser("disabledUser");
    var systemUser = TestFactory.createAnyUser("systemUser");
    Map<String, Object> model = new HashMap<>();
    model.put("user", user);
    model.put("systemUser", systemUser);
    model.put("accountDisabled", "true");

    EmailContent content =
        generator.generatePlainTextAndHtmlContent(
            "velocityTemplates/accountOperations/accountEnablementNotification.vm",
            model,
            Locale.UK);

    assertFalse(content.htmlContent().contains("<head>"));
    assertTrue(content.plainTextContent().contains(systemUser.getEmail()));
  }
}
