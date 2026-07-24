package com.researchspace.service;

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
    assertTrue(
        out.contains("Your export [TestExport] is completed and generated an archive of size 5 MB"),
        "rendered: " + out);
    assertTrue(out.contains("More details are available on the"), "rendered: " + out);
    assertTrue(out.contains("export report page"), "rendered: " + out);
    assertTrue(
        out.contains("Please click on, or copy the link into a browser to access the export:"),
        "rendered: " + out);
  }

  @Test
  void messageHtmlRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeSimpleMessage());
    model.put("dateOb", new Date());
    model.put("baseURL", "http://localhost:8080");
    model.put("date", new DateTool());

    String out = templates.render("message.vm", model);
    assertTrue(out.contains("RSpace message from"), "rendered: " + out);
    assertTrue(out.contains("sent you a message on"), "rendered: " + out);
    assertTrue(
        out.contains("To reply to the sender of this message, please do so in"),
        "rendered: " + out);
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
    assertTrue(out.contains("Notification from RSpace"), "rendered: " + out);
    assertTrue(out.contains("generated a notification for you of type"), "rendered: " + out);
    assertTrue(out.contains("Document Shared"), "rendered: " + out);
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
    assertTrue(out.contains("has been added to your RAiD record"), "rendered: " + out);
    assertTrue(out.contains("Your research output recently deposited on"), "rendered: " + out);
    assertTrue(out.contains("with the DOI"), "rendered: " + out);
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
    assertTrue(out.contains("could not be added to your RAiD record"), "rendered: " + out);
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
    assertTrue(out.contains("is complete"), "rendered: " + out);
    assertTrue(out.contains("Zenodo"), "rendered: " + out);
  }

  @Test
  void repoDepositFailureRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("result", Map.of("succeeded", false, "message", "Server error."));
    model.put("app", Map.of("label", "Zenodo"));

    String out = templates.render("repoDepositCompleteNotification.vm", model);
    assertTrue(out.contains("failed"), "rendered: " + out);
    assertTrue(out.contains("No URL for the repository could be retrieved"), "rendered: " + out);
  }

  @Test
  void requestHtmlRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeRequest("REQUEST_JOIN_LAB_GROUP", "Smith Lab"));
    model.put("dateOb", new Date());
    model.put("baseURL", "http://localhost:8080");
    model.put("date", new DateTool());

    String out = templates.render("request.vm", model);
    assertTrue(out.contains("In order to join the"), "rendered: " + out);
    assertTrue(out.contains("Lab"), "rendered: " + out);
    assertTrue(out.contains("group's PI will be permitted"), "rendered: " + out);
    assertTrue(out.contains("sent you a request on"), "rendered: " + out);
  }

  @Test
  void requestHtmlProjectGroupRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeRequest("REQUEST_JOIN_PROJECT_GROUP", "Science Project"));
    model.put("dateOb", new Date());
    model.put("baseURL", "http://localhost:8080");
    model.put("date", new DateTool());

    String out = templates.render("request.vm", model);
    assertTrue(out.contains("In order to join the"), "rendered: " + out);
    assertTrue(out.contains("Project"), "rendered: " + out);
    assertTrue(out.contains("won't be visible"), "rendered: " + out);
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
