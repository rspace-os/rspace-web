package com.researchspace.service;

import com.researchspace.model.User;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Resolves the configured application locale, with a seam for future per-user locales. */
@Slf4j
@Service
public class UserLocaleService {

  private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("en-US");

  @Value("${deployment.experimental.locale:}")
  private String configuredLocaleTag;

  public Locale getLocale() {
    return resolveConfiguredLocale();
  }

  public Locale getLocaleFor(User user) {
    return getLocale();
  }

  private Locale resolveConfiguredLocale() {
    if (StringUtils.isBlank(configuredLocaleTag)) {
      return DEFAULT_LOCALE;
    }
    Locale parsed = Locale.forLanguageTag(configuredLocaleTag.trim());
    if (parsed.getLanguage().isEmpty()) {
      log.warn(
          "Ignoring invalid deployment.experimental.locale value [{}]; falling back to {}",
          configuredLocaleTag,
          DEFAULT_LOCALE);
      return DEFAULT_LOCALE;
    }
    return parsed;
  }
}
