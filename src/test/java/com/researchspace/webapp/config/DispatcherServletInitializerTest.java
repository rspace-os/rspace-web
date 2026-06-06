package com.researchspace.webapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletRegistration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
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
    verify(dynamic).addMapping("/app/*", "/offline/*");

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
