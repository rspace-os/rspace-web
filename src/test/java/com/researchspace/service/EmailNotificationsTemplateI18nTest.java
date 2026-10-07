package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.tools.generic.DateTool;
import org.junit.jupiter.api.Test;

class EmailNotificationsTemplateI18nTest {

  private final EmailTemplateTestRenderer templates =
      new EmailTemplateTestRenderer("messageAndNotificationEmails/");

  private static Map<String, Object> communication(boolean notification, String message, long id) {
    Map<String, Object> communication = new HashMap<>();
    communication.put(
        "originator",
        Map.of(
            "fullName", "Jane Doe",
            "email", "jane@example.com",
            "id", 1L,
            "username", "jdoe"));
    communication.put("notification", notification);
    communication.put("message", message);
    communication.put("id", id);
    communication.put("ignoreRecordLinkInMessage", false);
    return communication;
  }

  private static Map<String, Object> makeSimpleMessage() {
    Map<String, Object> communication = communication(false, "Hello there!", 10L);
    communication.put("messageType", "SIMPLE_MESSAGE");
    return communication;
  }

  private static Map<String, Object> makeNotification(
      boolean withType, Map<String, Object> record) {
    Map<String, Object> communication = communication(true, "Check your export.", 20L);
    communication.put("notificationMessage", "Your action was processed.");
    if (withType) {
      communication.put("notificationType", "SYSTEM");
    }
    if (record != null) {
      communication.put("record", record);
    }
    return communication;
  }

  private static Map<String, Object> makeRequest(String messageType, String groupName) {
    Map<String, Object> communication = communication(false, "Please join my group.", 30L);
    communication.put("messageType", messageType);
    communication.put("group", Map.of("displayName", groupName));
    return communication;
  }

  @Test
  void exportCompleteNotificationRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("name", "[TestExport]");
    model.put("size", "5 MB");
    model.put("exportedRecordsSummary", "The archive includes 3 records.");
    model.put("exportReportLink", "http://example.com/report/1");
    model.put("link", "http://example.com/download");
    model.put("linkText", "http://example.com/download");
    model.put("removalPolicyMessage", "This archive will never be removed.");

    String out = templates.render("exportCompleteNotification.vm", model);
    assertThat(out)
        .as("rendered: " + out)
        .contains("Your export [TestExport] is completed and generated an archive of size 5 MB");
    assertThat(out).as("rendered: " + out).contains("More details are available on the");
    assertThat(out).as("rendered: " + out).contains("export report page");
    assertThat(out)
        .as("rendered: " + out)
        .contains("Please click on, or copy the link into a browser to access the export:");
  }

  @Test
  void messageHtmlRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeSimpleMessage());
    model.put("dateOb", new Date());
    model.put("baseURL", "http://localhost:8080");
    model.put("date", new DateTool());

    String out = templates.render("message.vm", model);
    assertThat(out).as("rendered: " + out).contains("RSpace message from");
    assertThat(out).as("rendered: " + out).contains("sent you a message on");
    assertThat(out)
        .as("rendered: " + out)
        .contains("To reply to the sender of this message, please do so in");
  }

  @Test
  void notificationHtmlRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeNotification(true, null));
    model.put("dateOb", new Date());
    model.put("baseURL", "http://localhost:8080");
    model.put("date", new DateTool());
    model.put("notificationTypeKey", "notificationType.documentShared");

    String out = templates.render("notification.vm", model);
    assertThat(out).as("rendered: " + out).contains("Notification from RSpace");
    assertThat(out).as("rendered: " + out).contains("generated a notification for you of type");
    assertThat(out).as("rendered: " + out).contains("Document Shared");
  }

  @Test
  void raidUpdateSuccessRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("succeed", true);
    model.put("repo", "Zenodo");
    model.put("doi", "https://doi.org/10.5281/zenodo.123456");
    model.put("url", "https://raid.org/r/abc.123");
    model.put("identifier", "abc.123");

    String out = templates.render("raidUpdateCompleteNotification.vm", model);
    assertThat(out).as("rendered: " + out).contains("has been added to your RAiD record");
    assertThat(out).as("rendered: " + out).contains("Your research output recently deposited on");
    assertThat(out).as("rendered: " + out).contains("with the DOI");
  }

  @Test
  void raidUpdateFailureRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("succeed", false);
    model.put("repo", "Zenodo");
    model.put("doi", "https://doi.org/10.5281/zenodo.123456");
    model.put("url", "https://raid.org/r/abc.123");
    model.put("identifier", "abc.123");

    String out = templates.render("raidUpdateCompleteNotification.vm", model);
    assertThat(out).as("rendered: " + out).contains("could not be added to your RAiD record");
  }

  @Test
  void repoDepositSuccessRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put(
        "result",
        Map.of(
            "succeeded", true,
            "message", "Deposited successfully.",
            "url", "https://zenodo.org/r/1"));
    model.put("app", Map.of("label", "Zenodo"));

    String out = templates.render("repoDepositCompleteNotification.vm", model);
    assertThat(out).as("rendered: " + out).contains("is complete");
    assertThat(out).as("rendered: " + out).contains("Zenodo");
  }

  @Test
  void repoDepositFailureRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("result", Map.of("succeeded", false, "message", "Server error."));
    model.put("app", Map.of("label", "Zenodo"));

    String out = templates.render("repoDepositCompleteNotification.vm", model);
    assertThat(out).as("rendered: " + out).contains("failed");
    assertThat(out).as("rendered: " + out).contains("No URL for the repository could be retrieved");
  }

  @Test
  void requestHtmlRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeRequest("REQUEST_JOIN_LAB_GROUP", "Smith Lab"));
    model.put("dateOb", new Date());
    model.put("baseURL", "http://localhost:8080");
    model.put("date", new DateTool());

    String out = templates.render("request.vm", model);
    assertThat(out).as("rendered: " + out).contains("In order to join the");
    assertThat(out).as("rendered: " + out).contains("Lab");
    assertThat(out).as("rendered: " + out).contains("group's PI will be permitted");
    assertThat(out).as("rendered: " + out).contains("sent you a request on");
  }

  @Test
  void requestHtmlProjectGroupRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeRequest("REQUEST_JOIN_PROJECT_GROUP", "Science Project"));
    model.put("dateOb", new Date());
    model.put("baseURL", "http://localhost:8080");
    model.put("date", new DateTool());

    String out = templates.render("request.vm", model);
    assertThat(out).as("rendered: " + out).contains("In order to join the");
    assertThat(out).as("rendered: " + out).contains("Project");
    assertThat(out).as("rendered: " + out).contains("won't be visible");
  }

  @Test
  void requestPlaintextRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeRequest("REQUEST_JOIN_LAB_GROUP", "Smith Lab"));
    model.put("dateOb", new Date());
    model.put("baseURL", "http://localhost:8080");
    model.put("date", new DateTool());

    String out = templates.renderPlainText("request.vm", model);
    assertTrue(out.contains("RSpace Request from"), "rendered: " + out);
    assertTrue(out.contains("sent you a request on"), "rendered: " + out);
    assertTrue(
        out.contains(
            "ACCEPT (http://localhost:8080/dashboard/updateMessageStatus"
                + "?messageOrRequestId=30&status=COMPLETED)"),
        "rendered: " + out);
  }
}
