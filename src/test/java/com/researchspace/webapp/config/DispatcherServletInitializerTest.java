package com.researchspace.webapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletRegistration;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.DispatcherServlet;

class DispatcherServletInitializerTest {

  private DispatcherServletInitializer initializer;
  private String savedMaxUploadSize;
  private String savedPropertyFileDir;

  @BeforeEach
  void setUp() {
    initializer = new DispatcherServletInitializer();
    savedMaxUploadSize = System.getProperty(DispatcherServletInitializer.PROPERTY_NAME);
    savedPropertyFileDir = System.getProperty(DispatcherServletInitializer.PROPERTY_FILE_DIR);
    System.clearProperty(DispatcherServletInitializer.PROPERTY_NAME);
    System.clearProperty(DispatcherServletInitializer.PROPERTY_FILE_DIR);
  }

  @AfterEach
  void tearDown() {
    restore(DispatcherServletInitializer.PROPERTY_NAME, savedMaxUploadSize);
    restore(DispatcherServletInitializer.PROPERTY_FILE_DIR, savedPropertyFileDir);
  }

  private void restore(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  @Test
  void defaultWhenNothingSet() {
    assertEquals(
        DispatcherServletInitializer.DEFAULT_MAX_FILE_SIZE, initializer.resolveMaxFileSize());
  }

  @Test
  void jvmSystemPropertyWins() {
    System.setProperty(DispatcherServletInitializer.PROPERTY_NAME, "12345");
    assertEquals(12345L, initializer.resolveMaxFileSize());
  }

  @Test
  void readsFromDeploymentPropertiesWhenSysPropAbsent(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("deployment.properties");
    Files.writeString(file, "files.maxUploadSize=67890\n");
    System.setProperty(DispatcherServletInitializer.PROPERTY_FILE_DIR, tmp.toString());

    assertEquals(67890L, initializer.resolveMaxFileSize());
  }

  @Test
  void sysPropTakesPrecedenceOverDeploymentProperties(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("deployment.properties");
    Files.writeString(file, "files.maxUploadSize=67890\n");
    System.setProperty(DispatcherServletInitializer.PROPERTY_FILE_DIR, tmp.toString());
    System.setProperty(DispatcherServletInitializer.PROPERTY_NAME, "11111");

    assertEquals(11111L, initializer.resolveMaxFileSize());
  }

  @Test
  void fallsBackToDefaultWhenPropertyFileDirMissingFile(@TempDir Path tmp) {
    System.setProperty(DispatcherServletInitializer.PROPERTY_FILE_DIR, tmp.toString());
    assertEquals(
        DispatcherServletInitializer.DEFAULT_MAX_FILE_SIZE, initializer.resolveMaxFileSize());
  }

  @Test
  void fallsBackToDefaultWhenDeploymentPropertiesHasNoKey(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("deployment.properties");
    Files.writeString(file, "some.other.property=foo\n");
    System.setProperty(DispatcherServletInitializer.PROPERTY_FILE_DIR, tmp.toString());

    assertEquals(
        DispatcherServletInitializer.DEFAULT_MAX_FILE_SIZE, initializer.resolveMaxFileSize());
  }

  @Test
  void readsFromDeploymentPropertiesWithFilePrefix(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("deployment.properties");
    Files.writeString(file, "files.maxUploadSize=42424242\n");
    System.setProperty(DispatcherServletInitializer.PROPERTY_FILE_DIR, "file:" + tmp);

    assertEquals(42424242L, initializer.resolveMaxFileSize());
  }

  @Test
  void ignoresNonNumericSysProp() {
    System.setProperty(DispatcherServletInitializer.PROPERTY_NAME, "not-a-number");
    assertEquals(
        DispatcherServletInitializer.DEFAULT_MAX_FILE_SIZE, initializer.resolveMaxFileSize());
  }

  @Test
  void ignoresNonPositiveValue() {
    System.setProperty(DispatcherServletInitializer.PROPERTY_NAME, "0");
    assertEquals(
        DispatcherServletInitializer.DEFAULT_MAX_FILE_SIZE, initializer.resolveMaxFileSize());
  }

  @Test
  void contextInitializedRegistersDispatcherWithMappingsAndMultipartConfig() {
    // Pin the resolved size so the multipart assertions are deterministic.
    System.setProperty(DispatcherServletInitializer.PROPERTY_NAME, "12345");

    ServletRegistration.Dynamic dynamic = mock(ServletRegistration.Dynamic.class);
    ServletContext ctx = mock(ServletContext.class);
    when(ctx.addServlet(eq("dispatcher"), any(DispatcherServlet.class))).thenReturn(dynamic);
    ServletContextEvent sce = mock(ServletContextEvent.class);
    when(sce.getServletContext()).thenReturn(ctx);

    initializer.contextInitialized(sce);

    verify(ctx).addServlet(eq("dispatcher"), any(DispatcherServlet.class));
    verify(dynamic).setLoadOnStartup(1);
    verify(dynamic).setAsyncSupported(true);
    verify(dynamic).addMapping("/app/*");
    verify(dynamic).addMapping(DispatcherServletInitializer.MULTIPART_SOURCE_PATTERNS);

    ArgumentCaptor<MultipartConfigElement> multipart =
        ArgumentCaptor.forClass(MultipartConfigElement.class);
    verify(dynamic).setMultipartConfig(multipart.capture());
    MultipartConfigElement config = multipart.getValue();
    assertEquals(12345L, config.getMaxFileSize());
    // Request size is twice the per-file limit (a multipart request may carry several files).
    assertEquals(24690L, config.getMaxRequestSize());
    assertEquals(0, config.getFileSizeThreshold());
    assertEquals("", config.getLocation());
  }

  /**
   * Guard for MULTIPART_SOURCE_PATTERNS: under Tomcat, multipart parsing only works when the
   * request's originally-mapped servlet carries the multipart config, so every controller that
   * accepts a MultipartFile must have its public URL prefix covered by one of the patterns. Fails
   * when a new upload endpoint is added outside the covered prefixes.
   */
  @Test
  void everyMultipartControllerIsCoveredByMultipartSourcePatterns() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
    Set<BeanDefinition> candidates = new LinkedHashSet<>();
    candidates.addAll(scanner.findCandidateComponents("com.researchspace"));
    candidates.addAll(scanner.findCandidateComponents("com.axiope"));

    List<String> uncovered = new ArrayList<>();
    for (BeanDefinition bd : candidates) {
      Class<?> controller = Class.forName(bd.getBeanClassName());
      boolean handlesMultipart =
          Arrays.stream(controller.getMethods())
              .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
              .anyMatch(DispatcherServletInitializerTest::takesMultipartFile);
      if (!handlesMultipart) {
        continue;
      }
      RequestMapping mapping =
          AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
      String[] prefixes =
          (mapping == null || mapping.value().length == 0) ? new String[] {"/"} : mapping.value();
      for (String prefix : prefixes) {
        // Public-view aliases are anonymous and read-only; uploads are never served from them.
        if (prefix.startsWith("/public/")) {
          continue;
        }
        if (!coveredByMultipartSourcePatterns(prefix)) {
          uncovered.add(controller.getSimpleName() + " -> " + prefix);
        }
      }
    }
    assertTrue(
        uncovered.isEmpty(),
        "Upload-accepting controllers not covered by"
            + " DispatcherServletInitializer.MULTIPART_SOURCE_PATTERNS (uploads on these paths"
            + " fail under Tomcat): "
            + uncovered);
  }

  private static boolean takesMultipartFile(Method method) {
    for (Parameter param : method.getParameters()) {
      if (isMultipart(param.getType())) {
        return true;
      }
      if (param.getParameterizedType() instanceof ParameterizedType parameterized
          && Arrays.stream(parameterized.getActualTypeArguments())
              .anyMatch(t -> t == MultipartFile.class)) {
        return true;
      }
      // form-backing beans with MultipartFile fields
      String paramClass = param.getType().getName();
      if (paramClass.startsWith("com.researchspace") || paramClass.startsWith("com.axiope")) {
        for (Field field : param.getType().getDeclaredFields()) {
          if (isMultipart(field.getType())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isMultipart(Class<?> type) {
    return MultipartFile.class.isAssignableFrom(type)
        || (type.isArray() && MultipartFile.class.isAssignableFrom(type.getComponentType()));
  }

  private static boolean coveredByMultipartSourcePatterns(String prefix) {
    // Normalise controller-level patterns like "/userform*" to their literal root.
    String path = prefix.endsWith("*") ? prefix.substring(0, prefix.length() - 1) : prefix;
    for (String pattern : DispatcherServletInitializer.MULTIPART_SOURCE_PATTERNS) {
      String root = pattern.substring(0, pattern.length() - 2);
      if (path.equals(root) || path.startsWith(root + "/")) {
        return true;
      }
    }
    return false;
  }

  @Test
  void contextInitializedUsesDefaultSizeWhenNothingConfigured() {
    ServletRegistration.Dynamic dynamic = mock(ServletRegistration.Dynamic.class);
    ServletContext ctx = mock(ServletContext.class);
    when(ctx.addServlet(eq("dispatcher"), any(DispatcherServlet.class))).thenReturn(dynamic);
    ServletContextEvent sce = mock(ServletContextEvent.class);
    when(sce.getServletContext()).thenReturn(ctx);

    initializer.contextInitialized(sce);

    ArgumentCaptor<MultipartConfigElement> multipart =
        ArgumentCaptor.forClass(MultipartConfigElement.class);
    verify(dynamic).setMultipartConfig(multipart.capture());
    MultipartConfigElement config = multipart.getValue();
    assertEquals(DispatcherServletInitializer.DEFAULT_MAX_FILE_SIZE, config.getMaxFileSize());
    assertEquals(
        DispatcherServletInitializer.DEFAULT_MAX_FILE_SIZE * 2, config.getMaxRequestSize());
  }
}
