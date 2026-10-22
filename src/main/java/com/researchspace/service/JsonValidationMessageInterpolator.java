package com.researchspace.service;

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.beanvalidation.MessageSourceResourceBundleLocator;

/** Resolves JSON validation phrases for default factories, including ORM validation. */
public class JsonValidationMessageInterpolator extends ResourceBundleMessageInterpolator {
  public JsonValidationMessageInterpolator() {
    super(new MessageSourceResourceBundleLocator(new JsonMessageSource()));
  }

  @Override
  public String interpolate(String messageTemplate, Context context) {
    return super.interpolate(messageTemplate, context, LocaleContextHolder.getLocale());
  }
}
