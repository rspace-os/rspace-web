package com.axiope.webapp.taglib;

import static org.joda.time.format.DateTimeFormat.shortTime;

import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.session.SessionAttributeUtils;
import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.TagSupport;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Formats a date for UI display to show 'Today at time', 'Yesterday at time' . Note since 1.54, if
 * input date is null, then an empty string is returned.
 */
public class RelativeDateTag extends TagSupport {

  private static final Logger LOG = LoggerFactory.getLogger(RelativeDateTag.class.getName());
  private static final long serialVersionUID = -8491659018947980799L;
  private static final MessageSourceUtils DEFAULT_MESSAGES =
      new MessageSourceUtils(new JsonMessageSource());

  private Date input;

  private int relativeForNDays = 1;

  private DateTime nowTime; // package scoped for testing

  /**
   * @throws JspException if template or action is null
   */
  public int doStartTag() throws JspException {
    try {
      // Get the writer object for output.
      JspWriter out = pageContext.getOut();

      // Perform substr operation on string.
      out.println(getString());

    } catch (IOException e) {
      LOG.error("Error writing start tag: ", e);
    }
    return SKIP_BODY;
  }

  String getString() {
    Object tzOb = null;
    if (input == null) {
      return "";
    }
    if (pageContext != null) {
      tzOb = pageContext.getSession().getAttribute(SessionAttributeUtils.TIMEZONE);
    }

    DateTime inputDt = new DateTime(input, DateTimeZone.UTC);
    DateTime nowDate = nowTime;
    if (nowDate == null) {
      nowDate = DateTime.now(DateTimeZone.UTC);
    }

    if (tzOb != null) {
      TimeZone tz = (TimeZone) tzOb;
      if (!tz.hasSameRules(TimeZone.getTimeZone("UTC"))) {
        DateTimeZone dtz = DateTimeZone.forTimeZone(tz);
        nowDate = nowDate.toDateTime(dtz);
        inputDt = inputDt.toDateTime(dtz);
      }
    }

    if (inputDt.toLocalDate().isEqual(nowDate.toLocalDate())) {
      return getMessage("relativeDate.today", shortTime().print(inputDt));
    }

    DateTime yesterday = nowDate.dayOfYear().addToCopy(-1);
    if (inputDt.dayOfYear().get() == yesterday.dayOfYear().get()
        && inputDt.year().get() == yesterday.year().get()) {
      return getMessage("relativeDate.yesterday", shortTime().print(inputDt));
    }

    if (inputDt.dayOfYear().getDifference(nowDate) * -1 < relativeForNDays) {
      int daysAgo = inputDt.dayOfYear().getDifference(nowDate) * -1;
      return getMessage("relativeDate.daysAgo", daysAgo, shortTime().print(inputDt));
    } else {
      DateTimeFormatter df = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm");

      return df.print(inputDt);
    }
  }

  private String getMessage(String key, Object... args) {
    if (pageContext == null) {
      return DEFAULT_MESSAGES.getMessage(key, args);
    }
    HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
    WebApplicationContext context =
        RequestContextUtils.findWebApplicationContext(request, pageContext.getServletContext());
    return context
        .getBean(MessageSourceUtils.class)
        .getMessage(key, args, RequestContextUtils.getLocale(request));
  }

  /**
   * The date to be compared
   *
   * @return
   */
  public Date getInput() {
    return input;
  }

  public void setInput(Date input) {
    this.input = input;
  }

  /**
   * The number of days difference between now and the input date, after which the display will use
   * the standard date/time display rather than the 'n days ago' syntax. This is optional
   *
   * @return
   */
  public int getRelativeForNDays() {
    return relativeForNDays;
  }

  public void setRelativeForNDays(int relativeForNDays) {
    this.relativeForNDays = relativeForNDays;
  }

  public DateTime getNowTime() {
    return nowTime;
  }

  public void setNowTime(DateTime nowTime) {
    this.nowTime = nowTime;
  }
}
