package com.axiope.webapp.taglib;

import com.researchspace.model.comms.NotificationType;
import com.researchspace.service.NotificationTypeMessages;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.springframework.context.MessageSource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Writes the localized display label for a {@link NotificationType}.
 *
 * <p>This tag is a bridge for the JSP-rendered notifications dropdown ({@code
 * notifications_ajax.jsp}) only. When the notifications listing migrates to React, delete this tag
 * (and its {@code rs.tld} entry) and return the raw enum in the JSON payload. {@link
 * NotificationTypeMessages} must stay either way: the email pipeline also resolves these labels
 * server-side.
 */
public class NotificationTypeLabelTag extends TagSupport {

  private static final long serialVersionUID = 1L;

  private NotificationType notificationType;
  private String var;

  public void setNotificationType(NotificationType notificationType) {
    this.notificationType = notificationType;
  }

  public void setVar(String var) {
    this.var = var;
  }

  @Override
  public int doStartTag() throws JspException {
    HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
    WebApplicationContext ctx =
        RequestContextUtils.findWebApplicationContext(request, pageContext.getServletContext());
    MessageSource messageSource = ctx.getBean("messageSource", MessageSource.class);
    String label =
        messageSource.getMessage(
            NotificationTypeMessages.keyFor(notificationType),
            null,
            RequestContextUtils.getLocale(request));
    pageContext.setAttribute(var, label);
    return SKIP_BODY;
  }
}
