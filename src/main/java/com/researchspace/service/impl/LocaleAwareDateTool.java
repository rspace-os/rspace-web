package com.researchspace.service.impl;

import java.util.Locale;
import org.apache.velocity.tools.generic.DateTool;

/** A {@link DateTool} with a caller-supplied locale. */
public class LocaleAwareDateTool extends DateTool {

  public LocaleAwareDateTool(Locale locale) {
    setLocale(locale);
  }
}
