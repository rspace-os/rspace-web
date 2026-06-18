/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Inventory system-wide settings for identifier (IGSN/PIDINST) registration. */
@Data
public class ApiInventorySystemSettings {

  /** The category of identifier a settings entry configures. */
  public enum InventorySettingType {
    IGSN,
    PIDINST
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class IdentifierSettings {
    private IdentifierType provider;
    private String serverUrl;
    private String username;
    private String password;
    private String repositoryPrefix;
    private String enabled;
  }

  private Map<InventorySettingType, IdentifierSettings> identifiersSettings = new HashMap<>();

  public IdentifierSettings getOrCreate(InventorySettingType type) {
    return identifiersSettings.computeIfAbsent(type, t -> new IdentifierSettings());
  }
}
