package com.researchspace.webapp.controller;

import static com.researchspace.model.dto.IntegrationInfo.getAppNameFromIntegrationName;
import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DRYAD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIGSHARE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.ZENODO_APP_NAME;

import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.apps.UserAppConfig;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.repository.LinkedDMP;
import com.researchspace.model.repository.RepoUIConfigInfo;
import com.researchspace.repository.spi.RepositoryOperationResult;
import com.researchspace.service.DMPManager;
import com.researchspace.service.RepositoryDepositHandler;
import com.researchspace.service.UserAppConfigManager;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Controller for repository configuration specific for repository setup rather than export. */
@Slf4j
@Controller
@RequestMapping("/repository")
public class RepositoryConfigurationController extends BaseController {

  private @Autowired RepositoryDepositHandler depositHandler;
  private @Autowired UserAppConfigManager userAppConfigMgr;
  private @Autowired DMPManager dmpManager;

  /**
   * Gets information on active repositories
   *
   * @return
   * @throws MalformedURLException
   */
  @GetMapping("/ajax/repo/uiConfig")
  @ResponseBody
  public List<RepoUIConfigInfo> getAllActiveRepositories() throws MalformedURLException {
    User user = userManager.getAuthenticatedUserInSession();

    Map<String, IntegrationInfo> rc = getRepositoryIntegrationInfos(user);

    List<IntegrationInfo> activeInfos =
        rc.entrySet().stream()
            .filter(activeRepositories())
            .map(Entry::getValue)
            .collect(Collectors.toList());
    List<RepoUIConfigInfo> uiConfigs = new ArrayList<>();
    for (IntegrationInfo inf : activeInfos) {
      RepoUIConfigInfo info = getRepoUIConfigInfo(user, inf.getName());
      info.setOptions(inf.getOptions());
      info.setDisplayName(inf.getDisplayName());
      uiConfigs.add(info);
    }
    if (isDMPEnabled(user)) {
      List<DMPUser> dmpUsers = dmpManager.findDMPsForUser(user);
      if (!dmpUsers.isEmpty()) {
        List<LinkedDMP> linkedDMPdt =
            dmpUsers.stream()
                .map(dmpu -> new LinkedDMP(dmpu.getId(), dmpu.getTitle(), dmpu.getDmpId()))
                .collect(Collectors.toList());
        for (RepoUIConfigInfo info : uiConfigs) {
          info.setLinkedDMPs(linkedDMPdt);
        }
      }
    }
    return uiConfigs;
  }

  // this only works for Dataverse just now
  @GetMapping("/ajax/testRepository/{appConfigId}")
  @ResponseBody
  public String testRepository(@PathVariable("appConfigId") Long appConfigSetId)
      throws MalformedURLException {
    StringBuilder errorStringBuilder = new StringBuilder();
    Optional<AppConfigElementSet> cfg =
        userAppConfigMgr.findByAppConfigElementSetId(appConfigSetId);
    validateCfgIfExists(appConfigSetId, errorStringBuilder, cfg);
    if (errorStringBuilder.length() > 0) {
      throw new IllegalArgumentException("Invalid app Id");
    }
    App app = cfg.get().getUserAppConfig().getApp();
    validateAppIsRepository(errorStringBuilder, app);
    if (errorStringBuilder.length() > 0) {
      throw new IllegalArgumentException("Invalid app Id");
    }
    RepositoryOperationResult result = depositHandler.testDataverseConnection(cfg.get());
    if (result.isSucceeded()) {
      return "Success! " + result.getMessage();
    } else {
      return "Failed! " + result.getMessage();
    }
  }

  private RepoUIConfigInfo getRepoUIConfigInfo(User user, String integrationInfoName)
      throws MalformedURLException {
    RepoUIConfigInfo info = null;
    if (DATAVERSE_APP_NAME.equals(integrationInfoName)) {
      // todo need to get this by each dataverse
      UserAppConfig appCfg =
          userAppConfigMgr.getByAppName(getAppNameFromIntegrationName(integrationInfoName), user);
      var appConfigElementSet =
          appCfg.getAppConfigElementSets().stream()
              .filter(
                  set -> set.getApp().toIntegrationInfoName().equalsIgnoreCase(integrationInfoName))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No app config element set found for app " + integrationInfoName));
      info = depositHandler.getDataverseRepoUIConfigInfo(appConfigElementSet, user);
    } else if (FIGSHARE_APP_NAME.equals(integrationInfoName)) {
      info = depositHandler.getFigshareRepoUIConfigInfo(user);
    } else if (DRYAD_APP_NAME.equals(integrationInfoName)) {
      info = depositHandler.getDryadRepoUIConfigInfo(user);
    } else if (ZENODO_APP_NAME.equals(integrationInfoName)) {
      info = depositHandler.getZenodoRepoUIConfigInfo(user);
    } else if (DIGITAL_COMMONS_DATA_APP_NAME.equals(integrationInfoName)) {
      info = depositHandler.getDigitalCommonsDataRepoUIConfigInfo(user);
    }
    if (info == null) {
      throw new IllegalStateException("unknown or undefined integration");
    }
    return info;
  }

  private Map<String, IntegrationInfo> getRepositoryIntegrationInfos(User user) {
    Map<String, IntegrationInfo> rc = new HashMap<>();
    rc.put(DATAVERSE_APP_NAME, integrationsHandler.getIntegration(user, DATAVERSE_APP_NAME));
    rc.put(FIGSHARE_APP_NAME, integrationsHandler.getIntegration(user, FIGSHARE_APP_NAME));
    rc.put(DRYAD_APP_NAME, integrationsHandler.getIntegration(user, DRYAD_APP_NAME));
    rc.put(ZENODO_APP_NAME, integrationsHandler.getIntegration(user, ZENODO_APP_NAME));
    rc.put(
        DIGITAL_COMMONS_DATA_APP_NAME,
        integrationsHandler.getIntegration(user, DIGITAL_COMMONS_DATA_APP_NAME));
    return rc;
  }

  private Predicate<? super Entry<String, IntegrationInfo>> activeRepositories() {
    return e ->
        e.getValue() != null
            && e.getValue().isAvailable()
            && e.getValue().isEnabled()
            && isConfiguredActive(e);
  }

  private boolean isConfiguredActive(Entry<String, IntegrationInfo> e) {
    if (FIGSHARE_APP_NAME.equals(e.getKey())) {
      return e.getValue().isOauthConnected();
    } else if (DATAVERSE_APP_NAME.equals(e.getKey())) {
      return e.getValue().hasOptions();
    } else if (DRYAD_APP_NAME.equals(e.getKey())) {
      return e.getValue().isOauthConnected();
    } else if (ZENODO_APP_NAME.equals(e.getKey())) {
      return e.getValue().hasOptions();
    } else if (DIGITAL_COMMONS_DATA_APP_NAME.equals(e.getKey())) {
      return e.getValue().hasOptions();
    } else {
      return false;
    }
  }

  private void validateAppIsRepository(StringBuilder errorBuilder, App app) {
    if (!app.isRepositoryApp()) {
      errorBuilder.append(
          getText("invalid.app.choice", new String[] {app.getName(), "Repository"}));
    }
  }

  private void validateCfgIfExists(
      Long appConfigSetId, StringBuilder erbf, Optional<AppConfigElementSet> cfg) {
    if (appConfigSetId > 0 && cfg.isEmpty()) {
      erbf.append(getResourceNotFoundMessage("Dataverse config", appConfigSetId));
    }
  }
}
