package com.axiope.service.cfg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.document.importer.ExternalFileImporter;
import com.researchspace.document.importer.MSWordImporter;
import com.researchspace.document.importer.RSpaceDocumentCreator;
import com.researchspace.documentconversion.spi.CompositeDocumentConvertor;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.service.FolderManager;
import com.researchspace.service.LicenseService;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * This test class tests how different deployment property settings affect the connfiguration of
 * DocumentConverterService.
 *
 * <p>By using static inner classes we can test different class-level configurations without having
 * to define every test in a separate class.
 *
 * <p>When running these tests, although we're using 'prod' profile, the Aspose classes aren't
 * present on the classpath during a unit test run, so even if
 * cfg.embeddedAsposeDocumentConversionService() is called, we'll just get the DummyConverter back.
 * This is fine, we just want to test here the conditions under which it is called.
 */
@RunWith(Suite.class)
@SuiteClasses({
  DocConverterConfigTest.EmbeddedNotUsedTest.class,
  DocConverterConfigTest.AppTest.class,
  DocConverterConfigTest.AppConverterNotSetIfNoExecutableFileFound.class,
  DocConverterConfigTest.NoopAddedIfNoAsposeLicense.class,
  DocConverterConfigTest.WebappConverterIfURLSet.class
})
public class DocConverterConfigTest {

  @Configuration
  @Profile(
      "docconverter") // needs to  define its own profile so that it doesn't pollute configuration
  // of other tests
  public static class DocConverterProdConfigTSS extends DocConverterProdConfig {
    // needed for Spring

    @Mock RSpaceDocumentCreator docCreator;
    @Mock FolderManager folderManager;

    public DocConverterProdConfigTSS() {}

    boolean isExecutable(String pathToAspose) {
      return true;
    }

    private void initMocks() {
      MockitoAnnotations.initMocks(DocConverterProdConfigTSS.class);
    }

    @Bean
    RSpaceDocumentCreator rSpaceDocumentCreator() {
      initMocks();
      return docCreator;
    }

    @Bean
    LicenseService licenseService() {
      return new NoCheckLicenseService();
    }

    @Bean
    FolderManager folderManager() {
      initMocks();
      return folderManager;
    }

    @Bean
    ExternalFileImporter externalWordFileImporter() {
      MSWordImporter msWordImporter = new MSWordImporter(compositeDocumentConverter());
      msWordImporter.setFolderMgr(folderManager());
      return msWordImporter;
    }

    // see http://blog.codeleak.pl/2015/09/placeholders-support-in-value.html, needed
    // so that unused properties are set to null.
    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
      PropertySourcesPlaceholderConfigurer props = new PropertySourcesPlaceholderConfigurer();
      props.setIgnoreUnresolvablePlaceholders(true);
      return props;
    }
  }

  /** This TSS makes sure that isExecutable is <code>false</code> for an aspose executable path */
  @Configuration
  @Profile("docconverter")
  public static class DocConverterProdConfigTSSNonExecutableAsposePathTSS
      extends DocConverterProdConfigTSS {
    // needed for Spring

    public DocConverterProdConfigTSSNonExecutableAsposePathTSS() {}

    boolean isExecutable(String pathToAspose) {
      return false;
    }
  }

  /*
   * Base test class for common class-level configuration.
   * We  configure 2 @Configuration classes, so we don't have to wire up the whole application
   * to test the logic in the configuration class.
   */
  @ContextConfiguration(classes = {DocConverterProdConfigTSS.class, DocConverterBaseConfig.class})
  @ActiveProfiles(profiles = {"prod", "docconverter"})
  @TestPropertySource(properties = {"aspose.app="})
  public static class TestBase extends AbstractJUnit4SpringContextTests {
    @Autowired DocConverterProdConfig cfg;

    List<DocumentConversionService> getConverterList() {
      CompositeDocumentConvertor docConverter =
          (CompositeDocumentConvertor) cfg.compositeDocumentConverter();
      List<DocumentConversionService> delegates = docConverter.getDelegates();
      return delegates;
    }
  }

  public static class EmbeddedNotUsedTest extends TestBase {
    // no aspose.app application path property set by default, so null converter should be set.
    @Test
    public void testEmbeddedConfig() {
      List<DocumentConversionService> delegates = getConverterList();
      assertEquals(2, delegates.size());
      assertTrue(delegates.contains(cfg.nullService()));
    }
  }

  // any file that exists in project should work OK here.
  //  aspose.app application path property IS set , so application converter should be set.
  @TestPropertySource(
      properties = {
        "aspose.app=src/test/resources/TestResources/file.sh",
        "aspose.license=src/test/resources/TestResources/file.sh"
      })
  public static class AppTest extends TestBase {
    @Test
    public void testAppConfig() {
      List<DocumentConversionService> delegates = getConverterList();
      assertEquals(2, delegates.size());
      assertNotNull("cfg is null", cfg);
      assertTrue(delegates.contains(cfg.asyncConverterService()));
      assertFalse(delegates.contains(cfg.nullService()));
    }
  }

  // any file that exists in project should work OK here.
  //  aspose.app application path property IS set , so application converter should be set.
  @TestPropertySource(
      properties = {"aspose.app=src/test/resources/TestResources/file.sh", "aspose.license="})
  public static class NoopAddedIfNoAsposeLicense extends TestBase {
    @Test
    public void testAppConfig() {
      List<DocumentConversionService> delegates = getConverterList();
      assertEquals(2, delegates.size());
      assertNotNull("cfg is null", cfg);
      assertFalse(delegates.contains(cfg.asyncConverterService()));
      assertTrue(delegates.contains(cfg.nullService()));
    }
  }

  // if web app url is set, this overrides aspose-app properties
  @TestPropertySource(
      properties = {
        "aspose.app=src/test/resources/TestResources/file.sh",
        "aspose.license=src/test/resources/TestResources/file.sh",
        "aspose.web.url=http://something.com"
      })
  public static class WebappConverterIfURLSet extends TestBase {
    @Test
    public void testAppConfig() {
      List<DocumentConversionService> delegates = getConverterList();
      assertEquals(2, delegates.size());
      assertNotNull("cfg is null", cfg);

      assertFalse(delegates.contains(cfg.asyncConverterService()));
      assertFalse(delegates.contains(cfg.nullService()));
      assertTrue(delegates.contains(cfg.asposeWebAppService()));
    }
  }

  // here we simulate the aspose jar not being executable
  @TestPropertySource(properties = {"aspose.app=somepathToApp.jar"})
  @ContextConfiguration(
      inheritLocations = false,
      classes = {
        DocConverterProdConfigTSSNonExecutableAsposePathTSS.class,
        DocConverterBaseConfig.class
      })
  @ActiveProfiles(profiles = "prod")
  public static class AppConverterNotSetIfNoExecutableFileFound extends TestBase {
    @Test
    public void testAppConfig() {
      List<DocumentConversionService> delegates = getConverterList();
      assertEquals(2, delegates.size());
      assertNotNull("cfg is null", cfg);
      // is not added, embedded is used as a fallback
      assertFalse(delegates.contains(cfg.asyncConverterService()));
    }
  }
}
