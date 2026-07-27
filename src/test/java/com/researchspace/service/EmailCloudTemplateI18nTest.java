package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EmailCloudTemplateI18nTest {

  private static final String LOGIN_LINK = "http://rspace.example.com/login";
  private static final String SIGNUP_LINK = "http://rspace.example.com/signup?token=abc";
  private final EmailTemplateTestRenderer templates =
      new EmailTemplateTestRenderer("MessageAndNotificationCloud/");

  @ParameterizedTest(name = "{0}")
  @MethodSource("templateCases")
  void rendersTemplateWithDynamicContent(TemplateCase templateCase) {
    String output = templates.render(templateCase.template(), templateCase.model());

    for (String expected : templateCase.expected()) {
      assertTrue(output.contains(expected), () -> "Missing '" + expected + "' in: " + output);
    }
  }

  @Test
  void plaintextConversionPreservesInvitationLink() {
    String output =
        templates.renderPlainText(
            "groupInvitationExistingUser.vm",
            Map.of(
                "invited", new FakeUser("Jane Doe", "jane@example.com"),
                "creator", new FakeUser("Bob Smith", "bob@example.com"),
                "group", new FakeGroup("Smith Lab"),
                "acceptanceLink", LOGIN_LINK));

    assertTrue(output.contains(LOGIN_LINK), output);
    assertFalse(output.contains("<a"), output);
  }

  private static Stream<TemplateCase> templateCases() {
    FakeUser invited = new FakeUser("Jane Doe", "jane@example.com");
    FakeUser creator = new FakeUser("Bob Smith", "bob@example.com");
    FakeGroup group = new FakeGroup("Smith Lab");
    return Stream.of(
        new TemplateCase(
            "groupInvitationExistingUser.vm",
            Map.of(
                "invited", invited,
                "creator", creator,
                "group", group,
                "acceptanceLink", LOGIN_LINK),
            "Jane Doe",
            "Bob Smith",
            "bob@example.com",
            "Smith Lab",
            LOGIN_LINK),
        new TemplateCase(
            "groupInvitationNewUser.vm",
            Map.of("creator", creator, "group", group, "acceptanceLink", SIGNUP_LINK),
            "Bob Smith",
            "bob@example.com",
            "Smith Lab",
            SIGNUP_LINK),
        new TemplateCase(
            "groupPIInvitationExistingUser.vm",
            Map.of(
                "invited", invited,
                "creator", creator,
                "groupName", "Smith Lab",
                "acceptanceLink", LOGIN_LINK),
            "Jane Doe",
            "Bob Smith",
            "Smith Lab",
            LOGIN_LINK),
        new TemplateCase(
            "groupPIInvitationNewUser.vm",
            Map.of("creator", creator, "groupName", "Smith Lab", "acceptanceLink", SIGNUP_LINK),
            "Bob Smith",
            "Smith Lab",
            SIGNUP_LINK),
        new TemplateCase(
            "shareRecordInvitationExistingUser.vm",
            Map.of(
                "invited", invited,
                "creator", creator,
                "recordName", "My Experiment",
                "acceptanceLink", LOGIN_LINK),
            "Jane Doe",
            "Bob Smith",
            "My Experiment",
            LOGIN_LINK),
        new TemplateCase(
            "shareRecordInvitationNewUser.vm",
            Map.of(
                "creator", creator,
                "recordName", "My Experiment",
                "acceptanceLink", SIGNUP_LINK),
            "Bob Smith",
            "My Experiment",
            SIGNUP_LINK),
        new TemplateCase(
            "signupVerificationMsg.vm",
            Map.of(
                "firstName", "Alice",
                "htmlDomainPrefix", "https://rspace.example.com",
                "verifyLink", "https://rspace.example.com/cloud/verifysignup?token=xyz",
                "ipAddress", "1.2.3.4"),
            "Alice",
            "https://rspace.example.com/cloud/verifysignup?token=xyz",
            "1.2.3.4"));
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

  public static class FakeGroup {
    private final String displayName;

    FakeGroup(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }
}
