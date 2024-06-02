package com.researchspace.service.impl;

import static org.apache.commons.lang.StringUtils.replace;

import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.velocity.VelocityEngineUtils;

/**
 * StrictEmailContentGenerator generates optional plain-text and HTML template. <br>
 * It will generate a plain-text email content if there is an alternative Velocity template suffixed
 * '-plaintext.vm'
 */
@Slf4j
public class StrictEmailContentGenerator {

  private @Autowired VelocityEngine velocity;

  /**
   * Generates an EmailContent that must contain an HTML message and will optionally contain a text
   * alternative if a template 'template-plaintext.vm' alternative exists.
   *
   * @param htmlTemplate HTML template name, e.g. 'email-content.vm'
   * @param velocityModel
   * @return EmailContent with optional text-variant
   */
  public EmailContent generatePlainTextAndHtmlContent(
      String htmlTemplate, Map<String, Object> velocityModel) {
    String plainTextTemplate = String.format("%s-plaintext.vm", replace(htmlTemplate, ".vm", ""));
    String msgHTML = mergeTemplate(htmlTemplate, velocityModel);

    String msgPlain = null;
    try {
      msgPlain = mergeTemplate(plainTextTemplate, velocityModel);
    } catch (ResourceNotFoundException e) {
      log.warn("Plain-text variant of HTML Template could not be found : {}", e.getMessage());
    }
    return EmailContent.builder().htmlContent(msgHTML).plainTextContent(msgPlain).build();
  }

  private String mergeTemplate(String template, Map<String, Object> velocityModel) {
    return VelocityEngineUtils.mergeTemplateIntoString(velocity, template, "UTF-8", velocityModel);
  }
}
