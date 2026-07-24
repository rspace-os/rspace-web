package com.researchspace.service;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.User;
import java.util.Locale;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class UserLocaleServiceTest {

  private final UserLocaleService service = new UserLocaleService();

  @Test
  public void defaultsToEnUsWhenNoLocaleIsConfigured() {
    assertEquals(Locale.forLanguageTag("en-US"), service.getLocale());
    assertEquals(Locale.forLanguageTag("en-US"), service.getLocaleFor(new User("someUser")));
    assertEquals(Locale.forLanguageTag("en-US"), service.getLocaleFor(null));
  }

  @Test
  public void usesTheConfiguredLocaleForBothRequestAndUserLookups() {
    ReflectionTestUtils.setField(service, "configuredLocaleTag", "fr-FR");

    assertEquals(Locale.forLanguageTag("fr-FR"), service.getLocale());
    assertEquals(Locale.forLanguageTag("fr-FR"), service.getLocaleFor(new User("someUser")));
  }

  @Test
  public void fallsBackToEnUsWhenTheConfiguredLocaleIsInvalid() {
    ReflectionTestUtils.setField(service, "configuredLocaleTag", "not a locale!!");

    assertEquals(Locale.forLanguageTag("en-US"), service.getLocale());
  }
}
