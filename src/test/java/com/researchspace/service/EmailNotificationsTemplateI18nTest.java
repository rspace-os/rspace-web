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

  public static class FakeUser {
    private final String fullName;
    private final String email;
    private final long id;
    private final String username;

    FakeUser(String fullName, String email) {
      this(fullName, email, 1L, "testuser");
    }

    FakeUser(String fullName, String email, long id, String username) {
      this.fullName = fullName;
      this.email = email;
      this.id = id;
      this.username = username;
    }

    public String getFullName() {
      return fullName;
    }

    public String getEmail() {
      return email;
    }

    public long getId() {
      return id;
    }

    public String getUsername() {
      return username;
    }
  }

  public static class FakeRecord {
    private final String name;
    private final long id;
    private final String globalIdentifier;
    private final boolean notebook;

    FakeRecord(String name) {
      this(name, 42L, "SD42", false);
    }

    FakeRecord(String name, long id, String globalIdentifier, boolean notebook) {
      this.name = name;
      this.id = id;
      this.globalIdentifier = globalIdentifier;
      this.notebook = notebook;
    }

    public String getName() {
      return name;
    }

    public long getId() {
      return id;
    }

    public String getGlobalIdentifier() {
      return globalIdentifier;
    }

    public boolean isNotebook() {
      return notebook;
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

  public static class FakeCommunication {
    private final FakeUser originator;
    private FakeRecord record;
    private final boolean notification;
    private final String notificationType;
    private final String notificationMessage;
    private final String message;
    private final String messageType;
    private FakeGroup group;
    private final long id;
    private final boolean ignoreRecordLinkInMessage;

    FakeCommunication(
        FakeUser originator,
        FakeRecord record,
        boolean notification,
        String notificationType,
        String notificationMessage,
        String message,
        String messageType,
        FakeGroup group,
        long id,
        boolean ignoreRecordLinkInMessage) {
      this.originator = originator;
      this.record = record;
      this.notification = notification;
      this.notificationType = notificationType;
      this.notificationMessage = notificationMessage;
      this.message = message;
      this.messageType = messageType;
      this.group = group;
      this.id = id;
      this.ignoreRecordLinkInMessage = ignoreRecordLinkInMessage;
    }

    public FakeUser getOriginator() {
      return originator;
    }

    public FakeRecord getRecord() {
      return record;
    }

    public boolean isNotification() {
      return notification;
    }

    public String getNotificationType() {
      return notificationType;
    }

    public String getNotificationMessage() {
      return notificationMessage;
    }

    public String getMessage() {
      return message;
    }

    public String getMessageType() {
      return messageType;
    }

    public FakeGroup getGroup() {
      return group;
    }

    public long getId() {
      return id;
    }

    public boolean isIgnoreRecordLinkInMessage() {
      return ignoreRecordLinkInMessage;
    }
  }

  public static class FakeResult {
    private final boolean succeeded;
    private final String message;
    private final String url;

    FakeResult(boolean succeeded, String message, String url) {
      this.succeeded = succeeded;
      this.message = message;
      this.url = url;
    }

    public boolean isSucceeded() {
      return succeeded;
    }

    public String getMessage() {
      return message;
    }

    public String getUrl() {
      return url;
    }
  }

  public static class FakeApp {
    private final String label;

    FakeApp(String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  private FakeCommunication makeSimpleMessage() {
    return new FakeCommunication(
        new FakeUser("Jane Doe", "jane@example.com", 1L, "jdoe"),
        null,
        false,
        null,
        null,
        "Hello there!",
        "SIMPLE_MESSAGE",
        null,
        10L,
        false);
  }

  private FakeCommunication makeNotification(boolean withType, FakeRecord record) {
    return new FakeCommunication(
        new FakeUser("Jane Doe", "jane@example.com", 1L, "jdoe"),
        record,
        true,
        withType ? "SYSTEM" : null,
        "Your action was processed.",
        "Check your export.",
        null,
        null,
        20L,
        false);
  }

  private FakeCommunication makeRequest(String messageType, FakeGroup group) {
    return new FakeCommunication(
        new FakeUser("Jane Doe", "jane@example.com", 1L, "jdoe"),
        null,
        false,
        null,
        null,
        "Please join my group.",
        messageType,
        group,
        30L,
        false);
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
    model.put("result", new FakeResult(true, "Deposited successfully.", "https://zenodo.org/r/1"));
    model.put("app", new FakeApp("Zenodo"));

    String out = templates.render("repoDepositCompleteNotification.vm", model);
    assertTrue(out.contains("is complete"), "rendered: " + out);
    assertTrue(out.contains("Zenodo"), "rendered: " + out);
  }

  @Test
  void repoDepositFailureRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("result", new FakeResult(false, "Server error.", null));
    model.put("app", new FakeApp("Zenodo"));

    String out = templates.render("repoDepositCompleteNotification.vm", model);
    assertTrue(out.contains("failed"), "rendered: " + out);
    assertTrue(out.contains("No URL for the repository could be retrieved"), "rendered: " + out);
  }

  @Test
  void requestHtmlRendersI18nText() {
    Map<String, Object> model = new HashMap<>();
    model.put("cmm", makeRequest("REQUEST_JOIN_LAB_GROUP", new FakeGroup("Smith Lab")));
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
    model.put("cmm", makeRequest("REQUEST_JOIN_PROJECT_GROUP", new FakeGroup("Science Project")));
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
    model.put("cmm", makeRequest("REQUEST_JOIN_LAB_GROUP", new FakeGroup("Smith Lab")));
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
