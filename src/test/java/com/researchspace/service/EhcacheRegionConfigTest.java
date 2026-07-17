package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.hibernate.annotations.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Fast unit test (no Spring context, no DB) asserting every {@code @Cache}-annotated entity has a
 * matching {@code <cache alias=...>} region in {@code ehcache.xml}. Hibernate 6 names the region
 * after the root entity of the hierarchy (or the explicit {@code region} attribute).
 *
 * <p>This guard exists because the drift is otherwise invisible: the test {@code SessionFactory}
 * disables the second-level cache, and at runtime {@code missing_cache_strategy=create-warn}
 * silently substitutes an untuned default region for a misnamed or absent one.
 */
public class EhcacheRegionConfigTest {

  private static final String ENTITY_MODEL_BASE_PACKAGE = "com.researchspace.model";
  private static final String EHCACHE_RESOURCE = "ehcache.xml";
  private static final Pattern ALIAS_PATTERN = Pattern.compile("alias=\"([^\"]+)\"");

  @Test
  public void everyCacheAnnotatedEntityHasMatchingEhcacheRegion() throws IOException {
    Set<String> definedRegions = readEhcacheAliases();

    // entity FQN (or @Cache region attr) -> the class that declares @Cache, for diagnostics
    TreeMap<String, String> expectedRegionToDeclarer = new TreeMap<>();
    for (Class<?> cached : findCacheAnnotatedEntities()) {
      Cache cache = cached.getAnnotation(Cache.class);
      String region =
          (cache != null && !cache.region().isEmpty()) ? cache.region() : cached.getName();
      expectedRegionToDeclarer.put(region, cached.getName());
    }

    TreeSet<String> missing = new TreeSet<>(expectedRegionToDeclarer.keySet());
    missing.removeAll(definedRegions);

    assertTrue(
        missing.isEmpty(),
        () ->
            "ehcache.xml is missing region(s) for @Cache-annotated entities. Hibernate 6 names "
                + "the L2 region after the root entity, and create-warn would silently substitute "
                + "an untuned region. Add a <cache alias=\"...\"> for each:\n"
                + missing.stream()
                    .map(
                        r -> "  - " + r + "  (declared on " + expectedRegionToDeclarer.get(r) + ")")
                    .collect(Collectors.joining("\n")));
  }

  private Set<Class<?>> findCacheAnnotatedEntities() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Cache.class));
    return scanner.findCandidateComponents(ENTITY_MODEL_BASE_PACKAGE).stream()
        .map(
            bd -> {
              try {
                return Class.forName(bd.getBeanClassName());
              } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Cannot load scanned entity " + bd, e);
              }
            })
        .collect(Collectors.toSet());
  }

  private Set<String> readEhcacheAliases() throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(EHCACHE_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(EHCACHE_RESOURCE + " not found on the test classpath");
      }
      String xml = IOUtils.toString(in, StandardCharsets.UTF_8);
      Set<String> aliases = new TreeSet<>();
      Matcher m = ALIAS_PATTERN.matcher(xml);
      while (m.find()) {
        aliases.add(m.group(1));
      }
      return aliases;
    }
  }
}
