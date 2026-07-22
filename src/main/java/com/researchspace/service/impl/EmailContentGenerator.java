package com.researchspace.service.impl;

import com.researchspace.service.EmailContent;
import com.researchspace.service.LocaleBoundMessages;
import com.researchspace.service.MessageSourceUtils;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.spring.VelocityEngineUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;

/** Renders an HTML email and derives its plain-text alternative from the rendered body. */
public class EmailContentGenerator {

  private @Autowired VelocityEngine velocity;
  private @Autowired MessageSourceUtils messages;

  /**
   * Renders an email whose subject is resolved from an i18n message key, so callers pass a
   * translation key rather than an already-formatted subject string. The resolved subject travels
   * on the returned {@link EmailContent#subject()}.
   *
   * @param subjectKey i18n key for the email subject
   * @param subjectArgs MessageFormat arguments for the subject, or null
   */
  public EmailContent render(
      String subjectKey, String htmlTemplate, Map<String, Object> velocityModel) {
    return render(subjectKey, null, htmlTemplate, velocityModel);
  }

  public EmailContent render(
      String subjectKey,
      Object[] subjectArgs,
      String htmlTemplate,
      Map<String, Object> velocityModel) {
    Locale locale = LocaleContextHolder.getLocale();
    String subject = messages.getMessage(subjectKey, subjectArgs, locale);
    return fromHtmlFragment(subject, mergeTemplate(htmlTemplate, velocityModel, locale), locale);
  }

  EmailContent fromHtmlFragment(String subject, String htmlFragment, Locale locale) {
    String html =
        "<html lang=\"%s\">\n<body>\n%s\n</body>\n</html>"
            .formatted(locale.toLanguageTag(), htmlFragment.strip());
    return new EmailContent(subject, html, EmailHtmlToPlainText.toPlainText(html));
  }

  private String mergeTemplate(
      String template, Map<String, Object> velocityModel, Locale recipientLocale) {
    Map<String, Object> model = new HashMap<>(velocityModel);
    model.putIfAbsent("msg", new LocaleBoundMessages(messages, recipientLocale));
    return VelocityEngineUtils.mergeTemplateIntoString(velocity, template, "UTF-8", model);
  }
}
