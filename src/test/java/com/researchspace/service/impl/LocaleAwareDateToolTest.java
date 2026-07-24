package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.Locale;
import org.junit.Test;

public class LocaleAwareDateToolTest {

  @Test
  public void formatsUsingTheBoundLocaleRegardlessOfDefault() {
    Locale originalDefault = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMAN);
      LocaleAwareDateTool dateTool = new LocaleAwareDateTool(Locale.US);
      String formatted = dateTool.format("MMMM", new Date(0));
      assertEquals("January", formatted);
    } finally {
      Locale.setDefault(originalDefault);
    }
  }
}
