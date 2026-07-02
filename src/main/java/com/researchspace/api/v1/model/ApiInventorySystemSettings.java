/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  /**
   * Settings grouped by type. A type may hold more than one provider (PIDINST holds both {@code
   * PIDINST_DATACITE} and {@code PIDINST_B2INST}), of which at most one is enabled at a time.
   */
  private Map<InventorySettingType, Set<IdentifierSettings>> identifiersSettings =
      new EnumMap<>(InventorySettingType.class);

  /** Add a settings entry under its type, preserving insertion order. */
  public void addSetting(InventorySettingType type, IdentifierSettings settings) {
    identifiersSettings.computeIfAbsent(type, t -> new LinkedHashSet<>()).add(settings);
  }

  /** Find the single settings entry for a specific provider, across all types. */
  public Optional<IdentifierSettings> findByProvider(IdentifierType provider) {
    return identifiersSettings.values().stream()
        .flatMap(Set::stream)
        .filter(s -> provider.equals(s.getProvider()))
        .findFirst();
  }
}
