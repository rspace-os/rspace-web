package com.axiope.service.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.researchspace.document.importer.RSpaceDocumentCreator;
import com.researchspace.documentconversion.ext.JodConverterClient;
import com.researchspace.documentconversion.ext.PdfConversionClient;
import com.researchspace.documentconversion.spi.CompositeDocumentConvertor;
import com.researchspace.service.FolderManager;
import com.researchspace.service.LicenseService;
import com.researchspace.testutils.WithSpringContext;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import org.apache.hc.client5.http.config.RequestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

@DirtiesContext
@WithSpringContext
@ContextConfiguration(
    classes = {
      ConversionDeploymentPropertiesTest.DeploymentConfig.class,
      DocConverterProdConfig.class,
      DocConverterBaseConfig.class
    })
@ActiveProfiles({"prod", "conversion-deployment-test"})
class ConversionDeploymentPropertiesTest {

  @Autowired private CompositeDocumentConvertor composite;
  @Autowired private Environment environment;

  @Test
  void usesPlaceholderLocalPropertiesForUrlTimeoutsAndLimits() {
    assertNull(environment.getProperty("conversion.url"));
    var pdf =
        composite.getDelegates().stream()
            .filter(PdfConversionClient.class::isInstance)
            .findFirst()
            .orElseThrow();
    var word =
        composite.getDelegates().stream()
            .filter(JodConverterClient.class::isInstance)
            .findFirst()
            .orElseThrow();
    Object sidecar = ReflectionTestUtils.getField(pdf, "client");
    assertNotNull(sidecar);
    assertEquals(1234L, ReflectionTestUtils.getField(sidecar, "maxInputBytes"));
    assertEquals(5678L, ReflectionTestUtils.getField(sidecar, "maxOutputBytes"));
    assertEquals(901L, ReflectionTestUtils.getField(word, "maxHtmlBytes"));
    assertEquals(
        Duration.ofMillis(12345), ReflectionTestUtils.getField(sidecar, "conversionTimeout"));
    Object restClient = ReflectionTestUtils.getField(sidecar, "client");
    Object factory = ReflectionTestUtils.getField(restClient, "clientRequestFactory");
    Object apacheClient = ReflectionTestUtils.getField(factory, "httpClient");
    var requestConfig = (RequestConfig) ReflectionTestUtils.getField(apacheClient, "defaultConfig");
    assertNotNull(requestConfig);
    assertEquals(2345, requestConfig.getConnectionRequestTimeout().toMilliseconds());
    assertEquals(3456, requestConfig.getConnectTimeout().toMilliseconds());
    assertEquals(4567, requestConfig.getResponseTimeout().toMilliseconds());
  }

  @Configuration
  @Profile("conversion-deployment-test")
  static class DeploymentConfig {
    @Bean
    static SidecarFixture sidecarFixture() throws IOException {
      return new SidecarFixture();
    }

    @Bean
    static PropertySourcesPlaceholderConfigurer placeholders(SidecarFixture fixture) {
      Properties properties = new Properties();
      properties.setProperty(
          "conversion.url", "http://127.0.0.1:" + fixture.server.getAddress().getPort());
      properties.setProperty("aspose.enabled", "false");
      properties.setProperty("aspose.license", "");
      properties.setProperty("aspose.app", "");
      properties.setProperty("aspose.logfile", "");
      properties.setProperty("aspose.jvmArgs", "");
      properties.setProperty("aspose.loglevel", "INFO");
      properties.setProperty("conversion.connectionRequestTimeoutMs", "2345");
      properties.setProperty("conversion.connectTimeoutMs", "3456");
      properties.setProperty("conversion.conversionTimeoutMs", "12345");
      properties.setProperty("conversion.responseTimeoutMs", "4567");
      properties.setProperty("conversion.maxInputBytes", "1234");
      properties.setProperty("conversion.maxOutputBytes", "5678");
      properties.setProperty("conversion.maxHtmlBytes", "901");
      var configurer = new PropertySourcesPlaceholderConfigurer();
      configurer.setProperties(properties);
      return configurer;
    }

    @Bean
    RSpaceDocumentCreator creator() {
      return mock(RSpaceDocumentCreator.class);
    }

    @Bean
    LicenseService licenseService() {
      return mock(LicenseService.class);
    }

    @Bean
    FolderManager folderManager() {
      return mock(FolderManager.class);
    }
  }

  static class SidecarFixture implements AutoCloseable {
    final HttpServer server;

    SidecarFixture() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/v1/capabilities",
          exchange -> {
            try (exchange) {
              byte[] json =
                  ("{\"protocol\":\"rspace-conversion-sidecar\",\"version\":1,"
                          + "\"roles\":[\"pdf\",\"word\"]}")
                      .getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(200, json.length);
              exchange.getResponseBody().write(json);
            }
          });
      server.start();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
