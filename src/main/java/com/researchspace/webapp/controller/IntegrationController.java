package com.researchspace.webapp.controller;

import static com.researchspace.service.IntegrationsHandler.ARGOS_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.CLUSTERMARKET_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DATAVERSE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DIGITAL_COMMONS_DATA_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPONLINE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DMPTOOL_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.DRYAD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.EGNYTE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.EGNYTE_DOMAIN_SETTING;
import static com.researchspace.service.IntegrationsHandler.EVERNOTE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.FIGSHARE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.GITHUB_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.JOVE_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.MSTEAMS_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.NEXTCLOUD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.OMERO_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.OWNCLOUD_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PROTOCOLS_IO_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.PYRAT_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.SLACK_APP_NAME;
import static com.researchspace.service.IntegrationsHandler.ZENODO_APP_NAME;
import static org.apache.commons.lang.StringUtils.join;

import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.preference.URLPreferenceValidator;
import com.researchspace.service.impl.IntegrationsHandlerImpl;
import com.researchspace.session.SessionAttributeUtils;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpSession;
import org.apache.commons.collections.MapUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing integrations set up by particular user To be used on 'Apps' tab, where
 * user can see (and update) their integrations, but also across the app to check if given
 * functionality is enabled.
 */
@RestController
@RequestMapping("/integration/")
public class IntegrationController extends BaseController {
  /**
   * Gets {@link IntegrationInfo} for the specified integration type.
   *
   * @param name an integration name, given by the name of the relevant {@link Preference} enum
   *     name.
   * @return An {@link AjaxReturnObject}. If no integration with the give name is found, returns an
   *     {@link ErrorList} if no Integration is found.
   */
  @GetMapping("/integrationInfo")
  public AjaxReturnObject<IntegrationInfo> getIntegrationInfo(
      @RequestParam(value = "name") String name) {
    User user = userManager.getAuthenticatedUserInSession();
    boolean isValidIntegration = integrationsHandler.isValidIntegration(name);
    if (!isValidIntegration) {
      String errorMsg = getText("errors.invalid", name);
      ErrorList msg = ErrorList.of(errorMsg);
      log.warn(errorMsg);
      return new AjaxReturnObject<>(null, msg);
    }
    IntegrationInfo info = integrationsHandler.getIntegration(user, name);
    return new AjaxReturnObject<>(info, null);
  }

  /**
   * Gets {@link IntegrationInfo}s for the specified integration type.s
   *
   * @param names am array of integration names, given by the name of the relevant {@link
   *     Preference} enum name.
   * @return An {@link AjaxReturnObject}. If no integrations are found, returns an {@link
   *     ErrorList}.
   */
  @GetMapping("/integrationInfos")
  public AjaxReturnObject<List<IntegrationInfo>> getIntegrationInfos(
      @RequestParam(value = "name[]") String[] names) {
    List<IntegrationInfo> infos = new ArrayList<>();
    for (String name : names) {
      AjaxReturnObject<IntegrationInfo> info = getIntegrationInfo(name);
      if (info.getData() != null) {
        infos.add(info.getData());
      }
    }
    if (infos.size() == 0) {
      String text = "There were no valid Apps : " + join(names, ",");
      ErrorList msg = ErrorList.of(text);
      log.warn(text);
      return new AjaxReturnObject<>(null, msg);
    }
    return new AjaxReturnObject<>(infos, null);
  }

  @GetMapping("/allIntegrations")
  public AjaxReturnObject<Map<String, IntegrationInfo>> getAllIntegrationsInfo(
      Principal principal) {
    log.info("returning details of every integration of user: {}", principal.getName());
    User user = userManager.getAuthenticatedUserInSession();
    Map<String, IntegrationInfo> map = getAll(user);
    return new AjaxReturnObject<>(map, null);
  }

  private Map<String, IntegrationInfo> getAll(User user) {
    Map<String, IntegrationInfo> rc = new HashMap<>();
    // preference-based integrations
    for (Preference booleanIntegration : IntegrationsHandlerImpl.booleanIntegrationPrefs) {
      String integrationName = booleanIntegration.name();
      rc.put(integrationName, integrationsHandler.getIntegration(user, integrationName));
    }
    // app-config integrations
    rc.put(SLACK_APP_NAME, integrationsHandler.getIntegration(user, SLACK_APP_NAME));
    rc.put(DATAVERSE_APP_NAME, integrationsHandler.getIntegration(user, DATAVERSE_APP_NAME));
    rc.put(FIGSHARE_APP_NAME, integrationsHandler.getIntegration(user, FIGSHARE_APP_NAME));
    rc.put(OWNCLOUD_APP_NAME, integrationsHandler.getIntegration(user, OWNCLOUD_APP_NAME));
    rc.put(NEXTCLOUD_APP_NAME, integrationsHandler.getIntegration(user, NEXTCLOUD_APP_NAME));
    rc.put(EVERNOTE_APP_NAME, integrationsHandler.getIntegration(user, EVERNOTE_APP_NAME));
    rc.put(GITHUB_APP_NAME, integrationsHandler.getIntegration(user, GITHUB_APP_NAME));
    rc.put(EGNYTE_APP_NAME, integrationsHandler.getIntegration(user, EGNYTE_APP_NAME));
    rc.put(MSTEAMS_APP_NAME, integrationsHandler.getIntegration(user, MSTEAMS_APP_NAME));
    rc.put(PROTOCOLS_IO_APP_NAME, integrationsHandler.getIntegration(user, PROTOCOLS_IO_APP_NAME));
    rc.put(PYRAT_APP_NAME, integrationsHandler.getIntegration(user, PYRAT_APP_NAME));
    rc.put(DMPTOOL_APP_NAME, integrationsHandler.getIntegration(user, DMPTOOL_APP_NAME));
    rc.put(DMPONLINE_APP_NAME, integrationsHandler.getIntegration(user, DMPONLINE_APP_NAME));
    rc.put(
        CLUSTERMARKET_APP_NAME, integrationsHandler.getIntegration(user, CLUSTERMARKET_APP_NAME));
    rc.put(JOVE_APP_NAME, integrationsHandler.getIntegration(user, JOVE_APP_NAME));
    rc.put(DRYAD_APP_NAME, integrationsHandler.getIntegration(user, DRYAD_APP_NAME));
    rc.put(ARGOS_APP_NAME, integrationsHandler.getIntegration(user, ARGOS_APP_NAME));
    rc.put(ZENODO_APP_NAME, integrationsHandler.getIntegration(user, ZENODO_APP_NAME));
    rc.put(OMERO_APP_NAME, integrationsHandler.getIntegration(user, OMERO_APP_NAME));
    rc.put(
        DIGITAL_COMMONS_DATA_APP_NAME,
        integrationsHandler.getIntegration(user, DIGITAL_COMMONS_DATA_APP_NAME));
    return rc;
  }

  @PostMapping("/update")
  public AjaxReturnObject<IntegrationInfo> updateIntegration(
      @RequestBody IntegrationInfo newInfo, // need name/enabled state in this object
      HttpSession session) {

    /* for Egnyte integration we verify that passed URL is valid */
    String error = null;
    if (EGNYTE_APP_NAME.equals(newInfo.getName()) && !MapUtils.isEmpty(newInfo.getOptions())) {
      removeEgnyteTokenFromSession(session);
      String egnyteUrl = (String) newInfo.getOptions().get(EGNYTE_DOMAIN_SETTING);
      error = new URLPreferenceValidator().connectAndReadUrl(egnyteUrl, null);
    }
    if (error != null) {
      ErrorList errorList = ErrorList.of(getText(error));
      return new AjaxReturnObject<>(null, errorList);
    }

    User subject = userManager.getAuthenticatedUserInSession();
    integrationsHandler.updateIntegrationInfo(subject, newInfo);
    return getIntegrationInfo(newInfo.getName());
  }

  @PostMapping("/saveAppOptions")
  public AjaxReturnObject<IntegrationInfo> saveAppOptions(
      @RequestParam(value = "optionsId", required = false) Long optionsId,
      @RequestParam(value = "appName") String appName,
      @RequestBody Map<String, String> options) {
    User subject = userManager.getAuthenticatedUserInSession();
    integrationsHandler.saveAppOptions(optionsId, options, appName, false, subject);
    return getIntegrationInfo(appName);
  }

  @PostMapping("/deleteAppOptions")
  public AjaxReturnObject<IntegrationInfo> deleteAppOptions(
      @RequestParam(value = "optionsId") Long optionsId,
      @RequestParam(value = "appName") String appName) {
    User subject = userManager.getAuthenticatedUserInSession();
    integrationsHandler.deleteAppOptions(optionsId, appName, subject);
    return getIntegrationInfo(appName);
  }

  private void removeEgnyteTokenFromSession(HttpSession session) {
    log.info("removing egnyte token from session");
    session.removeAttribute(SessionAttributeUtils.SESSION_EGNYTE_TOKEN);
  }
}
