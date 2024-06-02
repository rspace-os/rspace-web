package com.axiope.service.cfg;

import static com.researchspace.spring.taskexecutors.ShiroThreadBindingSubjectThreadPoolExecutor.createNewExecutor;

import com.axiope.userimport.IPostUserCreationSetUp;
import com.axiope.userimport.NotifyUserPostUserCreate;
import com.researchspace.admin.service.IServerlogRetriever;
import com.researchspace.admin.service.impl.FileLocationBasedLogRetriever;
import com.researchspace.core.util.cache.DefaultTimeLimitedMemoryCache;
import com.researchspace.core.util.cache.TimeLimitedMemoryCache;
import com.researchspace.dataverse.api.v1.DataverseAPI;
import com.researchspace.dataverse.http.DataverseAPIImpl;
import com.researchspace.dataverse.rspaceadapter.DataverseRSpaceRepository;
import com.researchspace.dataverse.rspaceadapter.DataverseRepoConfigurer;
import com.researchspace.dataverse.spring.config.DataverseSpringConfig;
import com.researchspace.dryad.rspaceadapter.DryadRSpaceRepository;
import com.researchspace.figshare.rspaceadapter.FigshareRSpaceRepository;
import com.researchspace.licenseserver.model.License;
import com.researchspace.licensews.client.LicenseWSClient;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.RepositoryConfigurer;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.GlobalInitManager;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.RemoteLicenseService;
import com.researchspace.service.impl.CommunicationManagerImpl;
import com.researchspace.service.impl.EcatMediaFactory;
import com.researchspace.service.impl.GlobalInitManagerImpl;
import com.researchspace.service.impl.LicenseServerChecker;
import com.researchspace.service.impl.license.NoCheckLicenseService;
import com.researchspace.spring.taskexecutors.ShiroThreadBindingSubjectThreadPoolExecutor;
import com.researchspace.webapp.filter.EASERemoteUserPolicy;
import com.researchspace.webapp.filter.MockRemoteUserPolicy;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import com.researchspace.webapp.filter.SAMLRemoteUserPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.context.WebApplicationContext;

/** Configuration for deployment/production */
@Configuration
@Profile({"prod", "prod-test"})
@EnableAsync
@Import(value = DataverseSpringConfig.class)
public class ProductionConfig extends BaseConfig {
  @Value("${deployment.sso.type}")
  private String deploymentSsoType;

  @Value("${license.server.active:true}")
  private boolean licenseServerActive;

  @Value("${figshare.categories.path}")
  private Path figshareCategoriesPath;

  @Value("${figshare.licenses.path}")
  private Path figshareLicensesPath;

  private @Autowired TaskExecutorConfig taskExecutorConfig;

  @Bean()
  public CommunicationManager communicationManager() {
    CommunicationManagerImpl impl = new CommunicationManagerImpl();
    List<Broadcaster> bcasters = new ArrayList<>();
    bcasters.add(broadcaster());
    impl.setBroadcasters(bcasters);
    return impl;
  }

  @Bean(name = "postUserCreate")
  public IPostUserCreationSetUp postUserCreationSetup() {
    return new NotifyUserPostUserCreate();
  }

  @Bean(name = "indexTaskExecutor")
  TaskExecutor indexExecutor() {
    return createNewExecutor(
        taskExecutorConfig.defaultTaskExecutorCore,
        taskExecutorConfig.defaultTaskExecutorMax,
        taskExecutorConfig.indexTaskExecutorQueue);
  }

  @Bean(name = "emailTaskExecutor")
  TaskExecutor emailTaskExecutor() {
    return createNewExecutor(
        taskExecutorConfig.defaultTaskExecutorCore,
        taskExecutorConfig.defaultTaskExecutorMax,
        taskExecutorConfig.defaultTaskExecutorQueue);
  }

  @Bean(name = "archiveTaskExecutor")
  TaskExecutor archiveTaskExecutor() {
    return createNewExecutor(
        taskExecutorConfig.defaultTaskExecutorCore,
        taskExecutorConfig.defaultTaskExecutorMax,
        taskExecutorConfig.defaultTaskExecutorQueue);
  }

  @Bean(name = "signTaskExecutor")
  TaskExecutor signTaskExecutor() {
    return createNewExecutor(
        taskExecutorConfig.defaultTaskExecutorCore,
        taskExecutorConfig.defaultTaskExecutorMax,
        taskExecutorConfig.defaultTaskExecutorQueue);
  }

  @Bean(name = "externalFileTaskExecutor")
  TaskExecutor externalFileTaskExecutor() {
    return createNewExecutor(
        taskExecutorConfig.defaultTaskExecutorCore,
        taskExecutorConfig.defaultTaskExecutorMax,
        taskExecutorConfig.defaultTaskExecutorQueue);
  }

  @Bean(name = "asyncDbMigrationExecutor")
  TaskExecutor asyncDbMigrationExecutor() {
    return new SimpleAsyncTaskExecutor();
  }

  /** Limits number of concurrent document conversions to */
  @Bean(name = "docConverter")
  TaskExecutor docConverter() {
    return ShiroThreadBindingSubjectThreadPoolExecutor.createNewExecutor(
        taskExecutorConfig.docConverterTaskExecutorCore,
        taskExecutorConfig.docConverterTaskExecutorMax,
        taskExecutorConfig.docConverterTaskExecutorQueue);
  }

  @Bean(name = "slackRequestExecutor")
  TaskExecutor slackRequestExecutor() {
    return ShiroThreadBindingSubjectThreadPoolExecutor.createNewExecutor(
        taskExecutorConfig.defaultTaskExecutorCore,
        taskExecutorConfig.defaultTaskExecutorMax,
        taskExecutorConfig.defaultTaskExecutorQueue);
  }

  @Bean
  public IApplicationInitialisor licenseServerChecker() {
    LicenseServerChecker rc = new LicenseServerChecker();
    rc.setLicenseService(licenseService());
    return rc;
  }

  /** The real production config that should be run in production */
  @Bean
  @Profile("!prod-test")
  public GlobalInitManager globalInitManager() {
    GlobalInitManagerImpl mgr = new GlobalInitManagerImpl();
    List<IApplicationInitialisor> inits = new ArrayList<>();
    inits.add(licenseServerChecker());
    inits.add(fileStoreRootDetector());
    inits.add(indexer());
    inits.add(integrationsHandlerInitialisor());
    inits.add(sampleTemplateAppInitialiser());
    inits.add(customForms());
    // should be last
    inits.add(dBDataIntegrityChecker());
    inits.add(sharedSnippetsFolderCreator());
    inits.add(sanityChecker());
    mgr.setApplicationInitialisors(inits);
    return mgr;
  }

  /** Removes sanity checker from test setup */
  @Bean
  @Profile("!prod")
  public GlobalInitManager globalInitManagerTest() {
    GlobalInitManagerImpl mgr = new GlobalInitManagerImpl();
    List<IApplicationInitialisor> inits = new ArrayList<>();
    inits.add(loadfromCSV());
    inits.add(indexer());
    inits.add(licenseServerChecker());
    inits.add(sharedSnippetsFolderCreator());
    mgr.setApplicationInitialisors(inits);
    return mgr;
  }

  @Bean
  @Scope(value = "prototype")
  public DataverseAPI dataverseAPI() {
    return new DataverseAPIImpl();
  }

  @Bean(name = "configurerDataverse")
  public RepositoryConfigurer dataverseRepoConfigurer() {
    DataverseRepoConfigurer rc = new DataverseRepoConfigurer();
    ClassPathResource subjects = new ClassPathResource("subjects.txt");
    rc.setResource(subjects);
    return rc;
  }

  /**
   * Create a new stateful repository adapter per request. <br>
   * The name is created from appName+'Repository' as IRepository beans are created on-demand at
   * export time.
   */
  @Bean(name = "dataverseRepository")
  @Scope(value = WebApplicationContext.SCOPE_REQUEST)
  public IRepository dataverseRepository() {
    DataverseRSpaceRepository repo = new DataverseRSpaceRepository();
    repo.setConfigurer(dataverseRepoConfigurer());
    repo.setDvAPI(dataverseAPI());
    logSettingRepositoryImplementation(repo);
    return repo;
  }

  /**
   * Creates a new {@link IRepository} bean for Figshare.
   *
   * @return the repository adapter
   */
  @Bean(name = "figshareRepository")
  public IRepository figshareRepository() {
    FigshareRSpaceRepository repo = new FigshareRSpaceRepository();
    try {
      if (figshareLicensesPath != null && figshareCategoriesPath != null) {
        log.info(
            "Setting Figshare categories and licenses from static files, "
                + "Categories: [{}]  Licenses: {}",
            figshareCategoriesPath,
            figshareLicensesPath);
        repo.setStaticFigshareConfig(
            Files.readString(figshareLicensesPath), Files.readString(figshareCategoriesPath));
        log.info("Licenses are: {}", repo.getFigshareLicenses());
      }
    } catch (Exception e) {
      log.error("Error setting static categories and subjects from JSON file: {}", e.getMessage());
    }
    logSettingRepositoryImplementation(repo);
    return repo;
  }

  /**
   * Creates a new {@link IRepository} bean for Dryad.
   *
   * @return the repository adapter
   */
  @Bean(name = "dryadRepository")
  public IRepository dryadRepository() {
    DryadRSpaceRepository repo = new DryadRSpaceRepository();
    logSettingRepositoryImplementation(repo);
    return repo;
  }

  /** Gets a REST client for test license server, */
  @Bean
  public RemoteLicenseService licenseService() {
    if (licenseServerActive) {
      log.info("License server active.");
      return new LicenseWSClient();
    } else {
      log.info("License server disabled. Using 'no check' RemoteLicenseService implementation.");
      return new NoCheckLicenseService();
    }
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public TimeLimitedMemoryCache<License> licenseCache() {
    DefaultTimeLimitedMemoryCache<License> rc = new DefaultTimeLimitedMemoryCache<>();
    // 3 day cache
    rc.setCacheTimeMillis((long) (3 * 24 * 60 * 60 * 1000));
    return rc;
  }

  @Bean
  protected RemoteUserRetrievalPolicy remoteUserRetrievalPolicy() {
    if ("SAML".equals(deploymentSsoType)) {
      return new SAMLRemoteUserPolicy();
    } else if ("TEST".equals(deploymentSsoType)) {
      return new MockRemoteUserPolicy();
    } else {
      return new EASERemoteUserPolicy();
    }
  }

  /** For parsing real log files */
  @Bean
  IServerlogRetriever serverlogRetriever() {
    return new FileLocationBasedLogRetriever();
  }

  @Bean
  protected IMediaFactory mediaFactory() {
    EcatMediaFactory rc = new EcatMediaFactory();
    rc.setMaxImageMemorySize(maxTiffConversionSize);
    return rc;
  }

  private void logSettingRepositoryImplementation(IRepository repo) {
    log.info("Setting in repository implementation {}", repo);
  }
}
