package com.axiope.service.cfg;

import static com.researchspace.spring.taskexecutors.ShiroThreadBindingSubjectThreadPoolExecutor.createNewExecutor;

import com.axiope.userimport.IPostUserCreationSetUp;
import com.axiope.userimport.NullOpPostUserCreate;
import com.researchspace.admin.service.IServerlogRetriever;
import com.researchspace.admin.service.impl.FileLocationBasedLogRetriever;
import com.researchspace.core.util.cache.DefaultTimeLimitedMemoryCache;
import com.researchspace.core.util.cache.TimeLimitedMemoryCache;
import com.researchspace.document.importer.ExternalFileImporter;
import com.researchspace.document.importer.MSWordImporter;
import com.researchspace.documentconversion.spi.CompositeDocumentConvertor;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.dryad.rspaceadapter.DryadRSpaceRepository;
import com.researchspace.figshare.rspaceadapter.FigshareRSpaceRepository;
import com.researchspace.licenseserver.model.License;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.StubRepositoryImpl;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.RemoteLicenseService;
import com.researchspace.service.impl.CommunicationManagerImpl;
import com.researchspace.service.impl.DeveloperGroupSetup;
import com.researchspace.service.impl.DummyConversionService;
import com.researchspace.service.impl.EcatMediaFactory;
import com.researchspace.service.impl.GlobalInitManagerImpl;
import com.researchspace.service.impl.LicenseServerChecker;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.snapgene.wclient.SnapgeneWSClient;
import com.researchspace.snapgene.wclient.SnapgeneWSClientImpl;
import com.researchspace.webapp.filter.MockRemoteUserPolicy;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import com.researchspace.webapp.integrations.snapgene.SnapgeneDummy;
import io.vavr.control.Option;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Run profile spring configuration used for running the web application in test/dev environments.
 *
 * <p>This is the default setting when running the web app ( this default is set in web.xml)<br>
 * Activate using property -Dspring.profiles.active=run
 */
@Configuration
@Profile("run")
@EnableAsync
public class TestAppConfig extends BaseConfig {

  @Bean(name = "postUserCreate")
  public IPostUserCreationSetUp postUserCreationSetup() {
    return new NullOpPostUserCreate();
  }

  @Bean
  public CommunicationManager communicationManager() {
    CommunicationManagerImpl impl = new CommunicationManagerImpl();
    List<Broadcaster> bcasters = new ArrayList<Broadcaster>();
    bcasters.add(broadcaster());
    impl.setBroadcasters(bcasters);
    return impl;
  }

  @Bean(name = "indexTaskExecutor")
  TaskExecutor indexExecutor() {
    // very low capacity for testing
    return createNewExecutor(1, 3, 5000);
  }

  @Bean(name = "emailTaskExecutor")
  TaskExecutor emailTaskExecutor() {
    // very low capacity for testing
    return createNewExecutor(1, 3, 10);
  }

  @Bean(name = "archiveTaskExecutor")
  TaskExecutor archiveTaskExecutor() {
    // very low capacity for testing
    return createNewExecutor(1, 3, 10);
  }

  @Bean(name = "signTaskExecutor")
  TaskExecutor signTaskExecutor() {
    // very low capacity for testing
    return createNewExecutor(2, 6, 10);
  }

  @Bean(name = "docConverter")
  TaskExecutor docConverter() {
    // very low capacity for testing
    return createNewExecutor(1, 2, 10);
  }

  @Bean(name = "slackRequestExecutor")
  TaskExecutor slackRequestExecutor() {
    // very low capacity for testing
    return createNewExecutor(1, 2, 10);
  }

  @Bean(name = "externalFileTaskExecutor")
  TaskExecutor externalFileTaskExecutor() {
    return createNewExecutor(2, 5, 10);
  }

  @Bean
  public IApplicationInitialisor licenseServerChecker() {
    return new LicenseServerChecker();
  }

  @Bean
  public GlobalInitManager globalInitManager() {
    GlobalInitManagerImpl mgr = new GlobalInitManagerImpl();
    List<IApplicationInitialisor> inits = new ArrayList<IApplicationInitialisor>();
    inits.add(fileStoreRootDetector());
    inits.add(indexer());
    inits.add(chemistryIndexer());
    inits.add(chemistryImageUpdater());
    // must be before devGrpSetup
    inits.add(sampleTemplateAppInitialiser());
    inits.add(devGrpSetup());
    inits.add(integrationsHandlerInitialisor());
    inits.add(dBDataIntegrityChecker());
    inits.add(systemConfigurationUpdater());
    inits.add(customForms());
    inits.add(sharedSnippetsFolderCreator());
    inits.add(sanityChecker()); // must be last
    mgr.setApplicationInitialisors(inits);
    return mgr;
  }

  @Bean
  public IApplicationInitialisor devGrpSetup() {
    return new DeveloperGroupSetup();
  }

  @Bean
  public IRepository repository() {
    return new StubRepositoryImpl();
  }

  /** Gets a dummy license client that allows for everything without connecting to license server */
  @Bean
  public RemoteLicenseService licenseService() {
    return new NoCheckLicenseService();
  }

  /**
   * For testing, just cache for 1 minute.
   *
   * @return
   */
  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public TimeLimitedMemoryCache<License> licenseCache() {
    DefaultTimeLimitedMemoryCache<License> rc = new DefaultTimeLimitedMemoryCache<>();
    rc.setCacheTimeMillis(Long.valueOf(60 * 1000));
    return rc;
  }

  /**
   * Returns log file parser
   *
   * @return
   */
  @Bean
  public IServerlogRetriever serverlogRetriever() {
    return new FileLocationBasedLogRetriever();
  }

  @Bean
  protected IMediaFactory mediaFactory() {
    EcatMediaFactory rc = new EcatMediaFactory();
    rc.setMaxImageMemorySize(maxTiffConversionSize);
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
    return new MSWordImporter(compositeDocumentConverter());
  }

  /**
   * Creates a Snapgene WS client or
   *
   * @return
   */
  @Bean
  SnapgeneWSClient snapgeneWSClient() {
    return Option.of(snapGeneWebUrl)
        .toTry()
        .mapTry(URI::new)
        .peek(uri -> log.info("Creating SnapgeneClient at {}", uri))
        .<SnapgeneWSClient>map(
            url -> new SnapgeneWSClientImpl(url, baseDocConverterConfig.customerIDSupplier()))
        .getOrElse(this::dummySnapgene);
  }

  SnapgeneDummy dummySnapgene() {
    log.info("Creating a SnapgeneDummy  implementation");
    return new SnapgeneDummy();
  }

  @Bean(name = "figshareRepository")
  public IRepository figshareRepository() {
    FigshareRSpaceRepository rc = new FigshareRSpaceRepository();
    log.info("Setting in repository implementation " + rc);
    return rc;
  }

  /**
   * Creates a new {@link IRepository} bean for Dryad.
   *
   * @return the repository adapter
   */
  @Bean(name = "dryadRepository")
  public IRepository dryadRepository() {
    DryadRSpaceRepository rc = new DryadRSpaceRepository();
    log.info("Setting in repository implementation {}", rc);
    return rc;
  }
}
