package com.axiope.service.cfg;

import com.axiope.userimport.IPostUserCreationSetUp;
import com.axiope.userimport.NullOpPostUserCreate;
import com.researchspace.admin.service.IServerlogRetriever;
import com.researchspace.core.util.NullCache;
import com.researchspace.core.util.cache.TimeLimitedMemoryCache;
import com.researchspace.document.importer.ExternalFileImporter;
import com.researchspace.document.importer.MSWordImporter;
import com.researchspace.document.importer.TestWordImportConversionService;
import com.researchspace.documentconversion.spi.CompositeDocumentConvertor;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.licenseserver.model.License;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.StubRepositoryImpl;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.LicenseService;
import com.researchspace.service.impl.CommunicationManagerImpl;
import com.researchspace.service.impl.DevBroadCaster;
import com.researchspace.service.impl.DevEmailSenderImpl;
import com.researchspace.service.impl.DummyConversionService;
import com.researchspace.service.impl.EcatMediaFactory;
import com.researchspace.service.impl.GlobalInitManagerImpl;
import com.researchspace.service.impl.InternalFileStoreImpl;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.filter.MockRemoteUserPolicy;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Dev profile spring configuration for running tests <br>
 * Activate using property -Dspring.profiles.active=dev (which is active in Spring test classes)
 */
@Configuration
@EnableAsync
@Profile("dev")
public class RSDevConfig extends BaseConfig {

  @Bean
  public Broadcaster broadcaster() {
    return new DevBroadCaster("dev");
  }

  @Bean(name = "postUserCreate")
  public IPostUserCreationSetUp postUserCreationSetup() {
    return new NullOpPostUserCreate();
  }

  /**
   * Minimal startup needed when testing
   *
   * @return
   */
  @Bean
  public GlobalInitManager globalInitManager() {
    GlobalInitManagerImpl mgr = new GlobalInitManagerImpl();
    List<IApplicationInitialisor> inits = new ArrayList<IApplicationInitialisor>();
    inits.add(fileStoreRootDetector());
    inits.add(integrationsHandlerInitialisor());
    mgr.setApplicationInitialisors(inits);
    return mgr;
  }

  @Bean
  public CommunicationManager communicationManager() {
    CommunicationManagerImpl impl = new CommunicationManagerImpl();
    List<Broadcaster> bcasters = new ArrayList<Broadcaster>();
    bcasters.add(broadcaster());
    impl.setBroadcasters(bcasters);
    return impl;
  }

  @Bean
  public EmailBroadcast emailBroadcast() {
    return new DevEmailSenderImpl();
  }

  @Bean(name = "indexTaskExecutor")
  TaskExecutor indexExecutor() {
    return new SyncTaskExecutor(); // sync for testing
  }

  @Bean(name = "externalFileTaskExecutor")
  TaskExecutor externalFileTaskExecutor() {
    return new SyncTaskExecutor(); // sync for testing
  }

  @Bean(name = "emailTaskExecutor")
  TaskExecutor emailTaskExecutor() {
    return new SyncTaskExecutor(); // sync for testing
  }

  @Bean(name = "archiveTaskExecutor")
  TaskExecutor archiveTaskExecutor() {
    return new SyncTaskExecutor(); // sync for testing
  }

  @Bean(name = "signTaskExecutor")
  TaskExecutor signTaskExecutor() {
    return new SyncTaskExecutor(); // sync for testing
  }

  @Bean(name = "docConverter")
  TaskExecutor docConverter() {
    return new SyncTaskExecutor(); // sync for testing
  }

  @Bean(name = "slackRequestExecutor")
  TaskExecutor slackRequestExecutor() {
    return new SyncTaskExecutor(); // sync for testing
  }

  /**
   * Sets audit trail to be inactive during test runs. Also wraps the service as Mockito spy so
   * integration tests can trace its usage.
   */
  @Bean
  public AuditTrailService auditTrailService() {
    AuditTrailService impl = super.auditTrailService();
    impl.setActive(false);
    return Mockito.spy(impl);
  }

  @Bean(name = "stub")
  @Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Primary
  public IRepository repository() {
    return new StubRepositoryImpl();
  }

  /**
   * For dev, we just allow all user requests to be met.
   *
   * @return a {@link LicenseService} that allows unlimited users
   */
  @Bean
  public LicenseService licenseService() {
    return new NoCheckLicenseService();
  }

  /**
   * For testing we never cache.
   *
   * @return
   */
  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public TimeLimitedMemoryCache<License> licenseCache() {
    return new NullCache<License>();
  }

  /**
   * Returns no-op parser.
   *
   * @return
   */
  @Bean
  IServerlogRetriever serverlogRetriever() {
    return IServerlogRetriever.NULL_LOG_RETRIEVER;
  }

  @Bean
  @Override
  public InternalFileStore internalFileStore() throws IOException {
    File tempDir = FileUtils.getTempDirectory();
    File root = new File(tempDir, "rs-test-fs");
    FileUtils.forceMkdir(root);
    InternalFileStoreImpl fs = new InternalFileStoreImpl();
    fs.setBaseDir(root);
    return fs;
  }

  @Bean
  protected IMediaFactory mediaFactory() {
    EcatMediaFactory rc = new EcatMediaFactory();
    rc.setMaxImageMemorySize(maxTiffConversionSize); // 100kb for testing.
    return rc;
  }

  @Bean
  protected RemoteUserRetrievalPolicy remoteUserRetrievalPolicy() {
    return new MockRemoteUserPolicy();
  }

  @Bean
  public DocumentConversionService dummyDocumentConversionService() {
    DummyConversionService rc = new DummyConversionService();
    return rc;
  }

  @Bean
  DocumentConversionService compositeDocumentConverter() {
    CompositeDocumentConvertor composite = new CompositeDocumentConvertor();
    List<DocumentConversionService> delegates = new ArrayList<DocumentConversionService>();
    delegates.add(baseDocConverterConfig.pdfToImageConverter());
    delegates.add(dummyDocumentConversionService());
    composite.setDelegates(delegates);
    return composite;
  }

  @Bean
  ExternalFileImporter externalWordFileImporter() {
    return new MSWordImporter(createDummyConverter().getTcs());
  }

  @Data
  public static class DummyWord2HTMLConverter {
    File wordHtml;
    File word2rspaceFolder;
    File originalWordFile;
    MockMultipartFile multiFile;
    TestWordImportConversionService tcs;
  }

  @Bean
  public DummyWord2HTMLConverter createDummyConverter() {
    DummyWord2HTMLConverter rc = new DummyWord2HTMLConverter();
    rc.wordHtml =
        RSpaceTestUtils.getResource("word2rspace/powerpaste/PowerPasteTesting_RSpace.html");
    rc.word2rspaceFolder = rc.wordHtml.getParentFile();
    rc.originalWordFile =
        RSpaceTestUtils.getResource("word2rspace/powerpaste/PowerPasteTesting_RSpace.docx");
    try {
      rc.multiFile = fileToMultipartfile("wordXfile", rc.originalWordFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
    rc.tcs = new TestWordImportConversionService(rc.word2rspaceFolder, rc.wordHtml);
    return rc;
  }

  private MockMultipartFile fileToMultipartfile(String paramname, File file) throws IOException {
    return new MockMultipartFile(
        paramname, file.getName(), "unknown", FileUtils.readFileToByteArray(file));
  }
}
