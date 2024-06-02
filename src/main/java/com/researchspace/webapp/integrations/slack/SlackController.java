package com.researchspace.webapp.integrations.slack;

import com.researchspace.analytics.service.AnalyticsEvent;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.ChatBotFunctionalityHandler;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.slack.SlackAttachment;
import com.researchspace.slack.SlackAuthToken;
import com.researchspace.slack.SlackMessage;
import com.researchspace.slack.SlackUser;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError.OauthAuthorizationErrorBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.SecurityUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Class responsible for handling connection between RSpace and Slack */
@Controller
@RequestMapping("/slack")
public class SlackController extends BaseController {
  private static final String ERROR = "error";
  private static final String USER_ID = "user_id";
  private static final String TEAM_ID = "team_id";
  private static final String SAVE_CONVO_ERROR_GENERIC_MSG =
      "apps.slack.saveconversation.genericDefault";
  private static final String EXPORT_MESSAGES_DOCUMENT_NAME = "Exported Slack Messages";
  private static final long MAX_REQUESTED_DURATION_MILLIS = 90 * 24 * 3600 * 1000L; // 90 days

  // Parses "1d 2h 3min", "4 days" etc
  private static final PeriodFormatter PERIOD_FORMATTER =
      new PeriodFormatterBuilder()
          .appendDays()
          .appendSuffix("d", " days")
          .appendSeparatorIfFieldsAfter(" ")
          .appendHours()
          .appendSuffix("h")
          .appendSeparatorIfFieldsAfter(" ")
          .appendMinutes()
          .appendSuffix("min")
          .toFormatter();

  private Clock clock = Clock.systemDefaultZone();

  @Value("${slack.client.id}")
  private String clientId;

  @Value("${slack.secret}")
  private String clientSecret;

  @Value("${slack.verification.token}")
  private String verificationToken;

  private @Autowired UserAppConfigManager userAppCfgMgr;
  private @Autowired SlackService slackService;
  private @Autowired ChatBotFunctionalityHandler chatBotFunctionalityHandler;
  @Autowired IPropertyHolder props;
  private @Autowired AnalyticsManager analyticsMgr;

  @GetMapping("/oauthUrl")
  @ResponseBody
  public AjaxReturnObject<String> oauthUrl() {
    var url =
        "https://slack.com/oauth/authorize?scope=incoming-webhook,commands,channels:history,users:read,files:read,groups:history,im:history,mpim:history&client_id="
            + this.clientId;
    return new AjaxReturnObject<>(url, null);
  }

  @GetMapping("/redirect_uri")
  public String handleSlackRedirect(
      @RequestParam Map<String, String> params, Model model, HttpSession session) {
    // param code or error
    if (params.containsKey(ERROR)) {
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg("Error connecting to Slack")
              .errorDetails(params.get(ERROR))
              .build();
      model.addAttribute(ERROR, error);
      return "connect/authorizationError";
    }

    String authorizationCode = params.get("code");
    try {
      String slackUrl =
          "https://slack.com/api/oauth.access?client_id="
              + clientId
              + "&client_secret="
              + clientSecret
              + "&code="
              + authorizationCode;
      String content = IOUtils.toString(new URL(slackUrl), StandardCharsets.UTF_8);
      model.addAttribute("slackResponse", content);
      log.info("slack response retrieved fine");

    } catch (IOException e) {
      log.warn("io exception on contacting oauth.access url", e);
      OauthAuthorizationError error =
          getAuthErrorBuilder()
              .errorMsg("exception during token exchange")
              .errorDetails(e.getMessage())
              .build();
      model.addAttribute(ERROR, error);
      return "connect/authorizationError";
    }

    return "connect/slack/connected";
  }

  private OauthAuthorizationErrorBuilder getAuthErrorBuilder() {
    return OauthAuthorizationError.builder().appName("Slack");
  }

  @PostMapping("/callbacks/search")
  @ResponseBody
  public SlackMessage basicSearch(@RequestParam Map<String, String> params) throws Exception {
    assertVerificationCode(params);

    User user;
    String searchTerm;

    try {
      // Get search term from Slack request
      searchTerm = params.get("text");
      // Show help if the search term is empty or 'help', even if user is not registered.
      if (searchTerm.isEmpty() || searchTerm.trim().equalsIgnoreCase("help")) {
        return getSearchHelpMessage();
      }
      // Get user associated with Slack user_id and team_id pair
      user = getUser(params.get(USER_ID), params.get(TEAM_ID));
      log.info(
          "Searching RSpace from Slack user {} in team {}",
          user.getUsername(),
          params.get(TEAM_ID));
      analyticsMgr.trackChatApp(user, "search", AnalyticsEvent.SLACK_USED);

      // Do the actual search
      ISearchResults<BaseRecord> docSearchResults =
          chatBotFunctionalityHandler.search(user, searchTerm);

      // Format results as a SlackMessage
      if (docSearchResults.getHits() == 0) {
        return new SlackMessage(getText("search.noresults"), null);
      } else {
        SlackMessage message = new SlackMessage("Found these documents:");
        for (BaseRecord doc : docSearchResults.getResults())
          message.addSlackAttachment(new SlackAttachment(props, doc));

        return message;
      }
    } catch (IllegalArgumentException | IllegalStateException e) {
      return new SlackMessage(e.getMessage(), null);
    }
  }

  private SlackMessage getSearchHelpMessage() {
    return new SlackMessage(getText("app.slack.search.help"));
  }

  @Data
  @AllArgsConstructor
  protected static class TimePeriod {
    long fromTimestampMillis;
    long toTimestampMillis;
  }

  private void checkTimePeriod(TimePeriod timePeriod) throws SlackCommandParseException {
    if (timePeriod == null) {
      throw new SlackCommandParseException(getText(SAVE_CONVO_ERROR_GENERIC_MSG));
    }

    long requestedDurationInMillis =
        timePeriod.getToTimestampMillis() - timePeriod.getFromTimestampMillis();

    if (requestedDurationInMillis > MAX_REQUESTED_DURATION_MILLIS) {
      throw new SlackCommandParseException(
          getText("apps.slack.saveconversation.maxtimeperiod", new String[] {"90"}));
    } else if (requestedDurationInMillis == 0) {
      throw new SlackCommandParseException(getText(SAVE_CONVO_ERROR_GENERIC_MSG));
    } else if (requestedDurationInMillis < 0) {
      throw new SlackCommandParseException(getText("errors.fromDateLaterThanToDate"));
    }
  }

  private long parseDate(String dateString, DateTimeZone dtz) {
    return ISODateTimeFormat.dateOptionalTimeParser()
        .withZone(dtz)
        .parseDateTime(dateString)
        .getMillis();
  }

  /**
   * Parses message and returns time interval. Accepts these formats: 1) * days (e.g. 5 days) 2) *d
   * *h *min (e.g. 1d 2h 3min) 3) from date 4) to date 5) from date to date
   *
   * <p>Date can be either YYYY-MM-DD or ISO8601.
   *
   * @param timeString
   * @return
   */
  protected TimePeriod parseTimeInterval(String timeString, DateTimeZone dtz)
      throws SlackCommandParseException {
    timeString = timeString.trim();

    TimePeriod timePeriod = null;
    long currentTime = clock.millis();

    if (!timeString.contains("from") && !timeString.contains("to")) {
      // Some duration until now (1, 2 formats)
      timePeriod =
          new TimePeriod(
              currentTime
                  - PERIOD_FORMATTER.parsePeriod(timeString).toStandardDuration().getMillis(),
              currentTime);
    } else {
      String[] tokens = timeString.split(" ");

      if (tokens.length == 2) {
        // Either "from date" or "to date" format
        if (tokens[0].equals("from")) {
          timePeriod = new TimePeriod(parseDate(tokens[1], dtz), currentTime);
        } else if (tokens[0].equals("to")) {
          timePeriod =
              new TimePeriod(
                  parseDate(tokens[1], dtz) - MAX_REQUESTED_DURATION_MILLIS,
                  parseDate(tokens[1], dtz));
        }
      } else if (tokens.length == 4 && tokens[0].equals("from") && tokens[2].equals("to")) {
        // "from date to date" format
        timePeriod = new TimePeriod(parseDate(tokens[1], dtz), parseDate(tokens[3], dtz));
      } else {
        throw new SlackCommandParseException(getText(SAVE_CONVO_ERROR_GENERIC_MSG));
      }
    }

    // Checks that the parsed time period is valid
    checkTimePeriod(timePeriod);

    return timePeriod;
  }

  @PostMapping("/callbacks/save_conversation")
  @ResponseBody
  public SlackMessage saveConversation(@RequestParam Map<String, String> params) throws Exception {
    assertVerificationCode(params);

    try {
      String timePeriodString = params.get("text");
      // Show help if the search term is empty or 'help'
      if (timePeriodString.trim().equalsIgnoreCase("help")) {
        return getSaveConversationHelp();
      }

      // Parse time period
      TimePeriod timePeriod = parseTimeInterval(timePeriodString, DateTimeZone.getDefault());

      // Get user associated with Slack user_id and team_id pair
      SlackUser slackUser = new SlackUser(params.get(USER_ID), params.get(TEAM_ID));
      String channelId = params.get("channel_id");
      String responseURI = params.get("response_url");

      User user = getUser(slackUser.getUserId(), slackUser.getTeamId());
      log.info(
          "Saving Slack conversation for user {} in team {}",
          user.getUsername(),
          params.get(TEAM_ID));
      analyticsMgr.trackChatApp(user, "save_conversation", AnalyticsEvent.SLACK_USED);

      SecurityUtils.getSubject()
          .login(
              new SlackAuthToken(
                  user.getUsername(),
                  String.format("%s.%s", slackUser.getUserId(), slackUser.getTeamId())));

      String accessToken = getAccessToken(user, params.get(USER_ID), params.get(TEAM_ID));
      if (accessToken == null) {
        throw new IllegalStateException(getText("apps.slack.error.noAccessToken"));
      }

      slackService.saveConversation(
          accessToken,
          channelId,
          new URI(responseURI),
          ((double) timePeriod.getFromTimestampMillis()) / 1000,
          ((double) timePeriod.getToTimestampMillis()) / 1000,
          user,
          slackUser,
          EXPORT_MESSAGES_DOCUMENT_NAME);

      return new SlackMessage(
          getText("apps.slack.saveconversation.ok", new String[] {EXPORT_MESSAGES_DOCUMENT_NAME}));
    } catch (IllegalArgumentException | IllegalStateException | SlackCommandParseException e) {
      return new SlackMessage(e.getMessage());
    }
  }

  private void assertVerificationCode(Map<String, String> params) {
    if (!params.get("token").equals(verificationToken)) {
      throw new IllegalArgumentException(getText("apps.slack.error.verificationCodeMismatch"));
    }
  }

  private SlackMessage getSaveConversationHelp() {
    return new SlackMessage(getText("app.slack.saveconversation.help"));
  }

  private String getAccessToken(User user, String userId, String teamId) {
    for (AppConfigElementSet elementSet :
        userAppCfgMgr.getByAppName("app.slack", user).getAppConfigElementSets()) {
      String currentUserId = elementSet.findElementByPropertyName("SLACK_USER_ID").getValue();
      String currentTeamId = elementSet.findElementByPropertyName("SLACK_TEAM_ID").getValue();
      String accessToken =
          elementSet.findElementByPropertyName("SLACK_USER_ACCESS_TOKEN").getValue();

      if (currentUserId.equals(userId) && currentTeamId.equals(teamId) && !accessToken.isEmpty())
        return accessToken;
    }
    return null;
  }

  public User getUser(String slackUserId, String slackTeamId) {
    List<User> users =
        ListUtils.intersection(
            userAppCfgMgr.findByAppConfigValue("SLACK_USER_ID", slackUserId),
            userAppCfgMgr.findByAppConfigValue("SLACK_TEAM_ID", slackTeamId));

    if (users.isEmpty()) {
      throw new IllegalStateException(getText("apps.slack.error.noconnecteduser"));
    } else if (users.size() > 1) {
      throw new IllegalStateException(
          getText(
              "apps.slack.error.tooManyconnectedusers",
              new String[] {users.get(0).getUsername(), users.get(1).getUsername()}));

    } else {
      return users.get(0);
    }
  }

  /* ==============
   *  for tests
   * ============== */
  protected void setVerificationToken(String verificationToken) {
    this.verificationToken = verificationToken;
  }

  protected void setUserAppCfgMgr(UserAppConfigManager userAppCfgMgr) {
    this.userAppCfgMgr = userAppCfgMgr;
  }

  protected void setClock(Clock clock) {
    this.clock = clock;
  }
}
