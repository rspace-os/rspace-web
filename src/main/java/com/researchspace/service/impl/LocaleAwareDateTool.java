package com.researchspace.service.impl;

import java.util.Locale;
import org.apache.velocity.tools.generic.DateTool;

public class LocaleAwareDateTool extends DateTool {

  public LocaleAwareDateTool(Locale locale) {
    setLocale(locale);
  }
}
