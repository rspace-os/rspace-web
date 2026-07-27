package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EmailAccountTemplateI18nTest {

  private static final String BASE_URL = "https://rspace.example.com";
  private final EmailTemplateTestRenderer templates =
      new EmailTemplateTestRenderer("accountOperations/");

  @ParameterizedTest(name = "{0}")
  @MethodSource("templateCases")
  void rendersTemplateWithDynamicContent(TemplateCase templateCase) {
    String output = templates.render(templateCase.template(), templateCase.model());

    for (String expected : templateCase.expected()) {
      assertTrue(output.contains(expected), () -> "Missing '" + expected + "' in: " + output);
    }
  }

  @Test
  void accountEnablementRendersBothBranches() {
    Map<String, Object> model =
        Map.of(
            "user",
            new FakeUser("Jane Doe", "jane@example.com"),
            "systemUser",
            new FakeUser("Admin User", "admin@example.com"),
            "baseURL",
            BASE_URL,
            "accountDisabled",
            "true");

    String disabled = templates.render("accountEnablementNotification.vm", model);
    String enabled =
        templates.render(
            "accountEnablementNotification.vm",
            Map.of(
                "user",
                model.get("user"),
                "systemUser",
                model.get("systemUser"),
                "baseURL",
                BASE_URL,
                "accountDisabled",
                "false"));

    assertTrue(disabled.contains("admin@example.com"), disabled);
    assertFalse(disabled.contains(BASE_URL + "/workspace"), disabled);
    assertTrue(enabled.contains(BASE_URL + "/workspace"), enabled);
  }

  @Test
  void defaultWelcomeRendersGroupAndIndividualBranches() {
    Map<String, Object> model =
        Map.of("userFirstName", "Alice", "htmlDomainPrefix", BASE_URL, "groupName", "Smith Lab");

    String groupWelcome = templates.render("defaultWelcomePostBatchSignupemail.vm", model);
    String individualWelcome =
        templates.render(
            "defaultWelcomePostBatchSignupemail.vm",
            Map.of("userFirstName", "Alice", "htmlDomainPrefix", BASE_URL));

    assertTrue(groupWelcome.contains("Smith Lab"), groupWelcome);
    assertFalse(individualWelcome.contains("Smith Lab"), individualWelcome);
    assertTrue(individualWelcome.contains("individual user"), individualWelcome);
  }

  @Test
  void plaintextConversionPreservesUsernameAndLoginLink() {
    String output =
        templates.renderPlainText(
            "usernameReminderMessage.vm",
            Map.of(
                "username", "jdoe",
                "loginLink", BASE_URL + "/login",
                "ipAddress", "10.0.0.2"));

    assertTrue(output.contains("jdoe"), output);
    assertTrue(output.contains(BASE_URL + "/login"), output);
    assertFalse(output.contains("<a"), output);
  }

  private static Stream<TemplateCase> templateCases() {
    return Stream.of(
        new TemplateCase(
            "activationRequest.vm",
            Map.of(
                "installation", "ResearchSpace Cloud",
                "fullName", "John Smith",
                "email", "john@example.com",
                "acceptlink", BASE_URL + "/admin/users/authorise/42",
                "denylink", BASE_URL + "/admin/users/deny/42"),
            "ResearchSpace Cloud",
            "John Smith",
            "john@example.com",
            BASE_URL + "/admin/users/authorise/42",
            BASE_URL + "/admin/users/deny/42"),
        new TemplateCase(
            "emailChangeConfirmationMsg.vm",
            Map.of("firstName", "Carol", "newEmailAddress", "carol.new@example.com"),
            "Carol",
            "carol.new@example.com"),
        new TemplateCase(
            "emailChangeVerificationMsg.vm",
            Map.of(
                "firstName", "Dave",
                "verifyLink", BASE_URL + "/cloud/verifyEmailChange?token=abc123",
                "ipAddress", "192.168.1.1"),
            "Dave",
            BASE_URL + "/cloud/verifyEmailChange?token=abc123",
            "192.168.1.1"),
        new TemplateCase(
            "genericAccountActivation.vm",
            Map.of("fullName", "Eve Smith", "link", BASE_URL + "/workspace"),
            "Eve Smith",
            BASE_URL + "/workspace"),
        new TemplateCase(
            "genericAccountDenial.vm", Map.of("fullName", "Frank Jones"), "Frank Jones"),
        new TemplateCase(
            "newUserAccountComplete.vm",
            Map.of(
                "newUser",
                new FakeNewUser("Grace Hall", "grace"),
                "adminUser",
                new FakeUser("Admin User", "admin@example.com"),
                "baseURL",
                BASE_URL,
                "newUserRole",
                "USER"),
            "Grace Hall",
            "grace",
            BASE_URL + "/workspace"),
        new TemplateCase(
            "passwordResetComplete.vm",
            Map.of("passwordType", "verification password"),
            "verification password"),
        new TemplateCase(
            "passwordResetMessage.vm",
            Map.of(
                "passwordType", "password",
                "resetLink", BASE_URL + "/signup/passwordResetReply?token=xyz",
                "ipAddress", "10.0.0.1"),
            BASE_URL + "/signup/passwordResetReply?token=xyz",
            "10.0.0.1"),
        new TemplateCase(
            "usernameReminderMessage.vm",
            Map.of(
                "username", "jdoe",
                "loginLink", BASE_URL + "/login",
                "ipAddress", "10.0.0.2"),
            "jdoe",
            BASE_URL + "/login",
            "10.0.0.2"));
  }

  private record TemplateCase(String template, Map<String, Object> model, String... expected) {}

  public static class FakeUser {
    private final String fullName;
    private final String email;

    FakeUser(String fullName, String email) {
      this.fullName = fullName;
      this.email = email;
    }

    public String getFullName() {
      return fullName;
    }

    public String getEmail() {
      return email;
    }
  }

  public static class FakeNewUser {
    private final String fullName;
    private final String username;

    FakeNewUser(String fullName, String username) {
      this.fullName = fullName;
      this.username = username;
    }

    public String getFullName() {
      return fullName;
    }

    public String getUsername() {
      return username;
    }
  }
}
