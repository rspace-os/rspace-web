package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.service.EmailContent;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.testutils.TestFactory;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
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
    ReflectionTestUtils.setField(
        generator, "messages", new MessageSourceUtils(new JsonMessageSource()));
  }

  @Test
  void resolvesSubjectAndRendersLocalizedAlternativesWithoutMutatingTheModel() {
    Map<String, Object> model = new HashMap<>();
    model.put("runAs", TestFactory.createAnyUser("runAsUser"));
    model.put("systemUser", TestFactory.createAnyUser("systemUser"));
    LocaleContextHolder.setLocale(Locale.GERMANY);
    EmailContent content;
    try {
      content =
          generator.render(
              "email.admin.adminRunningAsUserNotification.subject",
              "velocityTemplates/adminRunningAsUserNotification.vm",
              model);
    } finally {
      LocaleContextHolder.resetLocaleContext();
    }

    assertFalse(model.containsKey("msg"));
    assertEquals("RSpace admin is using your account", content.subject());
    assertTrue(content.htmlContent().startsWith("<html lang=\"de-DE\">\n<body>\n"));
    assertTrue(content.htmlContent().endsWith("\n</body>\n</html>"));
    assertTrue(content.plainTextContent().contains("RSpace admin"));
  }

  @Test
  void createsBothAlternativesFromRenderedBodyFragment() {
    EmailContent content =
        generator.fromHtmlFragment(
            "subject", "  <p>Hello <a href=\"https://example.com\">there</a></p>  ", Locale.UK);

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

    LocaleContextHolder.setLocale(Locale.UK);
    EmailContent content;
    try {
      content =
          generator.render(
              "email.account.accountEnablementNotification.subjectEnabled",
              "velocityTemplates/accountOperations/accountEnablementNotification.vm",
              model);
    } finally {
      LocaleContextHolder.resetLocaleContext();
    }

    assertFalse(content.htmlContent().contains("<head>"));
    assertTrue(content.plainTextContent().contains(systemUser.getEmail()));
  }
}
