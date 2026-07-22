package com.axiope.model.record.init;

import com.researchspace.service.JsonMessageSource;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;

/** Exposes JSON phrases through the {@link ResourceBundle} API used by built-in content. */
public final class BuiltinContentMessages {

  private static final JsonMessageSource MESSAGE_SOURCE = new JsonMessageSource();

  private BuiltinContentMessages() {}

  public static ResourceBundle forLocale(Locale locale) {
    return new ResourceBundle() {
      @Override
      protected Object handleGetObject(String key) {
        return MESSAGE_SOURCE.getMessage(key, null, null, locale);
      }

      @Override
      public Enumeration<String> getKeys() {
        return Collections.emptyEnumeration();
      }
    };
  }
}
