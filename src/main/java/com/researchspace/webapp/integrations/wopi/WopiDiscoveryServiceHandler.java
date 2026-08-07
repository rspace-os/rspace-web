package com.researchspace.webapp.integrations.wopi;

import com.researchspace.webapp.integrations.wopi.models.xml.XmlAction;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlApp;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlProofKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

public class WopiDiscoveryServiceHandler {

  @Data
  @AllArgsConstructor
  public static class WopiAction {
    private String name;
    private String urlsrc;
    private String targetext;
    private WopiApp app;

    public WopiAction(XmlAction action, WopiApp app) {
      this.name = action.getName();
      this.urlsrc = action.getUrlSource();
      this.targetext = action.getTargetExtension();
      this.app = app;
    }
  }

  @Data
  @AllArgsConstructor
  public static class WopiApp {
    private String name;
    private String favIconUrl;

    public WopiApp(XmlApp app) {
      this.name = app.getName();
      this.favIconUrl = app.getFavIconUrl();
    }
  }

  @Data
  @AllArgsConstructor
  public static class WopiPublicKeys {
    private String value;
    private String modulus;
    private String exponent;

    private String oldValue;
    private String oldModulus;
    private String oldExponent;

    public WopiPublicKeys(XmlProofKey key) {
      this.value = key.getValue();
      this.modulus = key.getModulus();
      this.exponent = key.getExponent();
      this.oldValue = key.getOldValue();
      this.oldModulus = key.getOldModulus();
      this.oldExponent = key.getOldExponent();
    }
  }

  @Data
  @AllArgsConstructor
  public static class WopiData {
    // Map from extension name to a map of action name and WopiAction
    private Map<String, Map<String, WopiAction>> availableActionsForExt;
    private Map<String, WopiApp> supportedExtensions;
    private WopiPublicKeys keys;
  }

  @Autowired private WopiProofKeyValidator proofKeyValidator;

  /**
   * Single object that gets changed when the discovery XML data gets updated. Source of truth for
   * the latest data. Can be null.
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private Optional<WopiData> data = Optional.empty();

  public void setData(WopiData data) {
    this.data = Optional.of(data);
    proofKeyValidator.setPublicKeys(data.getKeys());
  }

  /**
   * Main interface for accessing the cached Wopi data
   *
   * @return map of action names / actions supporting given file extension
   */
  public Map<String, WopiAction> getActionsForFileType(String ext) {
    if (!data.isPresent()) return Collections.emptyMap();
    else return data.get().getAvailableActionsForExt().getOrDefault(ext, new HashMap<>());
  }

  public Map<String, WopiApp> getSupportedExtensions() {
    if (!data.isPresent()) return Collections.emptyMap();
    else return data.get().getSupportedExtensions();
  }
}
