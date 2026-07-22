package com.researchspace.webapp.integrations.slack;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.slack.SlackMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

public class SlackControllerTest {

  private SlackController slackController = new SlackController();

  private UserAppConfigManager userAppCfgMgr = mock(UserAppConfigManager.class);
  MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());

  @Before
  public void setUp() throws Exception {
    slackController.setVerificationToken("dummyToken");
    slackController.setUserAppCfgMgr(userAppCfgMgr);
    slackController.setClock(Clock.fixed(Instant.parse("2017-12-28T01:23:45Z"), ZoneId.of("UTC")));
    slackController.setMessageSource(messages);
  }

  @Test
  public void checkMaxSaveConversationTimePeriod() throws Exception {

    List<User> noUsers = new ArrayList<>();
    when(userAppCfgMgr.findByAppConfigValue(any(), any())).thenReturn(noUsers);

    Map<String, String> params = getParamsWithGoodToken();
    params.put("text", "");
    SlackMessage noTimePeriodResponse = slackController.saveConversation(params);
    assertEquals(
        "Please provide a valid time period, e.g. '7 days' or '7d'",
        noTimePeriodResponse.getText());

    params.put("text", "91d");
    SlackMessage tooLongPeriodResponse = slackController.saveConversation(params);
    assertEquals(
        messages.getMessage("apps.slack.saveConversation.maxTimePeriod", new Object[] {"90"}),
        tooLongPeriodResponse.getText());

    params.put("text", "90d");
    SlackMessage okResponse = slackController.saveConversation(params);
    // passed the time limit
    assertEquals(
        "There are no RSpace users associated with this Slack account! Please connect to Slack"
            + " from the RSpace Apps tab.",
        okResponse.getText());
  }

  @Test
  public void checkParseTimePeriodLastDaysFormat() throws Exception {
    SlackController.TimePeriod timePeriod =
        slackController.parseTimeInterval("5 days", DateTimeZone.UTC);
    assertThat(
        timePeriod.getFromTimestampMillis(),
        is(Instant.parse("2017-12-23T01:23:45Z").toEpochMilli()));
    assertThat(
        timePeriod.getToTimestampMillis(),
        is(Instant.parse("2017-12-28T01:23:45Z").toEpochMilli()));
  }

  @Test
  public void checkParseTimePeriodLastDaysHoursMinsFormat() throws Exception {
    SlackController.TimePeriod timePeriod =
        slackController.parseTimeInterval("1d 2h 3min", DateTimeZone.UTC);
    assertThat(
        timePeriod.getFromTimestampMillis(),
        is(Instant.parse("2017-12-26T23:20:45Z").toEpochMilli()));
    assertThat(
        timePeriod.getToTimestampMillis(),
        is(Instant.parse("2017-12-28T01:23:45Z").toEpochMilli()));
  }

  @Test
  public void checkParseTimePeriodFromSimpleDateFormat() throws Exception {
    SlackController.TimePeriod timePeriod =
        slackController.parseTimeInterval("from 2017-12-23", DateTimeZone.UTC);
    assertThat(
        timePeriod.getFromTimestampMillis(),
        is(Instant.parse("2017-12-23T00:00:00Z").toEpochMilli()));
    assertThat(
        timePeriod.getToTimestampMillis(),
        is(Instant.parse("2017-12-28T01:23:45Z").toEpochMilli()));
  }

  @Test
  public void checkParseTimePeriodFromISO8601DateFormat() throws Exception {
    SlackController.TimePeriod timePeriod =
        slackController.parseTimeInterval("from 2017-12-23T14:00", DateTimeZone.UTC);
    assertThat(
        timePeriod.getFromTimestampMillis(),
        is(Instant.parse("2017-12-23T14:00:00Z").toEpochMilli()));
    assertThat(
        timePeriod.getToTimestampMillis(),
        is(Instant.parse("2017-12-28T01:23:45Z").toEpochMilli()));
  }

  @Test
  public void checkParseTimePeriodToSimpleDateFormat() throws Exception {
    SlackController.TimePeriod timePeriod =
        slackController.parseTimeInterval("to 2017-12-23", DateTimeZone.UTC);
    // MAX_REQUESTED_DURATION_MILLIS millis time period is returned
    assertThat(
        timePeriod.getFromTimestampMillis(),
        is(Instant.parse("2017-09-24T00:00:00Z").toEpochMilli()));
    assertThat(
        timePeriod.getToTimestampMillis(),
        is(Instant.parse("2017-12-23T00:00:00Z").toEpochMilli()));
  }

  @Test
  public void checkParseTimePeriodToISO8601DateFormat() throws Exception {
    SlackController.TimePeriod timePeriod =
        slackController.parseTimeInterval("to 2017-12-23T14:00", DateTimeZone.UTC);
    // MAX_REQUESTED_DURATION_MILLIS millis time period is returned
    assertThat(
        timePeriod.getFromTimestampMillis(),
        is(Instant.parse("2017-09-24T14:00:00Z").toEpochMilli()));
    assertThat(
        timePeriod.getToTimestampMillis(),
        is(Instant.parse("2017-12-23T14:00:00Z").toEpochMilli()));
  }

  @Test
  public void checkParseTimePeriodFromToDateFormat() throws Exception {
    SlackController.TimePeriod timePeriod =
        slackController.parseTimeInterval("from 2017-10-12 to 2017-12-23T14:00", DateTimeZone.UTC);
    assertThat(
        timePeriod.getFromTimestampMillis(),
        is(Instant.parse("2017-10-12T00:00:00Z").toEpochMilli()));
    assertThat(
        timePeriod.getToTimestampMillis(),
        is(Instant.parse("2017-12-23T14:00:00Z").toEpochMilli()));
  }

  private Map<String, String> getParamsWithGoodToken() {
    Map<String, String> params = new HashMap<>();
    params.put("token", "dummyToken");
    return params;
  }
}
