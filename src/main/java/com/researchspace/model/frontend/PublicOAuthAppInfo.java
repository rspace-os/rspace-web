package com.researchspace.model.frontend;

import com.researchspace.model.oauth.OAuthApp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Holds information displayed publicly to users when they view their connected OAuthApps */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicOAuthAppInfo {
  @NonNull String appName;
  @NonNull String clientId;

  public PublicOAuthAppInfo(OAuthApp app) {
    this.appName = app.getName();
    this.clientId = app.getClientId();
  }
}
