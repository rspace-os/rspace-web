package com.researchspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.icu.text.MessageFormat;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ObjectUtils;

/**
 * Message source backed by the i18next JSON files shared with the frontend.
 *
 * <p>Server keys are flattened with dots. Frontend keys retain their i18next namespace, such as
 * {@code common:actions.add}. ICU {@link MessageFormat} supports plural and select patterns that
 * Java's {@link java.text.MessageFormat} cannot parse.
 */
public class JsonMessageSource extends AbstractMessageSource {

  private static final Logger log = LoggerFactory.getLogger(JsonMessageSource.class);

  private static final String RESOURCE_PATTERN = "classpath*:i18n/locales/**/*.json";

  private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("en-US");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

  private final Map<String, Map<String, String>> messagesByLocale = load();

  @Override
  protected java.text.MessageFormat resolveCode(String code, Locale locale) {
    return null;
  }

  @Override
  protected String getMessageInternal(String code, Object[] args, Locale locale) {
    if (code == null) {
      return null;
    }
    Locale localeToUse = locale != null ? locale : Locale.getDefault();

    String pattern = lookup(code, localeToUse);
    if (pattern != null) {
      if (!isAlwaysUseMessageFormat() && ObjectUtils.isEmpty(args)) {
        // Preserve literal single quotes when no formatting is needed.
        return pattern;
      }
      return new MessageFormat(pattern, localeToUse).format(resolveArguments(args, localeToUse));
    }
    return getMessageFromParent(code, args, localeToUse);
  }

  private String lookup(String code, Locale locale) {
    String exact = byLocale(locale).get(code);
    if (exact != null) {
      return exact;
    }
    String language = byLocale(Locale.forLanguageTag(locale.getLanguage())).get(code);
    if (language != null) {
      return language;
    }
    return byLocale(DEFAULT_LOCALE).get(code);
  }

  private Map<String, String> byLocale(Locale locale) {
    return messagesByLocale.getOrDefault(locale.toLanguageTag(), Map.of());
  }

  private Map<String, Map<String, String>> load() {
    Map<String, Map<String, String>> result = new HashMap<>();
    try {
      Resource[] resources = resolver.getResources(RESOURCE_PATTERN);
      for (Resource resource : resources) {
        String locale = localeOf(resource);
        if (locale == null) {
          continue;
        }
        try (InputStream in = resource.getInputStream()) {
          JsonNode root = objectMapper.readTree(in);
          Map<String, String> target = result.computeIfAbsent(locale, k -> new HashMap<>());
          flatten(prefixFor(resource), root, target);
        } catch (IOException e) {
          log.error("Could not read phrase file {}", resource.getDescription(), e);
        }
      }
    } catch (IOException e) {
      log.error("Could not scan phrase files matching {}", RESOURCE_PATTERN, e);
    }
    log.info(
        "Loaded JSON phrases for locales {} ({} keys total)",
        result.keySet(),
        result.values().stream().mapToInt(Map::size).sum());
    return result;
  }

  private String localeOf(Resource resource) {
    try {
      String path = resource.getURL().getPath();
      String[] segments = path.split("/");
      if (segments.length < 2) {
        return null;
      }
      return segments[segments.length - 2];
    } catch (IOException e) {
      log.warn("Could not determine locale for resource {}", resource.getDescription(), e);
      return null;
    }
  }

  private String prefixFor(Resource resource) {
    String filename = resource.getFilename();
    if (filename == null || filename.startsWith("server.")) {
      return "";
    }
    int extension = filename.lastIndexOf('.');
    return (extension == -1 ? filename : filename.substring(0, extension)) + ":";
  }

  private void flatten(String prefix, JsonNode node, Map<String, String> target) {
    if (node.isObject()) {
      Iterator<Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Entry<String, JsonNode> field = fields.next();
        String separator = prefix.endsWith(":") ? "" : ".";
        String key = prefix.isEmpty() ? field.getKey() : prefix + separator + field.getKey();
        flatten(key, field.getValue(), target);
      }
    } else if (node.isValueNode() && !prefix.isEmpty()) {
      target.put(prefix, node.asText());
    }
  }
}
