/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DocumentSearchResults */
@Data
public class ApiInventorySystemSettings {

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class DataCiteSettings {
    private String serverUrl;
    private String username;
    private String password;
    private String repositoryPrefix;
    private String enabled;
  }

  @Getter private DataCiteSettings datacite = new DataCiteSettings();
}
