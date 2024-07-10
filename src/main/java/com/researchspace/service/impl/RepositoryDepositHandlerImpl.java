package com.researchspace.service.impl;

import static com.researchspace.model.dto.IntegrationInfo.getAppNameFromIntegrationName;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.model.repository.RepoUIConfigInfo;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.LicenseConfigInfo;
import com.researchspace.repository.spi.RepositoryConfig;
import com.researchspace.repository.spi.RepositoryConfigurer;
import com.researchspace.repository.spi.RepositoryOperationResult;
import com.researchspace.repository.spi.Subject;
import com.researchspace.repository.spi.properties.RepoProperty;
import com.researchspace.service.AppNotAuthorisedException;
import com.researchspace.service.IAsyncArchiveDepositor;
import com.researchspace.service.IRepositoryConfigFactory;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.RepositoryDepositHandler;
import com.researchspace.service.RepositoryFactory;
import com.researchspace.service.UserAppConfigManager;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.repositories.DigitalCommonsDataUIConnectionConfig;
import com.researchspace.webapp.controller.repositories.DryadUIConnectionConfig;
import com.researchspace.webapp.controller.repositories.FigshareUIConnectionConfig;
import com.researchspace.webapp.controller.repositories.RSDataverseConnectionConfig;
import com.researchspace.webapp.controller.repositories.ZenodoUIConnectionConfig;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class RepositoryDepositHandlerImpl implements RepositoryDepositHandler {

  @Autowired private IPropertyHolder propertyHolder;

  private @Autowired UserManager userManager;
  private @Autowired RepositoryFactory repoFactory;

  public void setRepoFactory(RepositoryFactory repoFactory) {
    this.repoFactory = repoFactory;
  }

  private @Autowired IRepositoryConfigFactory repoCfgFactory;

  public void setRepoCfgFactory(IRepositoryConfigFactory repoCfgFactory) {
    this.repoCfgFactory = repoCfgFactory;
  }

  private @Autowired UserAppConfigManager userAppConfigMgr;

  void setUserAppConfigMgr(UserAppConfigManager userAppConfigMgr) {
    this.userAppConfigMgr = userAppConfigMgr;
  }

  private @Autowired UserConnectionManager userConnectionManager;

  private @Autowired IntegrationsHandler handler;

  @Autowired IAsyncArchiveDepositor asyncDepositor;

  @Override
  public RepositoryOperationResult sendDocumentToRepository(
      RepoDepositConfig archiveConfig,
      Optional<AppConfigElementSet> cfg,
      App app,
      Future<EcatDocumentFile> document)
      throws IOException, ExecutionException, InterruptedException {
    User subject = userManager.getAuthenticatedUserInSession();
    checkConnectionState(app, subject);

    IRepository repository = repoFactory.getRepository(getRepoName(app));
    RepositoryConfig repoConnectionInfo = getRepoConnectionInfo(cfg, app, subject);
    repository.configure(repoConnectionInfo);
    repository.testConnection();

    asyncDepositor.depositDocument(
        app, subject, repository, archiveConfig, repoConnectionInfo, document);
    return null;
  }

  @Override
  public RepositoryOperationResult sendArchiveToRepository(
      RepoDepositConfig archiveConfig,
      Optional<AppConfigElementSet> cfg,
      App app,
      Future<ArchiveResult> archive)
      throws IOException, ExecutionException, InterruptedException {
    User subject = userManager.getAuthenticatedUserInSession();
    checkConnectionState(app, subject);

    IRepository repository = repoFactory.getRepository(getRepoName(app));
    RepositoryConfig repoConnectionInfo = getRepoConnectionInfo(cfg, app, subject);
    repository.configure(repoConnectionInfo);
    repository.testConnection();

    asyncDepositor.depositArchive(
        app, subject, repository, archiveConfig, repoConnectionInfo, archive);
    return null;
  }

  private RepositoryConfig getRepoConnectionInfo(
      Optional<AppConfigElementSet> cfg, App app, User subject) throws MalformedURLException {
    RepositoryConfig repoCfg = null;
    if (cfg.isPresent() && app.getName().equals(App.APP_DATAVERSE)) {
      repoCfg =
          repoCfgFactory.createRepositoryConfigFromAppCfg(
              new RSDataverseConnectionConfig(cfg.get()), subject);
    } else if (app.getName().equalsIgnoreCase(App.APP_FIGSHARE)) {
      FigshareUIConnectionConfig figshareUIConnectionConfig =
          new FigshareUIConnectionConfig(userConnectionManager, subject);
      repoCfg =
          repoCfgFactory.createRepositoryConfigFromAppCfg(figshareUIConnectionConfig, subject);
    } else if (app.getName().equalsIgnoreCase(App.APP_DRYAD)) {
      DryadUIConnectionConfig dryadUIConnectionConfig =
          new DryadUIConnectionConfig(userConnectionManager, subject, this.propertyHolder);
      repoCfg = repoCfgFactory.createRepositoryConfigFromAppCfg(dryadUIConnectionConfig, subject);
    } else if (app.getName().equalsIgnoreCase(App.APP_ZENODO)) {
      ZenodoUIConnectionConfig zenodoUIConnectionConfig =
          new ZenodoUIConnectionConfig(userConnectionManager, subject, this.propertyHolder);
      repoCfg = repoCfgFactory.createRepositoryConfigFromAppCfg(zenodoUIConnectionConfig, subject);
    } else if (app.getName().equalsIgnoreCase(App.APP_DIGITAL_COMMONS_DATA)) {
      DigitalCommonsDataUIConnectionConfig dcdUIConnectionConfig =
          new DigitalCommonsDataUIConnectionConfig(userConnectionManager, subject, this.propertyHolder);
      repoCfg = repoCfgFactory.createRepositoryConfigFromAppCfg(dcdUIConnectionConfig, subject);
    } else {
      throw new IllegalArgumentException("Unknown or unconfigured repository: " + app.getName());
    }
    return repoCfg;
  }

  protected void checkConnectionState(App app, User subject) {
    if (isConnectedApp(app)) {
      var integration = handler.getIntegration(subject, app.toIntegrationInfoName());
      if (!integration.isOauthConnected()) {
        String msg =
            String.format(
                "RSpace is not authorised to connect to App %s."
                    + "  Please authorise on the Apps page and retry.",
                app.getLabel());
        throw new AppNotAuthorisedException(msg);
      }
    }
  }

  // App is authorised in UserConnection
  private boolean isConnectedApp(App app) {
    return App.APP_FIGSHARE.equals(app.getName());
  }

  private String getRepoName(App app) {
    return app.getUniqueName() + "Repository";
  }

  @Override
  public RepositoryOperationResult testDataverseConnection(AppConfigElementSet appSet)
      throws MalformedURLException {
    User subject = userManager.getAuthenticatedUserInSession();
    checkConnectionState(appSet.getUserAppConfig().getApp(), subject);
    App app = appSet.getApp();
    RSDataverseConnectionConfig connectionCfg = new RSDataverseConnectionConfig(appSet);
    RepositoryConfig repoCfg =
        repoCfgFactory.createRepositoryConfigFromAppCfg(connectionCfg, subject);
    IRepository repo = repoFactory.getRepository(getRepoName(app));
    repo.configure(repoCfg);
    RepositoryOperationResult result = repo.testConnection();
    if (!result.isSucceeded()) {
      log.warn("Test connection to Dataverse failed - {}", result.getMessage());
    }
    return result;
  }

  @Override
  public RepoUIConfigInfo getDataverseRepoUIConfigInfo(AppConfigElementSet appSet, User subject)
      throws MalformedURLException {
    App app = appSet.getApp();
    checkConnectionState(app, userManager.getAuthenticatedUserInSession());
    RSDataverseConnectionConfig cfg = new RSDataverseConnectionConfig(appSet);
    RepositoryConfig repoConnectionInfo =
        repoCfgFactory.createRepositoryConfigFromAppCfg(cfg, subject);
    return getRepositoryConfiguration(app, repoConnectionInfo);
  }

  @Override
  public RepoUIConfigInfo getFigshareRepoUIConfigInfo(User subject) throws MalformedURLException {
    UserAppConfig appCfg =
        userAppConfigMgr.getByAppName(
            getAppNameFromIntegrationName(IntegrationsHandler.FIGSHARE_APP_NAME), subject);
    checkConnectionState(appCfg.getApp(), userManager.getAuthenticatedUserInSession());
    FigshareUIConnectionConfig cfg = new FigshareUIConnectionConfig(userConnectionManager, subject);
    RepositoryConfig repoConnectionInfo =
        repoCfgFactory.createRepositoryConfigFromAppCfg(cfg, subject);
    return getRepositoryConfiguration(appCfg.getApp(), repoConnectionInfo);
  }

  @Override
  public RepoUIConfigInfo getDryadRepoUIConfigInfo(User user) throws MalformedURLException {
    UserAppConfig appCfg =
        userAppConfigMgr.getByAppName(
            getAppNameFromIntegrationName(IntegrationsHandler.DRYAD_APP_NAME), user);
    checkConnectionState(appCfg.getApp(), userManager.getAuthenticatedUserInSession());
    DryadUIConnectionConfig cfg =
        new DryadUIConnectionConfig(userConnectionManager, user, propertyHolder);
    RepositoryConfig repoConnectionInfo =
        repoCfgFactory.createRepositoryConfigFromAppCfg(cfg, user);
    return getRepositoryConfiguration(appCfg.getApp(), repoConnectionInfo);
  }

  @Override
  public RepoUIConfigInfo getZenodoRepoUIConfigInfo(User user) throws MalformedURLException {
    UserAppConfig appCfg =
        userAppConfigMgr.getByAppName(
            getAppNameFromIntegrationName(IntegrationsHandler.ZENODO_APP_NAME), user);
    checkConnectionState(appCfg.getApp(), userManager.getAuthenticatedUserInSession());
    ZenodoUIConnectionConfig cfg =
        new ZenodoUIConnectionConfig(userConnectionManager, user, propertyHolder);
    RepositoryConfig repoConnectionInfo =
        repoCfgFactory.createRepositoryConfigFromAppCfg(cfg, user);
    return getRepositoryConfiguration(appCfg.getApp(), repoConnectionInfo);
  }

  // makes API calls to repository to get static info about licenses, subjects etc
  private RepoUIConfigInfo getRepositoryConfiguration(App app, RepositoryConfig repoCfg) {
    IRepository repo = repoFactory.getRepository(getRepoName(app));
    repo.configure(repoCfg);
    RepositoryConfigurer configurer = repo.getConfigurer();

    List<Subject> subjects = configurer.getSubjects();
    LicenseConfigInfo license = configurer.getLicenseConfigInfo();
    List<RepoProperty> otherProperties = new ArrayList<>(configurer.getOtherProperties().values());
    return new RepoUIConfigInfo(app.getName(), subjects, license, otherProperties);
  }
}
