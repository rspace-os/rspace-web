package com.axiope.service.cfg;

import com.researchspace.document.importer.ExternalFileImporter;
import com.researchspace.document.importer.MSWordImporter;
import com.researchspace.documentconversion.ext.AsposeAppInvoker;
import com.researchspace.documentconversion.ext.AsposeConversionChecker;
import com.researchspace.documentconversion.ext.AsposeWebAppClient;
import com.researchspace.documentconversion.ext.AsyncDocConverterService;
import com.researchspace.documentconversion.ext.AsyncDocumentConverterService;
import com.researchspace.documentconversion.ext.ConversionChecker;
import com.researchspace.documentconversion.spi.CompositeDocumentConvertor;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.service.impl.DummyConversionService;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Separate configuration class to enable easier unit testing without having to load all the
 * ApplicationContext for the entire application.
 */
@Configuration
@Profile({"prod", "prod-test"})
@EnableAsync
public class DocConverterProdConfig {

  protected Logger log = LoggerFactory.getLogger(DocConverterProdConfig.class);

  private static final String asposeLicensePathProperty = "aspose.license";

  private static final String asposeAppPathProperty = "aspose.app";

  // this is needed to resolve properties set in tests.
  @Autowired Environment env;

  @Value("${aspose.app:}")
  private String asposeApp;

  @Value("${aspose.license:}")
  private String asposeLicense;

  @Value("${aspose.web.url:}")
  private String asposeWebAppUrl;

  @Value("${aspose.enabled:true}")
  private boolean asposeEnabled;

  @Value("${aspose.use.dummy.converter:false}")
  private boolean useDummyConverter;

  @Autowired DocConverterBaseConfig baseCfg;

  // these 2 mechanisms are needed for test and production to work.
  private String getLicensePath() {
    // needed for unit test
    String envProperty = env.getProperty(asposeLicensePathProperty);
    if (!StringUtils.isEmpty(envProperty)) {
      return envProperty;
    } else {
      return asposeLicense; // needed for production
    }
  }

  private String getAppPath() {
    String appProperty = env.getProperty(asposeAppPathProperty);
    if (!StringUtils.isEmpty(appProperty)) {
      return appProperty;
    } else {
      return asposeApp;
    }
  }

  @Bean(name = "asposeAppService")
  public AsyncDocConverterService asposeAppService() {
    if (!asposeEnabled) {
      return new AsposeAppInvoker(cannotConvert());
    }
    return new AsposeAppInvoker(asposeConversionChecker());
  }

  @Bean
  public ConversionChecker asposeConversionChecker() {
    return new AsposeConversionChecker();
  }

  @Bean
  public ConversionChecker cannotConvert() {
    return (to, from) -> false;
  }

  @Bean(name = "asposeWebAppService")
  public DocumentConversionService asposeWebAppService() {
    if (!asposeEnabled) {
      return nullService();
    }
    try {
      URI uri = new URI(asposeWebAppUrl != null ? asposeWebAppUrl : "");
      AsposeWebAppClient webclient =
          new AsposeWebAppClient(uri, asposeConversionChecker(), baseCfg.customerIDSupplier());
      return webclient;
    } catch (URISyntaxException e) {
      throw new IllegalStateException(
          "Tried to create Aspose webapp converter with invalid or missing property"
              + " aspose.web.url ");
    }
  }

  @Bean(name = "asyncConverterService")
  public DocumentConversionService asyncConverterService() {
    if (!asposeEnabled) {
      return nullService();
    }
    AsyncDocumentConverterService invoker = new AsyncDocumentConverterService(asposeAppService());
    return invoker;
  }

  @Bean
  DocumentConversionService compositeDocumentConverter() {
    CompositeDocumentConvertor composite = new CompositeDocumentConvertor();
    List<DocumentConversionService> delegates = new ArrayList<>();
    delegates.add(baseCfg.pdfToImageConverter());
    String licensePath = getLicensePath();
    if (useDummyConverter) {
      delegates.add(new DummyConversionService());
      log.info("Using dummy conversion service returning sample files.");
    } else if (asposeEnabled) {
      if (!StringUtils.isBlank(asposeWebAppUrl)) {
        delegates.add(asposeWebAppService());
      } else {
        if (!isPathSet(licensePath, asposeLicensePathProperty) || !isReadable(licensePath)) {
          log.warn(
              "Aspose License path specified by property '{}' is not set",
              asposeLicensePathProperty);
          warnNoConversions();
          delegates.add(nullService());
        } else {
          // if we set a property to an external application, then we use it, else use embedded
          String pathToAspose = getAppPath();
          if (!isPathSet(pathToAspose, asposeAppPathProperty) || !isExecutable(pathToAspose)) {
            warnNoConversions();
            delegates.add(nullService());
          } else {
            log.info("Adding Aspose document converter application at {}", pathToAspose);
            delegates.add(asyncConverterService());
          }
        }
      }
    } else {
      delegates.add(nullService());
    }
    composite.setDelegates(delegates);
    return composite;
  }

  @Bean
  ExternalFileImporter externalWordFileImporter() {
    return new MSWordImporter(compositeDocumentConverter());
  }

  private void warnNoConversions() {
    log.warn(
        "Adding default no-ops document converter - Office Document previews will "
            + "not work until the converter application is set.");
  }

  @Bean(name = "unsupportedConverterService")
  public DocumentConversionService nullService() {
    return DocumentConversionService.NULL_SERVICE; // null
  }

  private boolean isPathSet(String filePath, String propertyName) {
    if (StringUtils.isEmpty(filePath)) {
      log.warn("No application path found for property '{}'", asposeAppPathProperty);
      return false;
    }
    return true;
  }

  boolean isExecutable(String filePath) {
    File file = new File(filePath);
    if (!file.exists()) {
      log.warn("Path to Aspose doc coverter application {} does not exist.", filePath);
      return false;
    } else if (!file.canExecute()) {
      log.warn(" Aspose doc converter application {} is not executable by RSpace.", filePath);
      return false;
    }
    return true;
  }

  private boolean isReadable(String filePath) {
    File file = new File(filePath);
    if (!file.exists()) {
      log.warn("Path to  [{}] does not exist.", filePath);
      return false;
    }
    return true;
  }
}
