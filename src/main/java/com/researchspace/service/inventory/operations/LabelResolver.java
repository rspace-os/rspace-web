package com.researchspace.service.inventory.operations;

import com.ibm.icu.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;

/** Resolves an i18n key to a display name, interpolating args as ICU MessageFormat. */
@FunctionalInterface
public interface LabelResolver {

  String resolve(String key, Map<String, Object> args);

  default String resolve(String key) {
    return resolve(key, Map.of());
  }

  /**
   * Reads the pattern from the {@code inventory:} catalog, falling back to the key itself when
   * missing, and ICU-formats it only when args are supplied - an unconditional format() would
   * mangle a literal apostrophe in an argument-free name, since ICU MessageFormat treats it as an
   * escape character.
   */
  static LabelResolver fromMessageSource(MessageSource messages, Locale locale) {
    return (key, args) -> {
      String pattern = messages.getMessage("inventory:" + key, null, "inventory:" + key, locale);
      if (args == null || args.isEmpty()) {
        return pattern;
      }
      return new MessageFormat(pattern, locale).format(args);
    };
  }
}
