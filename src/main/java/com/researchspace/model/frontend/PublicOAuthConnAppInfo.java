package com.researchspace.model.frontend;

import com.researchspace.model.oauth.OAuthToken;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Holds information displayed publicly to users when they view their connected OAuthApps */
@Data
@NoArgsConstructor
public class PublicOAuthConnAppInfo {
  @NonNull String clientName;
  @NonNull String clientId;
  @NonNull String scope = "all";

  public PublicOAuthConnAppInfo(OAuthToken token, PublicOAuthAppInfo appInfo) {
    if (!token.getClientId().equals(appInfo.getClientId())) {
      throw new IllegalArgumentException("Token and App client IDs do not match!");
    }

    this.clientName = appInfo.getAppName();
    this.clientId = token.getClientId();
  }
}
