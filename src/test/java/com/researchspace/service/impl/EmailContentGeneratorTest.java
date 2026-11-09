package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;

import com.researchspace.booking.service.BookingNotificationMessageFormatter;
import com.researchspace.model.booking.BookingTimeFormat;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import com.researchspace.service.EmailContent;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserLocaleService;
import com.researchspace.testutils.TestFactory;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class EmailContentGeneratorTest {

  private EmailContentGenerator generator;

  @BeforeEach
  void setUp() {
    VelocityEngine velocity =
        new VelocityEngine() {
          @Override
          public boolean mergeTemplate(
              String name,
              String encoding,
              org.apache.velocity.context.Context context,
              java.io.Writer writer) {
            String path =
                name.contains("/")
                    ? name
                    : "velocityTemplates/messageAndNotificationEmails/" + name;
            return super.mergeTemplate(path, encoding, context, writer);
          }
        };
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
    ReflectionTestUtils.setField(generator, "userLocaleService", new UserLocaleService());
  }

  @Test
  void resolvesSubjectAndRendersLocalizedAlternativesWithoutMutatingTheModel() {
    Map<String, Object> model = new HashMap<>();
    model.put("runAs", TestFactory.createAnyUser("runAsUser"));
    model.put("systemUser", TestFactory.createAnyUser("systemUser"));
    setDeploymentLocale("de-DE");
    EmailContent content =
        generator.render(
            "email.admin.adminRunningAsUserNotification.subject",
            "velocityTemplates/adminRunningAsUserNotification.vm",
            model);

    assertFalse(model.containsKey("msg"));
    assertEquals("RSpace admin is using your account", content.subject());
    assertThat(content.htmlContent()).startsWith("<html lang=\"de-DE\">\n<body>\n");
    assertThat(content.htmlContent()).endsWith("\n</body>\n</html>");
    assertThat(content.plainTextContent()).contains("RSpace admin");
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

    setDeploymentLocale("en-GB");
    EmailContent content =
        generator.render(
            "email.account.accountEnablementNotification.subjectEnabled",
            "velocityTemplates/accountOperations/accountEnablementNotification.vm",
            model);

    assertThat(content.htmlContent()).doesNotContain("<head>");
    assertThat(content.plainTextContent()).contains(systemUser.getEmail());
  }

  @Test
  void escapesNotificationOriginatorNames() {
    setDeploymentLocale("en-US");
    var originator = TestFactory.createAnyUser("actor");
    originator.setId(7L);
    originator.setFirstName("Actor <script>");
    var notification = TestFactory.createANotification(originator);

    EmailContent content =
        new CommunicationEmailContentGenerator(generator, "https://rspace.example")
            .generate(notification);

    assertThat(content.htmlContent())
        .contains("Actor &lt;script&gt;", "href=\"https://rspace.example/userform?userId=7\"")
        .doesNotContain("Actor <script>");
    assertThat(content.plainTextContent()).contains("Actor <script>");
  }

  @ParameterizedTest
  @CsvSource({
    "NOTIFICATION_BOOKING_CREATED,Booking Created: Microscope <script>",
    "NOTIFICATION_BOOKING_CANCELLED,Booking Cancelled: Microscope <script>",
    "NOTIFICATION_BOOKING_RESTORED,Booking Restored: Microscope <script>",
    "NOTIFICATION_BOOKING_CHANGED,Booking Changed: Microscope <script>"
  })
  void usesRecipientEmailContextForBookingSubjectsAndHeaders(
      NotificationType type, String subject) {
    setDeploymentLocale("en-US");
    var originator = TestFactory.createAnyUser("actor");
    originator.setId(7L);
    originator.setFirstName("Actor <script>");
    var notification = TestFactory.createANotification(originator);
    notification.setNotificationType(type);
    var data = new BookingNotificationData();
    data.setInstrumentName("Microscope <script>");
    data.setEmailDisplayTimezone("Asia/Tokyo");
    data.setEmailTimeFormat(BookingTimeFormat.H24);
    data.setBookingId("42");
    data.setInstrumentGlobalIdentifier("IN12");
    data.setStartTime("2026-01-02T03:04:05Z");
    data.setEndTime("2026-01-02T04:04:05Z");
    notification.setNotificationMessage(
        new BookingNotificationMessageFormatter(new JsonMessageSource(), "https://rspace.example/")
            .formatForEmail(type, data, ZoneId.of("Asia/Tokyo"), Locale.US, BookingTimeFormat.H24));
    notification.setNotificationDataObject(data);
    var fixedNow = Instant.parse("2026-01-15T21:17:00Z");
    EmailContent content;
    try (var instants = mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
      instants.when(Instant::now).thenReturn(fixedNow);
      content =
          new CommunicationEmailContentGenerator(generator, "https://rspace.example")
              .generate(notification);
    }
    String header =
        content.htmlContent().split("https://rspace.example/booking/calendar/bookings/42", 2)[0];
    assertThat(header)
        .contains("Jan 16, 2026", "06:17", "Asia/Tokyo, UTC+09:00")
        .doesNotContain(" AM", " PM");
    assertEquals(subject, content.subject());
    assertThat(content.htmlContent())
        .contains("Asia/Tokyo", "Actor &lt;script&gt;", "Microscope &lt;script&gt;")
        .doesNotContain("Actor <script>", "href=\"/booking/")
        .contains(
            "href=\"https://rspace.example/booking/calendar/bookings/42\"",
            "href=\"https://rspace.example/booking/bookable-items/IN12\"");
    assertThat(content.plainTextContent())
        .contains(
            "Asia/Tokyo",
            "Microscope <script>",
            "https://rspace.example/booking/calendar/bookings/42",
            "https://rspace.example/booking/bookable-items/IN12");
  }

  private void setDeploymentLocale(String languageTag) {
    UserLocaleService locales =
        (UserLocaleService) ReflectionTestUtils.getField(generator, "userLocaleService");
    ReflectionTestUtils.setField(locales, "configuredLocaleTag", languageTag);
  }
}
