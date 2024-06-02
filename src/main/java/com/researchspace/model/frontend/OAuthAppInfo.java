package com.researchspace.model.frontend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Information that is only returned once after the creation of a new OAuth app */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OAuthAppInfo {
  @NonNull String appName;
  @NonNull String clientId;
  @NonNull String unhashedClientSecret;
}
