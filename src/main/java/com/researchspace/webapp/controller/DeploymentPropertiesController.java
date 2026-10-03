package com.researchspace.webapp.controller;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.User;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.ClientReadable;
import com.researchspace.service.ClientReadableSecret;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.raid.RaIDServerConfigurationDTO;
import com.researchspace.webapp.integrations.pyrat.PyratServerConfigurationDTO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/deploymentproperties*")
public class DeploymentPropertiesController extends BaseController {

  private static final ObjectMapper CONFIG_MAPPER =
      new ObjectMapper().setSerializationInclusion(Include.NON_NULL);
  private static final TypeReference<Map<String, PyratServerConfigurationDTO>> PYRAT_CONFIG =
      new TypeReference<>() {};
  private static final TypeReference<Map<String, RaIDServerConfigurationDTO>> RAID_CONFIG =
      new TypeReference<>() {};

  @Autowired private SystemPropertyManager sysPropertyMgr;

  @ClientReadable
  @Value("${onedrive.redirect}")
  private String oneDriveRedirect;

  @ClientReadable
  @Value("${onedrive.client.id}")
  private String oneDriveClientId;

  @ClientReadable
  @Value("${server.urls.prefix}")
  private String baseURL;

  @ClientReadable
  @Value("${box.client.id}")
  private String boxClientId;

  @ClientReadable
  @Value("${egnyte.client.id}")
  private String egnyteClientId;

  @Value("${pyrat.server.config}")
  private String pyratServerConfig;

  @Value("${raid.server.config}")
  private String raidServerConfig;

  @ClientReadable
  @Value("${labtools.server.location}")
  private String labToolsServerUrl;

  @ClientReadable
  @Value("${owncloud.url}")
  private String ownCloudURL;

  @ClientReadable
  @Value("${owncloud.server.name}")
  private String ownCloudServerName;

  @ClientReadable
  @Value("${owncloud.client.id}")
  private String ownCloudClientId;

  @ClientReadable
  @Value("${nextcloud.url}")
  private String nextCloudURL;

  @ClientReadable
  @Value("${nextcloud.server.name}")
  private String nextCloudServerName;

  @ClientReadable
  @Value("${nextcloud.client.id}")
  private String nextCloudClientId;

  @ClientReadableSecret("a browser API key: the Google Picker runs client-side")
  @Value("${googledrive.developer.key}")
  private String googleDriveDevKey;

  @ClientReadable
  @Value("${googledrive.client.id}")
  private String googleDriveClientId;

  @ClientReadable
  @Value("${googledrive.app.id}")
  private String googleDriveAppId;

  @ClientReadable
  @Value("${clustermarket.api.url}")
  private String clustermarketApiUrl;

  @ClientReadable
  @Value("${clustermarket.web.url}")
  private String clustermarketWebUrl;

  @ClientReadable
  @Value("${omero.api.url}")
  private String omeroApiUrl;

  @ClientReadable
  @Value("${sysadmin.delete.user}")
  private String sysadminDeleteUser;

  @ClientReadable
  @Value("${collabora.wopi.enabled}")
  private String collaboraEnabled;

  @ClientReadable
  @Value("${msoffice.wopi.enabled}")
  private String officeOnlineEnabled;

  @ClientReadable
  @Value("${netfilestores.enabled}")
  private String netfilestoresEnabled;

  @ClientReadable
  @Value("${gallery.actions.metadata.sidecarFile.enabled}")
  private String metadataSidecarFileEnabled;

  @ClientReadable
  @Value("${chemistry.provider}")
  private String chemistryProvider;

  @ClientReadable
  @Value("${deployment.cloud}")
  private String cloudDeployment;

  @ClientReadable
  @Value("${deployment.description}")
  private String deploymentDescription;

  @ClientReadable
  @Value("${deployment.helpEmail}")
  private String deploymentHelpEmail;

  /**
   * Service to return the value of property stored in the deployment.properties file. Uses a
   * whitelist strategy to only return properties that should be exposed.
   *
   * @param propertyName
   * @return
   * @throws Exception
   */
  @GetMapping("/ajax/property")
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  @ResponseBody
  public String getPropertyValue(@RequestParam(value = "name") String propertyName) {
    // managed props e.g. for RSPAC-861
    List<SystemPropertyValue> dbProperties = sysPropertyMgr.getAllSysadminProperties();
    for (SystemPropertyValue spv : dbProperties) {
      if (spv.getProperty().getName().equals(propertyName)
          && SystemPropertyName.isClientReadable(spv.getProperty())) {
        return spv.getValue();
      }
    }

    // not found, must be set in deployment property files
    Map<String, String> deploymentProperties = clientReadableDeploymentProperties();
    if (!deploymentProperties.containsKey(propertyName)) {
      throw new IllegalArgumentException(
          messages.getResourceNotFoundMessage(
              messages.getMessage("resourceType.property"), propertyName));
    }
    return deploymentProperties.get(propertyName);
  }

  /**
   * Service to return all available property values stored in the deployment.properties file. Uses
   * a whitelist strategy to only return properties that should be exposed.
   *
   * @param response
   * @return
   */
  @GetMapping("/ajax/properties")
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  @ResponseBody
  public Map<String, String> getPropertyValues(HttpServletResponse response) {
    Map<String, SystemPropertyValue> rc = sysPropertyMgr.getAllSysadminPropertiesAsMap();
    Map<String, String> properties = clientReadableDeploymentProperties();
    for (String name :
        List.of(
            "dropbox.available",
            "dropbox.linking.enabled",
            "box.available",
            "box.linking.enabled",
            "box.api.enabled",
            "googledrive.available",
            "googledrive.linking.enabled",
            "onedrive.available",
            "onedrive.linking.enabled",
            "egnyte.available",
            SystemPropertyName.CHEMISTRY_AVAILABLE.getPropertyName(),
            SystemPropertyName.SNAPGENE_AVAILABLE.getPropertyName())) {
      properties.put(name, rc.get(name).getValue());
    }
    // older name for server.urls.prefix
    properties.put("baseURL", baseURL);

    return properties;
  }

  /**
   * RSDEV-1525: the deployment properties the browser may read, allowlisted by annotation so a new
   * {@code @Value} field stays server-side unless it is marked, plus the derived values.
   */
  private Map<String, String> clientReadableDeploymentProperties() {
    Map<String, String> properties =
        clientReadableFields(this, DeploymentPropertiesController.class);
    properties.put("pyrat.server.config", withoutSecrets(pyratServerConfig, PYRAT_CONFIG));
    properties.put("raid.server.config", withoutSecrets(raidServerConfig, RAID_CONFIG));
    properties.put("aspose.enabled", String.valueOf(isAsposeEnabled()));
    return properties;
  }

  /**
   * The values of {@code type}'s {@code @Value} fields marked {@link ClientReadable} or {@link
   * ClientReadableSecret}, keyed by property name.
   */
  static Map<String, String> clientReadableFields(Object target, Class<?> type) {
    Map<String, String> properties = new HashMap<>();
    ReflectionUtils.doWithLocalFields(
        type,
        field -> {
          Value value = field.getAnnotation(Value.class);
          boolean readable =
              field.isAnnotationPresent(ClientReadable.class)
                  || field.isAnnotationPresent(ClientReadableSecret.class);
          if (value != null && readable) {
            ReflectionUtils.makeAccessible(field);
            // "${name}" or "${name:default}"
            String name =
                StringUtils.substringBefore(
                    StringUtils.substringBetween(value.value(), "${", "}"), ":");
            properties.put(name, (String) ReflectionUtils.getField(field, target));
          }
        });
    return properties;
  }

  /**
   * Re-serializes a multi-server config through its DTO, whose WRITE_ONLY fields (tokens, client
   * secrets) are dropped, so only the server-side clients ever see them (RSDEV-1525).
   */
  private <T> String withoutSecrets(String config, TypeReference<Map<String, T>> type) {
    if (StringUtils.isBlank(config)) {
      return config;
    }
    try {
      return CONFIG_MAPPER.writeValueAsString(CONFIG_MAPPER.readValue(config, type));
    } catch (JsonProcessingException e) {
      // only the class: Jackson's message quotes the source text, which holds the secrets
      log.warn("Unparseable multi-server configuration, not returning it: {}", e.getClass());
      return "";
    }
  }

  /*
   * ==================================================
   *   below methods used by 'System Settings' page
   * ==================================================
   */

  /**
   * Returns system settings page fragment from JSP. Doesn't set any model properties.
   *
   * @return system settings page view
   */
  @GetMapping("/ajax/systemSettingsView")
  public ModelAndView getFileSystemsView() {
    return new ModelAndView("system/settings_ajax");
  }

  @GetMapping("/ajax/editableProperties")
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  @ResponseBody
  public Map<String, String> getEditableProperties() {
    Map<String, String> properties = new HashMap<>();

    List<SystemPropertyValue> dbProperties = sysPropertyMgr.getAllSysadminProperties();
    for (SystemPropertyValue spv : dbProperties) {
      if (SystemPropertyName.isClientReadable(spv.getProperty())) {
        properties.put(spv.getProperty().getName(), spv.getValue());
      }
    }
    return properties;
  }

  /**
   * Method for updating system property by name
   *
   * @return updated property value
   */
  @PostMapping("/ajax/updateProperty")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"newValue"})
  @ResponseBody
  public AjaxReturnObject<String> updateProperty(
      @RequestParam(value = "propertyName", required = true) String propertyName,
      @RequestParam(value = "newValue", required = true) String newValue) {
    User subject = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(subject);
    SystemPropertyValue updatedValue = sysPropertyMgr.save(propertyName, newValue, subject);
    return new AjaxReturnObject<>(updatedValue.getValue(), null);
  }
}
