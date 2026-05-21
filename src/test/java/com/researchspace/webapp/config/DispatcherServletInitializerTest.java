package com.researchspace.webapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
