package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.velocity.tools.generic.DateTool;
import org.junit.jupiter.api.Test;

class EmailTemplateI18nTest {

  private final EmailTemplateTestRenderer templates = new EmailTemplateTestRenderer("");

  @Test
  void groupInvitationAcceptedRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("status", "COMPLETED");
    model.put("user", new FakeUser("Jane Doe", "jane@example.com"));
    model.put("groupType", "lab group");
    model.put("groupName", "Smith Lab");
    String out = templates.render("groupInvitationAccepted.vm", model);
    assertTrue(
        out.contains("Jane Doe has accepted your invitation to join the lab group Smith Lab."),
        "rendered: " + out);
  }

  @Test
  void recordWitnessedDeclinedRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("status", "REJECTED");
    model.put("user", new FakeUser("Jane Doe", "jane@example.com"));
    model.put("record", new FakeRecord("Experiment 1"));
    String out = templates.render("recordWitnessed.vm", model);
    assertTrue(
        out.contains(
            "Jane Doe (jane@example.com) has declined to witness your signature of document"
                + " Experiment 1."),
        "rendered: " + out);
  }

  @Test
  void adminRunningAsUserNotificationRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("runAs", new FakeUser("Jane Doe", "jane@example.com"));
    model.put("systemUser", new FakeUser("Admin User", "admin@example.com"));
    String out = templates.render("adminRunningAsUserNotification.vm", model);
    assertTrue(out.contains("Hello Jane Doe,"), "rendered: " + out);
    assertTrue(
        out.contains(
            "The RSpace system administrator (Admin User) has recently logged into your RSpace"
                + " account."),
        "rendered: " + out);
    assertTrue(
        out.contains(
            "please contact the RSpace admin at <a"
                + " href=\"mailto:admin@example.com\">admin@example.com</a>"),
        "rendered: " + out);
  }

  @Test
  void adminRunningAsUserNotificationPlaintextRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("runAs", new FakeUser("Jane Doe", "jane@example.com"));
    model.put("systemUser", new FakeUser("Admin User", "admin@example.com"));
    String out = templates.renderPlainText("adminRunningAsUserNotification.vm", model);
    assertTrue(out.contains("Hello Jane Doe,"), "rendered: " + out);
    assertTrue(
        out.contains("please contact the RSpace admin at admin@example.com"), "rendered: " + out);
  }

  @Test
  void promoteToPICompleteRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("newPI", new FakeUser("Dr. Smith", "smith@example.com"));
    model.put("systemUser", new FakeUser("Admin User", "admin@example.com"));
    model.put("newLabGroup", new FakeGroup(42L));
    model.put("baseURL", "https://rspace.example.com");
    String out = templates.render("promoteToPIComplete.vm", model);
    assertTrue(out.contains("Hello Dr. Smith,"), "rendered: " + out);
    assertTrue(
        out.contains("The RSpace system administrator has set you up with a PI role."),
        "rendered: " + out);
    assertTrue(out.contains("https://rspace.example.com/groups/view/42"), "rendered: " + out);
    assertTrue(out.contains("https://rspace.example.com/userform?userId=99"), "rendered: " + out);
  }

  @Test
  void supportLogFilesRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("user", new FakeUser("Sysadmin One", "sysadmin@example.com"));
    model.put("dateOb", new Date());
    model.put("date", new DateTool());
    model.put("message", "Please check this error.");
    model.put("logLines", List.of("Error: something went wrong", "at line 42"));
    String out = templates.render("supportLogFiles.vm", model);
    assertTrue(
        out.contains(
            "Sysadmin One (sysadmin@example.com) generated an RSpace server error log file on"),
        "rendered: " + out);
    assertTrue(
        out.contains("The following message was sent from Sysadmin One"), "rendered: " + out);
    assertTrue(out.contains("END OF LOGS"), "rendered: " + out);
  }

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

    public Long getId() {
      return 99L;
    }
  }

  public static class FakeRecord {
    private final String name;

    FakeRecord(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public static class FakeGroup {
    private final Long id;

    FakeGroup(Long id) {
      this.id = id;
    }

    public Long getId() {
      return id;
    }
  }
}
