package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Entity;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.NaturalIdCache;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Fast unit test (no Spring context, no DB) asserting every {@code @Cache}-annotated entity,
 * collection association and natural-id has a matching {@code <cache alias=...>} region in {@code
 * ehcache.xml}. Hibernate 6 names an entity region after the root entity of the hierarchy, a
 * collection region after its role ({@code <declaringEntityFQN>.<property>}), and a natural-id
 * region {@code <rootEntityFQN>##NaturalId}, unless an explicit {@code region} attribute is set.
 *
 * <p>This guard exists because the drift is otherwise invisible: the test {@code SessionFactory}
 * disables the second-level cache, and at runtime {@code missing_cache_strategy=create-warn}
 * silently substitutes an untuned default region for a misnamed or absent one.
 */
public class EhcacheRegionConfigTest {

  private static final String ENTITY_MODEL_BASE_PACKAGE = "com.researchspace.model";
  private static final String EHCACHE_RESOURCE = "ehcache.xml";
  private static final String DAO_CONTEXT_RESOURCE = "applicationContext-dao.xml";
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

  @Test
  public void everyCacheAnnotatedAssociationHasMatchingEhcacheRegion() throws IOException {
    Set<String> definedRegions = readEhcacheAliases();

    // region name -> declaration site, for diagnostics
    TreeMap<String, String> expectedRegionToDeclarer = new TreeMap<>();
    for (Class<?> entity : findAnnotatedClasses(Entity.class)) {
      // Walk up the hierarchy so @Cache on @MappedSuperclass properties is seen. A property
      // declared on a mapped superclass is re-mapped per entity subclass, so its collection
      // role (and default region) uses the concrete entity's name; a property declared on an
      // @Entity keeps that entity's name.
      for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
        String roleOwner = c.isAnnotationPresent(Entity.class) ? c.getName() : entity.getName();
        for (Field field : c.getDeclaredFields()) {
          Cache cache = field.getAnnotation(Cache.class);
          if (cache != null) {
            String region =
                cache.region().isEmpty() ? roleOwner + "." + field.getName() : cache.region();
            expectedRegionToDeclarer.put(region, c.getName() + "." + field.getName());
          }
        }
        for (Method method : c.getDeclaredMethods()) {
          Cache cache = method.getAnnotation(Cache.class);
          if (cache != null) {
            String region =
                cache.region().isEmpty() ? roleOwner + "." + propertyName(method) : cache.region();
            expectedRegionToDeclarer.put(region, c.getName() + "." + method.getName());
          }
        }
      }
    }
    for (Class<?> naturalIdCached : findAnnotatedClasses(NaturalIdCache.class)) {
      NaturalIdCache annotation = naturalIdCached.getAnnotation(NaturalIdCache.class);
      String region =
          annotation.region().isEmpty()
              ? naturalIdCached.getName() + "##NaturalId"
              : annotation.region();
      expectedRegionToDeclarer.put(region, naturalIdCached.getName());
    }

    TreeSet<String> missing = new TreeSet<>(expectedRegionToDeclarer.keySet());
    missing.removeAll(definedRegions);

    assertTrue(
        missing.isEmpty(),
        () ->
            "ehcache.xml is missing region(s) for @Cache-annotated collection associations or "
                + "natural ids. create-warn would silently substitute an untuned region. Add a "
                + "<cache alias=\"...\"> for each:\n"
                + missing.stream()
                    .map(
                        r -> "  - " + r + "  (declared on " + expectedRegionToDeclarer.get(r) + ")")
                    .collect(Collectors.joining("\n")));
  }

  /**
   * The missing-region strategy must be a deliberate, visible choice: unset, hibernate-jcache
   * defaults to create-warn silently.
   */
  @Test
  public void missingCacheStrategyIsDeclaredExplicitly() throws IOException {
    // Strip XML comments first: a comment quoting the assignment must not satisfy the check.
    String activeContent = readProductionDaoContext().replaceAll("(?s)<!--.*?-->", "");
    Pattern activeProperty =
        Pattern.compile(
            "(?m)^\\s*hibernate\\.javax\\.cache\\.missing_cache_strategy=create-warn\\s*$");
    assertTrue(
        activeProperty.matcher(activeContent).find(),
        DAO_CONTEXT_RESOURCE
            + " must declare hibernate.javax.cache.missing_cache_strategy=create-warn as an "
            + "active property line, not just in a comment; if the strategy is being changed "
            + "deliberately, update this test with it");
  }

  /**
   * src/test/resources ships its own applicationContext-dao.xml (with L2 disabled) that shadows the
   * production file on the test classpath, so pick the copy that is not under test-classes.
   */
  private String readProductionDaoContext() throws IOException {
    var resources = getClass().getClassLoader().getResources(DAO_CONTEXT_RESOURCE);
    while (resources.hasMoreElements()) {
      java.net.URL url = resources.nextElement();
      if (!url.getPath().contains("test-classes")) {
        try (InputStream in = url.openStream()) {
          return IOUtils.toString(in, StandardCharsets.UTF_8);
        }
      }
    }
    throw new IllegalStateException(
        "production " + DAO_CONTEXT_RESOURCE + " not found on the test classpath");
  }

  private String propertyName(Method getter) {
    String name = getter.getName();
    if (name.startsWith("get") && name.length() > 3) {
      return Character.toLowerCase(name.charAt(3)) + name.substring(4);
    }
    if (name.startsWith("is") && name.length() > 2) {
      return Character.toLowerCase(name.charAt(2)) + name.substring(3);
    }
    return name;
  }

  private Set<Class<?>> findCacheAnnotatedEntities() {
    return findAnnotatedClasses(Cache.class);
  }

  private Set<Class<?>> findAnnotatedClasses(
      Class<? extends java.lang.annotation.Annotation> annotation) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
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
    String xml = readClasspathResource(EHCACHE_RESOURCE);
    Set<String> aliases = new TreeSet<>();
    Matcher m = ALIAS_PATTERN.matcher(xml);
    while (m.find()) {
      aliases.add(m.group(1));
    }
    return aliases;
  }

  private String readClasspathResource(String resource) throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException(resource + " not found on the test classpath");
      }
      return IOUtils.toString(in, StandardCharsets.UTF_8);
    }
  }
}
