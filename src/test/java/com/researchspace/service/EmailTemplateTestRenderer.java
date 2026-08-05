package com.researchspace.service;

import com.researchspace.service.impl.EmailHtmlToPlainText;
import java.util.HashMap;
import java.util.Map;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.spring.VelocityEngineUtils;

final class EmailTemplateTestRenderer {

  private final VelocityEngine velocity = new VelocityEngine();
  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
  private final String templateDirectory;

  EmailTemplateTestRenderer(String templateDirectory) {
    this.templateDirectory = templateDirectory;
    velocity.setProperty("resource.loaders", "class");
    velocity.setProperty(
        "resource.loader.class.class",
        "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
    velocity.setProperty("velocimacro.library", "velocityTemplates/VM_global_library.vm");
    velocity.init();
  }

  String render(String template, Map<String, Object> model) {
    Map<String, Object> context = new HashMap<>(model);
    context.put("msg", messages);
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, "velocityTemplates/" + templateDirectory + template, "UTF-8", context);
  }

  String renderPlainText(String template, Map<String, Object> model) {
    return EmailHtmlToPlainText.toPlainText(render(template, model));
  }
}
