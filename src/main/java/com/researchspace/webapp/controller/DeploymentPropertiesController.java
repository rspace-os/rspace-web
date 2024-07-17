package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.SystemPropertyManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/deploymentproperties*")
public class DeploymentPropertiesController extends BaseController {

  public static final String SNAPGENE_AVAILABLE = "snapgene.available";

  @Autowired private SystemPropertyManager sysPropertyMgr;

  @Value("${onedrive.redirect}")
  private String oneDriveRedirect;

  @Value("${onedrive.client.id}")
  private String oneDriveClientId;

  @Value("${mendeley.id}")
  private String mendeleyId;

  @Value("${server.urls.prefix}")
  private String baseURL;

  @Value("${box.client.id}")
  private String boxClientId;

  @Value("${egnyte.client.id}")
  private String egnyteClientId;

  @Value("${pyrat.url}")
  private String pyratUrl;

  @Value("${pyrat.client.token}")
  private String pyratClientToken;

  @Value("${labtools.server.location}")
  private String labToolsServerUrl;

  @Value("${owncloud.url}")
  private String ownCloudURL;

  @Value("${owncloud.server.name}")
  private String ownCloudServerName;

  @Value("${owncloud.auth.type}")
  private String ownCloudAuthType;

  @Value("${owncloud.client.id}")
  private String ownCloudClientId;

  @Value("${nextcloud.url}")
  private String nextCloudURL;

  @Value("${nextcloud.server.name}")
  private String nextCloudServerName;

  @Value("${nextcloud.auth.type}")
  private String nextCloudAuthType;

  @Value("${nextcloud.client.id}")
  private String nextCloudClientId;

  @Value("${googledrive.developer.key}")
  private String googleDriveDevKey;

  @Value("${googledrive.client.id}")
  private String googleDriveClientId;

  @Value("${clustermarket.api.url}")
  private String clustermarketApiUrl;

  @Value("${clustermarket.web.url}")
  private String clustermarketWebUrl;

  @Value("${omero.api.url}")
  private String omeroApiUrl;

  @Value("${jove.api.url}")
  private String joveApiUrl;

  @Value("${sysadmin.delete.user}")
  private String sysadminDeleteUser;

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
      if (spv.getProperty().getName().equals(propertyName)) {
        return spv.getValue();
      }
    }

    // not found, must be set in deployment property files
    switch (propertyName) {
      case "clustermarket.web.url":
        return clustermarketWebUrl;
      case "clustermarket.api.url":
        return clustermarketApiUrl;
      case "omero.api.url":
        return omeroApiUrl;
      case "jove.api.url":
        return joveApiUrl;
      case "onedrive.redirect":
        return oneDriveRedirect;
      case "mendeley.id":
        return mendeleyId;
      case "server.urls.prefix":
        return baseURL;
      case "onedrive.client.id":
        return oneDriveClientId;
      case "egnyte.client.id":
        return egnyteClientId;
      case "pyrat.url":
        return pyratUrl;
      case "pyrat.client.token":
        return pyratClientToken;
      case "owncloud.url":
        return ownCloudURL;
      case "owncloud.server.name":
        return ownCloudServerName;
      case "owncloud.auth.type":
        return ownCloudAuthType;
      case "owncloud.client.id":
        return ownCloudClientId;
      case "nextcloud.url":
        return nextCloudURL;
      case "nextcloud.server.name":
        return nextCloudServerName;
      case "nextcloud.auth.type":
        return nextCloudAuthType;
      case "nextcloud.client.id":
        return nextCloudClientId;
      case "googledrive.developer.key":
        return googleDriveDevKey;
      case "googledrive.client.id":
        return googleDriveClientId;
      case "aspose.enabled":
        return String.valueOf(isAsposeEnabled());
      case "sysadmin.delete.user":
        return sysadminDeleteUser;
      default:
        throw new IllegalArgumentException("No property available for name: " + propertyName);
    }
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
    Map<String, String> properties = new HashMap<>();
    properties.put("clustermarket.api.url", clustermarketApiUrl);
    properties.put("clustermarket.web.url", clustermarketWebUrl);
    properties.put("omero.api.url", omeroApiUrl);
    properties.put("jove.api.url", joveApiUrl);
    properties.put("dropbox.available", rc.get("dropbox.available").getValue());
    properties.put("dropbox.linking.enabled", rc.get("dropbox.linking.enabled").getValue());

    properties.put("box.available", rc.get("box.available").getValue());
    properties.put("box.linking.enabled", rc.get("box.linking.enabled").getValue());
    properties.put("box.api.enabled", rc.get("box.api.enabled").getValue());
    properties.put("box.client.id", boxClientId);

    properties.put("googledrive.available", rc.get("googledrive.available").getValue());
    properties.put("googledrive.linking.enabled", rc.get("googledrive.linking.enabled").getValue());

    properties.put("onedrive.available", rc.get("onedrive.available").getValue());
    properties.put("onedrive.linking.enabled", rc.get("onedrive.linking.enabled").getValue());
    properties.put("onedrive.redirect", oneDriveRedirect);
    properties.put("onedrive.client.id", oneDriveClientId);

    properties.put("egnyte.available", rc.get("egnyte.available").getValue());
    properties.put("egnyte.client.id", egnyteClientId);

    properties.put("chemistry.available", rc.get("chemistry.available").getValue());
    properties.put(SNAPGENE_AVAILABLE, rc.get(SNAPGENE_AVAILABLE).getValue());

    properties.put("mendeley.available", rc.get("mendeley.available").getValue());
    properties.put("mendeley.id", mendeleyId);
    properties.put("baseURL", baseURL);

    properties.put("labtools.server.location", labToolsServerUrl);

    properties.put("owncloud.url", ownCloudURL);
    properties.put("owncloud.server.name", ownCloudServerName);
    properties.put("owncloud.auth.type", ownCloudAuthType);
    properties.put("owncloud.client.id", ownCloudClientId);

    properties.put("nextcloud.url", nextCloudURL);
    properties.put("nextcloud.server.name", nextCloudServerName);
    properties.put("nextcloud.auth.type", nextCloudAuthType);
    properties.put("nextcloud.client.id", nextCloudClientId);

    properties.put("pyrat.url", pyratUrl);
    properties.put("pyrat.client.token", pyratClientToken);

    properties.put("googledrive.developer.key", googleDriveDevKey);
    properties.put("googledrive.client.id", googleDriveClientId);

    properties.put("server.urls.prefix", baseURL);
    properties.put("aspose.enabled", String.valueOf(isAsposeEnabled()));

    return properties;
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
      properties.put(spv.getProperty().getName(), spv.getValue());
    }
    return properties;
  }

  /**
   * Method for updating system property by name
   *
   * @return updated property value
   */
  @PostMapping("/ajax/updateProperty")
  @ResponseBody
  public AjaxReturnObject<String> updateProperty(
      @RequestParam(value = "propertyName", required = true) String propertyName,
      @RequestParam(value = "newValue", required = true) String newValue) {
    User subject = userManager.getAuthenticatedUserInSession();
    SystemPropertyValue updatedValue = sysPropertyMgr.save(propertyName, newValue, subject);
    return new AjaxReturnObject<>(updatedValue.getValue(), null);
  }
}
