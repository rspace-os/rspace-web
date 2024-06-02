package com.axiope.service.cfg;

import static com.researchspace.files.service.ExternalFileStoreProvider.EGNYTE;

import com.axiope.search.FileSearchStrategy;
import com.axiope.search.IFileIndexer;
import com.axiope.userimport.AuthorisedPostSignUp;
import com.axiope.userimport.DefaultPostUserCreate;
import com.axiope.userimport.IPostUserSignup;
import com.axiope.userimport.RequireValidUserNameStrategy;
import com.axiope.userimport.UserNameCreationStrategy;
import com.axiope.userimport.UserNameFromFirstLastNameStrategy;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.auth.GroupPermissionUtils;
import com.researchspace.auth.LoginHelper;
import com.researchspace.auth.ManualLoginHelperImpl;
import com.researchspace.auth.PostOAuthLoginHelperImpl;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.auth.WhiteListIPCheckerImpl;
import com.researchspace.core.util.ResponseUtil;
import com.researchspace.dao.customliquibaseupdates.LiveLiqUpdater;
import com.researchspace.document.importer.DocumentImporterFromWord2HTML;
import com.researchspace.document.importer.EvernoteEnexImporter;
import com.researchspace.document.importer.ExternalFileImporter;
import com.researchspace.document.importer.RSpaceDocumentCreator;
import com.researchspace.export.pdf.ExportConfigurer;
import com.researchspace.export.pdf.ExportConfigurerImpl;
import com.researchspace.export.pdf.ExportProcessor;
import com.researchspace.export.pdf.ExportToFileConfig;
import com.researchspace.export.pdf.HTMLStringGenerator;
import com.researchspace.export.pdf.HTMLStringGeneratorForExport;
import com.researchspace.export.pdf.ImageRetrieverHelper;
import com.researchspace.export.pdf.ImageRetrieverHelperImpl;
import com.researchspace.export.pdf.MSWordProcessor;
import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.extmessages.msteams.MsTeamsMessageSender;
import com.researchspace.files.service.ExternalFileService;
import com.researchspace.files.service.ExternalFileServiceImpl;
import com.researchspace.files.service.ExternalFileStore;
import com.researchspace.files.service.ExternalFileStoreLocator;
import com.researchspace.files.service.ExternalFileStoreLocatorImpl;
import com.researchspace.files.service.ExternalFileStoreProvider;
import com.researchspace.files.service.FileStore;
import com.researchspace.files.service.FileStoreImpl;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.files.service.egnyte.EgnyteFileSearcher;
import com.researchspace.files.service.egnyte.EgnyteFileStoreAdapter;
import com.researchspace.googleauth.ExternalAuthTokenVerifier;
import com.researchspace.googleauth.impl.GoogleTokenVerifier;
import com.researchspace.licensews.client.DefaultLicenseRequestProcessor;
import com.researchspace.licensews.client.LicenseRequestProcessor;
import com.researchspace.linkedelements.FieldLinksEntitiesSynchronizer;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.audittrail.AuditTrailImpl;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.Log4jHistoryDAOImpl;
import com.researchspace.model.comms.RequestFactory;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.SymmetricTextEncryptor;
import com.researchspace.model.permissions.TextEncryptor;
import com.researchspace.model.record.BreadcrumbGenerator;
import com.researchspace.model.record.DefaultBreadcrumbGenerator;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.units.QuantityUtils;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.search.impl.FileIndexer;
import com.researchspace.search.impl.LuceneSearchStrategy;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.Broadcaster;
import com.researchspace.service.DeletionExecutor;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.DocumentHTMLPreviewHandler;
import com.researchspace.service.DocumentSharedStateCalculator;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.ExternalMessageSenderFactory;
import com.researchspace.service.FolderDeletionOrderPolicy;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.service.IAsyncArchiveDepositor;
import com.researchspace.service.IContentInitialiserUtils;
import com.researchspace.service.IMediaFactory;
import com.researchspace.service.IMessageAndNotificationTracker;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.IRepositoryConfigFactory;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.ImageProcessor;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.MessageOrRequestCreatorManager;
import com.researchspace.service.PiChangeHandler;
import com.researchspace.service.PostAnyLoginAction;
import com.researchspace.service.PostFirstLoginAction;
import com.researchspace.service.PostLoginHandler;
import com.researchspace.service.PostSigningManager;
import com.researchspace.service.RSpaceRequestManager;
import com.researchspace.service.RSpaceRequestOnCreateHandler;
import com.researchspace.service.RSpaceRequestUpdateHandler;
import com.researchspace.service.RepositoryFactory;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.SysadminUserCreationHandler;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserFolderCreator;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import com.researchspace.service.archive.ArchiveImporterManager;
import com.researchspace.service.archive.ArchiveImporterManagerImpl;
import com.researchspace.service.archive.ArchiveModelToDatabaseSaver;
import com.researchspace.service.archive.ArchiveModelToDatabaseSaverImpl;
import com.researchspace.service.archive.ArchiveParserImpl;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.FolderTreeImporter;
import com.researchspace.service.archive.FolderTreeImporterImpl;
import com.researchspace.service.archive.FormImporter;
import com.researchspace.service.archive.FormImporterImpl;
import com.researchspace.service.archive.IArchiveParser;
import com.researchspace.service.archive.IExportUtils;
import com.researchspace.service.archive.ImportRecordsOnly;
import com.researchspace.service.archive.ImportStrategy;
import com.researchspace.service.archive.ImportUsersAndRecords;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.service.archive.TimeLimitedExportRemovalPolicy;
import com.researchspace.service.archive.UserImporter;
import com.researchspace.service.archive.UserImporterImpl;
import com.researchspace.service.archive.export.ArchiveDataHandler;
import com.researchspace.service.archive.export.ArchiveExportPlanner;
import com.researchspace.service.archive.export.ArchiveExportPlannerImpl;
import com.researchspace.service.archive.export.ArchiveNamingStrategy;
import com.researchspace.service.archive.export.ArchiveRemover;
import com.researchspace.service.archive.export.ExportObjectGenerator;
import com.researchspace.service.archive.export.ExportObjectWriter;
import com.researchspace.service.archive.export.ExportRemovalPolicy;
import com.researchspace.service.archive.export.FormIconWriter;
import com.researchspace.service.archive.export.HTMLWriter;
import com.researchspace.service.archive.export.MessageArchiveDataHandler;
import com.researchspace.service.archive.export.StandardPostExportCompletionImpl;
import com.researchspace.service.archive.export.UserArchiveDataHandler;
import com.researchspace.service.archive.export.XMLArchiveExportManagerServiceImpl;
import com.researchspace.service.audit.search.AuditTrailHandler;
import com.researchspace.service.audit.search.AuditTrailHandlerImpl;
import com.researchspace.service.audit.search.BasicLogQuerySearcher;
import com.researchspace.service.audit.search.IAuditFileSearch;
import com.researchspace.service.audit.search.IAuditSearchResultPostProcessor;
import com.researchspace.service.audit.search.ILogResourceTracker;
import com.researchspace.service.audit.search.LogFileTracker;
import com.researchspace.service.audit.search.LogLineContentProvider;
import com.researchspace.service.audit.search.LogLineContentProviderImpl;
import com.researchspace.service.audit.search.UpdateRecordNamePostProcessor;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesImpl;
import com.researchspace.service.cloud.impl.CommunityManualUserSignupPolicy;
import com.researchspace.service.cloud.impl.CommunityPostSignupVerification;
import com.researchspace.service.cloud.impl.CreateLabGroupRequestHandler;
import com.researchspace.service.cloud.impl.ShareRecordRequestHandler;
import com.researchspace.service.impl.ApiAvailabilityHandlerImpl;
import com.researchspace.service.impl.AsyncDepositorImpl;
import com.researchspace.service.impl.CollabGroupShareRequestCreateHandler;
import com.researchspace.service.impl.CollabGroupShareRequestUpdateHandler;
import com.researchspace.service.impl.ContentInitialiserUtilsImpl;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import com.researchspace.service.impl.DBDataIntegrityChecker;
import com.researchspace.service.impl.DMPUpdateHandler;
import com.researchspace.service.impl.DefaultUserFolderCreator;
import com.researchspace.service.impl.DefaultUserSignupPolicy;
import com.researchspace.service.impl.DeleteFolderFromSharedFolderPolicy;
import com.researchspace.service.impl.DeleteFromSharedFolderExecutor;
import com.researchspace.service.impl.DetailedRecordInformationProviderImpl;
import com.researchspace.service.impl.DevBroadCaster;
import com.researchspace.service.impl.DevEmailSenderImpl;
import com.researchspace.service.impl.DocumentHTMLPreviewHandlerImpl;
import com.researchspace.service.impl.EmailBroadcastImp;
import com.researchspace.service.impl.ExampleContentAction;
import com.researchspace.service.impl.ExportImportImpl;
import com.researchspace.service.impl.ExportUtils;
import com.researchspace.service.impl.ExternalMessageHandlerImpl;
import com.researchspace.service.impl.ExternalMessageSenderFactoryImpl;
import com.researchspace.service.impl.ExternalOauthUserSignupPolicy;
import com.researchspace.service.impl.FieldLinksEntitySyncImpl;
import com.researchspace.service.impl.FileStoreRootDetector;
import com.researchspace.service.impl.GroupSharedSnippetsFolderAppInitialiser;
import com.researchspace.service.impl.ImageProcessorImpl;
import com.researchspace.service.impl.IntegrationsHandlerImpl;
import com.researchspace.service.impl.IntegrationsHandlerInitialisor;
import com.researchspace.service.impl.InternalFileStoreImpl;
import com.researchspace.service.impl.JoinExistingCollGroupRequestUpdateHandler;
import com.researchspace.service.impl.JoinGroupRequestUpdateHandler;
import com.researchspace.service.impl.LoadUsersFromCSVOnStartUpInitialisor;
import com.researchspace.service.impl.LoggingInitializer;
import com.researchspace.service.impl.LuceneSearchIndexInitialisor;
import com.researchspace.service.impl.MessageAndNotificationTracker;
import com.researchspace.service.impl.MessageOrRequestCreatorManagerImpl;
import com.researchspace.service.impl.MovePermissionChecker;
import com.researchspace.service.impl.MultipleSharesPermittedDocSharedStatusCalculatorImpl;
import com.researchspace.service.impl.OperateAsUserLookup;
import com.researchspace.service.impl.PiChangeHandlerImpl;
import com.researchspace.service.impl.PostLoginHandlerImpl;
import com.researchspace.service.impl.PostRecordSigningExportHash;
import com.researchspace.service.impl.RSpaceRequestManagerImpl;
import com.researchspace.service.impl.ReauthenticatorImpl;
import com.researchspace.service.impl.RepositoryDepositHandlerImpl;
import com.researchspace.service.impl.SampleTemplateAppInitialiser;
import com.researchspace.service.impl.SanityChecker;
import com.researchspace.service.impl.SharingHandlerImpl;
import com.researchspace.service.impl.StrictEmailContentGenerator;
import com.researchspace.service.impl.SysadminUserCreationHandlerImpl;
import com.researchspace.service.impl.SystemPropertyPermissionManagerImpl;
import com.researchspace.service.impl.UserContentUpdater;
import com.researchspace.service.impl.UserContentUpdaterImpl;
import com.researchspace.service.impl.UserExternalIdResolverImpl;
import com.researchspace.slack.SlackMessageSender;
import com.researchspace.snapgene.wclient.SnapgeneWSClient;
import com.researchspace.snapgene.wclient.SnapgeneWSClientImpl;
import com.researchspace.webapp.controller.AuditTrailSearchResultCsvGenerator;
import com.researchspace.webapp.controller.PaginationSettingsPreferences;
import com.researchspace.webapp.controller.RepositoryConfigFactoryImpl;
import com.researchspace.webapp.controller.SysadminCreateUserFormConfigurer;
import com.researchspace.webapp.controller.SysadminCreateUserFormConfigurerImpl;
import com.researchspace.webapp.controller.WorkspaceViewModePreferences;
import com.researchspace.webapp.filter.BaseShiroFormAuthFilterExt;
import com.researchspace.webapp.filter.OriginRefererChecker;
import com.researchspace.webapp.filter.OriginRefererCheckerImpl;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import com.researchspace.webapp.filter.SSOShiroFormAuthFilterExt;
import com.researchspace.webapp.filter.StandaloneShiroFormAuthFilterExt;
import com.researchspace.webapp.integrations.argos.ArgosDMPProvider;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorImpl;
import com.researchspace.webapp.integrations.dmptool.DMPToolDMPProvider;
import com.researchspace.webapp.integrations.dmptool.DMPToolDMPProviderImpl;
import com.researchspace.webapp.integrations.egnyte.EgnyteAuthConnector;
import com.researchspace.webapp.integrations.egnyte.EgnyteAuthConnectorImpl;
import com.researchspace.webapp.integrations.pyrat.PyratClient;
import com.researchspace.webapp.integrations.slack.SlackService;
import com.researchspace.webapp.integrations.slack.SlackServiceImpl;
import com.researchspace.webapp.integrations.snapgene.SnapgeneWSNoop;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryProcessor;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler;
import com.researchspace.webapp.integrations.wopi.WopiProofKeyValidator;
import com.researchspace.zenodo.rspaceadapter.ZenodoRSpaceRepository;
import io.vavr.control.Option;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.ui.velocity.VelocityEngineFactoryBean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

/**
 * Base class with configuration for all Spring profiles - gradually using this for new beans
 * /services rather than XML configuration.
 */
@Configuration
@EnableScheduling
@EnableTransactionManagement
@EnableRetry
public abstract class BaseConfig {

  @Autowired ApplicationContext context;
  protected @Autowired DocConverterBaseConfig baseDocConverterConfig;
  protected @Autowired DeploymentPropertyConfig deploymentPropertyConfig;
  protected @Autowired DataSource dataSource;

  @Value("${authorised.signup}")
  private String authorizedSignup;

  @Value("${files.maxUploadSize}")
  private String maxUploadSize;

  @Value("${email.enabled}")
  private String emailEnabled;

  // optional folder for velocity templates
  @Value("${velocity.ext.dir}")
  private String velocityExtDir;

  @Value("${apitoken.encryption.key}")
  private String apiTokenEncryptionKey;

  @Value("${rs.filestore}")
  private String fileStoreType;

  @Value("${rs.ext.filestore.baseURL}")
  private String fileStoreBaseUrl;

  @Value("${rs.ext.filestore.root}")
  private String fileStoreRoot;

  @Value("${mail.maxEmailsPerSecond:5}")
  private Integer maxEmailsPerSecond;

  @Value("${mail.addressChunkSize:25}")
  private Integer addressChunkSize = 25;

  @Value("${max.tiff.conversionSize:8192000}")
  Long maxTiffConversionSize;

  @Value("${snapgene.web.url}")
  String snapGeneWebUrl;

  @Value("${dmptool.base.url}")
  URL dmpbaseUrl;

  @Value("${argos.url}")
  URL argosApiUrl;

  @Value("${chemistry.web.url}")
  String chemistryWebUrl;

  @Value("${aws.s3.hasS3Access}")
  private boolean hasS3Access;

  protected Logger log = LoggerFactory.getLogger(BaseConfig.class);

  protected abstract IMediaFactory mediaFactory();

  public BaseConfig() {
    log.info("in config {}", getClass().getName());
  }

  /** Default task executor for small async jobs */
  @Bean
  public TaskExecutor taskExecutor() {
    return new SimpleAsyncTaskExecutor();
  }

  @Bean
  public LoggingInitializer loggingInitializer() {
    return new LoggingInitializer();
  }

  @Bean
  public IAuditFileSearch auditQuerySearcher() {
    BasicLogQuerySearcher searcher =
        new BasicLogQuerySearcher(logFileTracker(), logLineContentProvider());
    return searcher;
  }

  @Bean
  public ILogResourceTracker logFileTracker() {
    return new LogFileTracker(logLineContentProvider());
  }

  @Bean
  public LogLineContentProvider logLineContentProvider() {
    return new LogLineContentProviderImpl();
  }

  @Bean(name = "velocityEngine")
  public VelocityEngineFactoryBean velocityFactoryBean() {
    VelocityEngineFactoryBean vEngine = new VelocityEngineFactoryBean();

    vEngine.setResourceLoaderPath(
        "classpath:velocityTemplates/,"
            + "classpath:velocityTemplates/textFieldElements,"
            + "classpath:velocityTemplates/messageAndNotificationEmails,"
            + "classpath:velocityTemplates/integrations,"
            + "classpath:velocityTemplates/MessageAndNotificationCloud,"
            + "classpath:velocityTemplates/mobile,"
            + "classpath:velocityTemplates/archive,"
            + "classpath:velocityTemplates/accountOperations,"
            + "classpath:velocityTemplates/slack,"
            + "file:"
            + velocityExtDir);

    return vEngine;
  }

  @Bean
  public IPropertyHolder propertyHolder() {
    return new PropertyHolder();
  }

  @Bean
  public SysadminCreateUserFormConfigurer sysadminCreateUserFormConfigurer() {
    return new SysadminCreateUserFormConfigurerImpl(propertyHolder());
  }

  /**
   * Creates a bean that massages HttpREsponse headers to enable caching. This bean can be
   * inactivated/deactivated via the rs.httpcache.enabled property.
   *
   * @return
   */
  @Bean
  ResponseUtil responseUtil() {
    return new ResponseUtil();
  }

  @Bean
  RSpaceRequestUpdateHandler collabShareRequestHandler() {
    return new CollabGroupShareRequestUpdateHandler();
  }

  @Bean
  RSpaceRequestUpdateHandler joinExistingCollabGroupRequestHandler() {
    return new JoinExistingCollGroupRequestUpdateHandler();
  }

  @Bean
  RSpaceRequestUpdateHandler joinGroupRequestHandler() {
    return new JoinGroupRequestUpdateHandler();
  }

  @Bean
  RSpaceRequestUpdateHandler createLabGroupRequestHandler() {
    return new CreateLabGroupRequestHandler();
  }

  @Bean
  RSpaceRequestUpdateHandler shareRecordRequestHandler() {
    return new ShareRecordRequestHandler();
  }

  @Bean
  public RequestFactory requestFactory(RichTextUpdater rtu) {
    RequestFactory rc = new RequestFactory();
    return rc;
  }

  void addRequestHandlers(RSpaceRequestManagerImpl impl) {
    List<RSpaceRequestUpdateHandler> handlers = new ArrayList<RSpaceRequestUpdateHandler>();
    handlers.add(collabShareRequestHandler());
    handlers.add(joinExistingCollabGroupRequestHandler());
    handlers.add(joinGroupRequestHandler());
    handlers.add(createLabGroupRequestHandler());
    handlers.add(shareRecordRequestHandler());
    impl.setRequestHandlers(handlers);
  }

  @Bean
  RSpaceRequestManager rspaceRequestManager() {
    RSpaceRequestManagerImpl rc = new RSpaceRequestManagerImpl();
    addRequestHandlers(rc);
    return rc;
  }

  @Bean
  public IApplicationInitialisor loadfromCSV() {
    return new LoadUsersFromCSVOnStartUpInitialisor();
  }

  @Bean
  public IApplicationInitialisor indexer() {
    return new LuceneSearchIndexInitialisor();
  }

  @Bean
  public IApplicationInitialisor sanityChecker() {
    return new SanityChecker();
  }

  @Bean
  public IApplicationInitialisor integrationsHandlerInitialisor() {
    return new IntegrationsHandlerInitialisor();
  }

  @Bean
  IApplicationInitialisor sampleTemplateAppInitialiser() {
    return new SampleTemplateAppInitialiser();
  }

  @Bean
  public IApplicationInitialisor dBDataIntegrityChecker() {
    return new DBDataIntegrityChecker();
  }

  @Bean(name = "sharedSnippetsFolderCreator")
  public IApplicationInitialisor sharedSnippetsFolderCreator() {
    return new GroupSharedSnippetsFolderAppInitialiser();
  }

  @Bean
  public PermissionFactory permissionFactory() {
    return new DefaultPermissionFactory();
  }

  @Bean
  public IRecordFactory recordFactory() {
    RecordFactory rf = new RecordFactory();
    rf.setModifiedByStrategy(IActiveUserStrategy.CHECK_OPERATE_AS);
    return rf;
  }

  @Bean
  public OperateAsUserLookup operateAsUserLookup() {
    return new OperateAsUserLookup();
  }

  @Bean
  public AuditTrailService auditTrailService() {
    AuditTrailImpl impl = new AuditTrailImpl();
    impl.setHistoryDao(new Log4jHistoryDAOImpl());
    impl.setUserLookup(operateAsUserLookup());
    return impl;
  }

  @SuppressWarnings("rawtypes")
  @Bean
  @Scope(WebApplicationContext.SCOPE_REQUEST)
  public PaginationCriteria<?> paginationCriteria() {
    return new PaginationCriteria();
  }

  @Bean
  @Scope(WebApplicationContext.SCOPE_REQUEST)
  public ArchiveExportConfig archiveExportConfig() {
    return new ArchiveExportConfig();
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public ExportObjectGenerator xmlDocumentor() {
    return new ExportObjectGenerator();
  }

  @Bean
  public ExportObjectWriter htmlWriter() {
    return new HTMLWriter();
  }

  @Bean
  public ArchiveImporterManager archiveImporter() {
    return new ArchiveImporterManagerImpl();
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public IArchiveParser archiveParser() {
    return new ArchiveParserImpl();
  }

  @Bean
  public ArchiveModelToDatabaseSaver archiveToDatabaseSaver() {
    return new ArchiveModelToDatabaseSaverImpl();
  }

  @Bean
  public ExportRemovalPolicy exportRemovalPolicy() {
    return new TimeLimitedExportRemovalPolicy();
  }

  @Bean
  @Scope(WebApplicationContext.SCOPE_REQUEST)
  public ExportToFileConfig pdfConfigInfo() {
    return new ExportToFileConfig();
  }

  /**
   * Configures archive service manage
   *
   * @return
   */
  @Bean
  public ArchiveExportServiceManager archiveManager() {
    XMLArchiveExportManagerServiceImpl mgr = new XMLArchiveExportManagerServiceImpl();
    List<ArchiveDataHandler> handlers = new ArrayList<ArchiveDataHandler>();
    handlers.add(messageArchiveHandler());
    handlers.add(userArchiveHandler());
    return mgr;
  }

  @Bean
  public ArchiveDataHandler userArchiveHandler() {
    return new UserArchiveDataHandler();
  }

  @Bean
  public ArchiveDataHandler messageArchiveHandler() {
    return new MessageArchiveDataHandler();
  }

  @Bean
  public LicenseRequestProcessor licenseRequestProcessor() {
    return new DefaultLicenseRequestProcessor();
  }

  @Bean
  public UserNameCreationStrategy userNameCreationStrategy() {
    if (isStandalone()) {
      return new UserNameFromFirstLastNameStrategy();
    } else {
      return new RequireValidUserNameStrategy();
    }
  }

  private boolean isStandalone() {
    return deploymentPropertyConfig.isStandalone();
  }

  private boolean isCloud() {
    return deploymentPropertyConfig.isCloud();
  }

  @Bean(name = "persistLoginTimeauthc")
  BaseShiroFormAuthFilterExt shiroFilter() {
    if (isStandalone()) {
      return standaloneLoginAuthFilterExt();
    } else {
      return new SSOShiroFormAuthFilterExt(remoteUserRetrievalPolicy());
    }
  }

  @Bean(name = "standaloneLoginAuthFilterExt")
  StandaloneShiroFormAuthFilterExt standaloneLoginAuthFilterExt() {
    return new StandaloneShiroFormAuthFilterExt();
  }

  /**
   * Creates a RemoteUserRetrievalPolicy. This shoould only be called if the RSpace is not
   * standalone, i.e has some SSO mechanism to login users.
   *
   * @return
   */
  protected abstract RemoteUserRetrievalPolicy remoteUserRetrievalPolicy();

  @Bean
  public MessageOrRequestCreatorManager messageOrRequestCreatorManager() {
    MessageOrRequestCreatorManagerImpl impl = new MessageOrRequestCreatorManagerImpl();
    List<RSpaceRequestOnCreateHandler> handlers = new ArrayList<RSpaceRequestOnCreateHandler>();
    handlers.add(collabGroupShareRequestCreateHandler());
    impl.setRequestHandlers(handlers);
    return impl;
  }

  @Bean
  public RSpaceRequestOnCreateHandler collabGroupShareRequestCreateHandler() {
    return new CollabGroupShareRequestCreateHandler();
  }

  /**
   * Environment-specific signup policy - cloud or default?
   *
   * @return
   */
  @Bean(name = "manualPolicy")
  public ISignupHandlerPolicy signupHandlerPolicy() {
    if (isCloud()) {
      return new CommunityManualUserSignupPolicy();
    } else {
      return new DefaultUserSignupPolicy();
    }
  }

  @Bean(name = "externalPolicy")
  public ISignupHandlerPolicy externalPolicy() {
    if (isCloud()) {
      return new ExternalOauthUserSignupPolicy();
    } else {
      return new DefaultUserSignupPolicy();
    }
  }

  @Bean(name = "postSignup")
  IPostUserSignup iPostUserCreationSetUp() {
    // if it's cloud version, we do the email verification.
    if (isCloud()) {
      return new CommunityPostSignupVerification();
    }
    // default handling
    if (StringUtils.isBlank(authorizedSignup)) {
      return new DefaultPostUserCreate();
    }
    // else create based on property settings.
    if (Boolean.parseBoolean(authorizedSignup)) {
      return new AuthorisedPostSignUp();
    } else {
      return new DefaultPostUserCreate();
    }
  }

  /**
   * @return
   */
  @Bean
  IAuditSearchResultPostProcessor auditSearchResultPostProcessor() {
    return new UpdateRecordNamePostProcessor();
  }

  @Bean("compositeFileStore")
  public FileStore compositeFileStore() throws IOException {
    FileStoreImpl impl =
        new FileStoreImpl(internalFileStore(), externalFileStoreLocator(), externalFileService());
    return impl;
  }

  @Bean
  public ExternalFileService externalFileService() {
    if ("LOCAL".equals(fileStoreType)) {
      return ExternalFileService.NOOP;
    } else {
      log.info("Creating real external file service");
      return new ExternalFileServiceImpl();
    }
  }

  @Bean
  public InternalFileStore internalFileStore() throws IOException {
    return new InternalFileStoreImpl();
  }

  @Bean
  public ExternalFileStoreLocator externalFileStoreLocator() {
    if ("LOCAL".equals(fileStoreType)) {
      return ExternalFileStoreLocator.NOOP_ExternalFileStoreLocator;
    } else {
      log.info("Setting external file store locator for {}", fileStoreType);
      return new ExternalFileStoreLocatorImpl(
          ExternalFileStoreProvider.valueOf(fileStoreType.trim()), externalFileStore());
    }
  }

  /**
   * Get the correct file store depending on fileStoreType
   *
   * @return
   * @throws IOException
   */
  @Bean
  public ExternalFileStore externalFileStore() {
    if (EGNYTE.name().equals(fileStoreType)) {
      log.info(
          "Creating Egnyte FileStore at URL {} and root folder {}",
          fileStoreBaseUrl,
          fileStoreRoot);
      return new EgnyteFileStoreAdapter(fileStoreBaseUrl, fileStoreRoot);
    }
    return null;
  }

  @Bean(name = "userNameToUserConnection")
  @SessionScope
  public Map<String, UserConnection> userUserConnectionMap() {
    return new HashMap<String, UserConnection>();
  }

  /**
   * We're not indexing files locally if they're stored in external file service
   *
   * @return
   */
  @Bean
  public IFileIndexer fileIndexer() {
    if (!"LOCAL".equals(fileStoreType)) {
      return IFileIndexer.NOOP_INDEXER;
    } else {
      return new FileIndexer();
    }
  }

  @Bean
  public FileSearchStrategy fileSearchStrategy() {
    if (!"LOCAL".equals(fileStoreType)) {
      return new EgnyteFileSearcher(fileStoreBaseUrl, fileStoreRoot, externalFileStoreLocator());
    } else {
      return new LuceneSearchStrategy();
    }
  }

  @Bean
  public MovePermissionChecker movePermissionChecer() {
    return new MovePermissionChecker();
  }

  @Bean
  FieldLinksEntitiesSynchronizer fieldLinksEntitiesSynchronizer() {
    return new FieldLinksEntitySyncImpl();
  }

  @Bean
  public DocumentSharedStateCalculator documentSharedStateCalculator() {
    return new MultipleSharesPermittedDocSharedStatusCalculatorImpl();
  }

  @Bean
  IReauthenticator reauthenticatorImpl() {
    return new ReauthenticatorImpl();
  }

  /** For PDF export */
  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public HTMLStringGenerator HTMLStringGenerator() {
    return new HTMLStringGeneratorForExport();
  }

  @Bean
  public ImageRetrieverHelper imageRetrieverHelper() {
    return new ImageRetrieverHelperImpl();
  }

  @Bean
  public MultipartResolver multipartResolver() {
    CommonsMultipartResolver rc = new CommonsMultipartResolver();
    Long defaultLimit = 10_000_000l; // 10Mb default default
    try {
      long maxSize = Long.parseLong(maxUploadSize);
      rc.setMaxUploadSize(maxSize);
    } catch (NumberFormatException nfe) {
      log.warn(
          "Couldn't set max file upload size [{}], using default [{}]",
          maxUploadSize,
          defaultLimit);
      rc.setMaxUploadSize(defaultLimit);
    }
    return rc;
  }

  @Bean
  public ExportConfigurer PDFExportConfigurer() {
    return new ExportConfigurerImpl();
  }

  @Bean
  TextEncryptor encryptor() {
    return new SymmetricTextEncryptor(apiTokenEncryptionKey);
  }

  @Bean
  IExportUtils pdfUtils() {
    return new ExportUtils();
  }

  @Bean
  IMessageAndNotificationTracker messageAndNotificationTracker() {
    return new MessageAndNotificationTracker();
  }

  @Bean()
  IntegrationsHandler IntegrationsHandler() {
    return new IntegrationsHandlerImpl();
  }

  @Bean
  protected IApplicationInitialisor fileStoreRootDetector() {
    return new FileStoreRootDetector();
  }

  @Bean(name = "customFormAppInitialiser")
  public IApplicationInitialisor customForms() {
    return new CustomFormAppInitialiser();
  }

  @Bean
  public UserContentUpdater userContentUpdater() {
    return new UserContentUpdaterImpl();
  }

  @Bean
  LiveLiqUpdater liveLiqUpdater() {
    LiveLiqUpdater updater = new LiveLiqUpdater("sqlUpdates/liquibase-live-master.xml");
    return updater;
  }

  @Bean()
  @Lazy()
  UserImporter userImporter() {
    return new UserImporterImpl();
  }

  @Bean
  FolderTreeImporter folderTreeImporter() {
    return new FolderTreeImporterImpl();
  }

  @Bean
  RSpaceDocumentCreator documentCreator() {
    return new DocumentImporterFromWord2HTML();
  }

  @Bean
  ExportImport exportImport() {
    ExportImportImpl impl = new ExportImportImpl();
    return impl;
  }

  @Bean
  SlackService slackService() {
    return new SlackServiceImpl();
  }

  @Bean
  ExportProcessor msWordProcessor() {
    return new MSWordProcessor();
  }

  @Bean
  IContentInitialiserUtils contentInitialiserUtils() {
    return new ContentInitialiserUtilsImpl();
  }

  @Bean(name = "defaultUserFolderCreator")
  UserFolderCreator userContentCreator() {

    return new DefaultUserFolderCreator();
  }

  @Bean
  ExternalMessageHandlerImpl externalMessageHandlerImpl() {
    return new ExternalMessageHandlerImpl();
  }

  @Bean
  ExternalMessageSender slackExternalMessageSender() {
    return new SlackMessageSender();
  }

  @Bean
  ExternalMessageSender msteamsExternalMessageSender() {
    return new MsTeamsMessageSender();
  }

  @Bean
  ExternalMessageSenderFactory externalMessageSenderFactory() {
    ExternalMessageSenderFactoryImpl fac = new ExternalMessageSenderFactoryImpl();
    List<ExternalMessageSender> senders = new ArrayList<>();
    senders.add(slackExternalMessageSender());
    senders.add(msteamsExternalMessageSender());
    fac.setMessageSenders(senders);
    return fac;
  }

  /**
   * Enables fresh object creation for new repository object per invocation
   *
   * @return
   */
  @Bean()
  public FactoryBean<Object> serviceLocatorFactoryBean() {
    ServiceLocatorFactoryBean factoryBean = new ServiceLocatorFactoryBean();
    factoryBean.setServiceLocatorInterface(RepositoryFactory.class);
    return factoryBean;
  }

  @Bean
  public IRepositoryConfigFactory repoConfigFactory() {
    return new RepositoryConfigFactoryImpl();
  }

  @Bean
  ExternalAuthTokenVerifier ExternalAuthTokenVerifier() {
    return new GoogleTokenVerifier();
  }

  @Bean(name = "manualLoginHelper")
  LoginHelper manualoginHelper() {
    return new ManualLoginHelperImpl();
  }

  @Bean(name = "postOAuthLoginHelper")
  LoginHelper postOAuthLoginHelper() {
    return new PostOAuthLoginHelperImpl();
  }

  @Bean
  public UserExternalIdResolverImpl userExternalIdResolverImpl() {
    return new UserExternalIdResolverImpl();
  }

  @Bean
  RepositoryDepositHandlerImpl repositoryDepositHandlerImpl() {
    return new RepositoryDepositHandlerImpl();
  }

  @Bean
  IAsyncArchiveDepositor IAsyncDepositor() {
    return new AsyncDepositorImpl();
  }

  @Bean
  DMPUpdateHandler dmpUpdateHandler() {
    return new DMPUpdateHandler();
  }

  @Bean
  DeletionExecutor deletionExecutor() {
    return new DeleteFromSharedFolderExecutor();
  }

  @Bean
  FolderDeletionOrderPolicy deletionOrderPolicy() {
    return new DeleteFolderFromSharedFolderPolicy();
  }

  @Bean
  ApiAvailabilityHandler ApiAvailabilityHandler() {
    return new ApiAvailabilityHandlerImpl();
  }

  @Bean
  PostSigningManager postRecordSigningExportHash() {
    return new PostRecordSigningExportHash(exportImport());
  }

  @Bean
  PostArchiveCompletion standardPostExportCompletionImpl() {
    return new StandardPostExportCompletionImpl();
  }

  @Bean
  PiChangeHandler postPiChangeHandler() {
    return new PiChangeHandlerImpl();
  }

  @Bean
  AuditTrailHandler auditTrailHandler() {
    return new AuditTrailHandlerImpl();
  }

  @Bean
  FormIconWriter formIconWriter() {
    return new FormIconWriter();
  }

  @Bean
  FormImporter formImporter() {
    return new FormImporterImpl();
  }

  @Bean
  ArchiveNamingStrategy ArchiveNamingStrategy() {
    return new ArchiveNamingStrategy();
  }

  @Bean
  DocumentHTMLPreviewHandler documentHTMLPreviewHandler() {
    return new DocumentHTMLPreviewHandlerImpl();
  }

  @Bean
  ArchiveRemover archiveRemover() {
    return new ArchiveRemover();
  }

  @Bean
  ImageProcessor imageProcessor() {
    return new ImageProcessorImpl();
  }

  @Bean
  public EmailBroadcast emailBroadcast() {
    if (Boolean.parseBoolean(emailEnabled)) {
      return new EmailBroadcastImp(getMaxEmailsPerSecond(), getEmailAddressChunkSize());
    }
    return new DevEmailSenderImpl();
  }

  @Bean
  public Broadcaster broadcaster() {
    if (Boolean.parseBoolean(emailEnabled)) {
      return new EmailBroadcastImp(getMaxEmailsPerSecond(), getEmailAddressChunkSize());
    } else {
      return new DevBroadCaster();
    }
  }

  private Integer getMaxEmailsPerSecond() {
    return maxEmailsPerSecond != null ? maxEmailsPerSecond : 5;
  }

  private Integer getEmailAddressChunkSize() {
    return addressChunkSize != null && addressChunkSize > 0 ? addressChunkSize : 25;
  }

  @Bean("validator")
  LocalValidatorFactoryBean localValidatorFactoryBean() {
    return new LocalValidatorFactoryBean();
  }

  @Bean
  SysadminUserCreationHandler sysadminUserCreationHandler() {
    return new SysadminUserCreationHandlerImpl();
  }

  @Bean
  DetailedRecordInformationProvider detailedRecordInformationProviderImpl() {
    return new DetailedRecordInformationProviderImpl();
  }

  @Bean
  SharingHandler sharingHandler() {
    return new SharingHandlerImpl();
  }

  @Bean
  PaginationSettingsPreferences paginationSettingsPreferences() {
    return new PaginationSettingsPreferences();
  }

  @Bean
  WorkspaceViewModePreferences workspaceViewModePreferences() {
    return new WorkspaceViewModePreferences();
  }

  @Bean
  ExternalFileImporter evernoteFileImporter() {
    return new EvernoteEnexImporter();
  }

  @Bean
  WhiteListIPChecker whiteListIPChecker() {
    return new WhiteListIPCheckerImpl();
  }

  @Bean
  PostLoginHandler postLoginHandler() {
    PostLoginHandlerImpl rc = new PostLoginHandlerImpl();

    List<PostFirstLoginAction> postFirstLoginActions = new ArrayList<>();

    postFirstLoginActions.add(egnyteAuthConnector());
    postFirstLoginActions.add(exampleContentAction());

    rc.setPostFirstLoginActions(postFirstLoginActions);

    List<PostAnyLoginAction> postAnyLoginActions = new ArrayList<>();
    postAnyLoginActions.add(egnyteAuthConnector());
    rc.setPostAnyLoginActions(postAnyLoginActions);

    return rc;
  }

  @Bean
  PostFirstLoginAction exampleContentAction() {
    return new ExampleContentAction();
  }

  @Bean
  EgnyteAuthConnector egnyteAuthConnector() {
    return new EgnyteAuthConnectorImpl();
  }

  @Bean
  GroupPermissionUtils groupPermissionUtils() {
    return new GroupPermissionUtils();
  }

  @Bean
  SystemPropertyPermissionManager systemPropertyPermissionUtils() {
    return new SystemPropertyPermissionManagerImpl();
  }

  @Bean
  ImportStrategy importUsersAndRecords() {
    return new ImportUsersAndRecords();
  }

  @Bean
  ImportStrategy importRecordsOnly() {
    return new ImportRecordsOnly();
  }

  @Bean
  WopiDiscoveryServiceHandler wopiDiscoveryServiceHandler() {
    return new WopiDiscoveryServiceHandler();
  }

  @Bean
  WopiDiscoveryProcessor wopiDiscoveryProcessor() {
    return new WopiDiscoveryProcessor();
  }

  @Bean
  WopiProofKeyValidator wopiProofKeyValidator() {
    return new WopiProofKeyValidator();
  }

  @Bean
  AuditTrailSearchResultCsvGenerator auditTrailSearchResultCsvGenerator() {
    return new AuditTrailSearchResultCsvGenerator();
  }

  /**
   * Creates a Snapgene WS client or a no-op implementation if snapgene.web.url property not set
   *
   * @return SnapgeneWSClient
   */
  @Bean
  SnapgeneWSClient snapgeneWSClient() {
    return Option.of(snapGeneWebUrl)
        .toTry()
        .mapTry(URI::new)
        .peek(uri -> log.info("Creating SnapgeneClient at {}", uri))
        .<SnapgeneWSClient>map(
            uri -> new SnapgeneWSClientImpl(uri, baseDocConverterConfig.customerIDSupplier()))
        .getOrElse(this::noopSnapgene);
  }

  private SnapgeneWSNoop noopSnapgene() {
    log.info("Creating a No-op Snapgene client, as property 'snapgene.web.url' was not set");
    return new SnapgeneWSNoop();
  }

  void setSnapgeneUrl(String url) {
    this.snapGeneWebUrl = url;
  }

  void setChemistryWSUrl(String url) {
    this.chemistryWebUrl = url;
  }

  @Bean
  StrictEmailContentGenerator strictEmailContentGenerator() {
    return new StrictEmailContentGenerator();
  }

  @Bean
  QuantityUtils quantityUtils() {
    return new QuantityUtils();
  }

  @Bean
  OriginRefererChecker originRefererChecker() {
    return new OriginRefererCheckerImpl();
  }

  @Bean
  ArchiveExportPlanner ArchiveExportPlannerImpl() {
    return new ArchiveExportPlannerImpl();
  }

  @Bean
  public S3Utilities s3Utilities() {
    if (hasS3Access) {
      return new S3UtilitiesImpl();
    } else {
      return S3Utilities.NOOP_S3Utilities;
    }
  }

  @Bean
  public DMPToolDMPProvider dmpClient() {
    return new DMPToolDMPProviderImpl(dmpbaseUrl);
  }

  @Bean
  public ArgosDMPProvider argosDMPProvider() {
    return new ArgosDMPProvider(argosApiUrl);
  }

  /**
   * Creates a new {@link IRepository} bean for Zenodo.
   *
   * @return the repository adapter
   */
  @Bean(name = "zenodoRepository")
  public IRepository zenodoRepository() {
    ZenodoRSpaceRepository rc = new ZenodoRSpaceRepository();
    log.info("Setting in repository implementation {}", rc);
    return rc;
  }

  @Bean(name = "dataCiteConnector")
  public DataCiteConnector getDataCiteConnector() {
    return new DataCiteConnectorImpl();
  }

  @Bean(name = "pyrat")
  public PyratClient pyratClient() {
    return new PyratClient();
  }

  @Bean
  public BreadcrumbGenerator getBreadCrumbGenerator() {
    return new DefaultBreadcrumbGenerator();
  }
}
