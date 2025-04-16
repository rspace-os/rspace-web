package com.researchspace.service;

import lombok.Getter;

@Getter
public enum SystemPropertyName {
  API_AVAILABLE("api.available"),
  BOX_AVAILABLE("box.available"),
  CHEMISTRY_AVAILABLE("chemistry.available"),
  DATACITE_ENABLED("datacite.enabled"),
  DATACITE_SERVER_URL("datacite.server.url"),
  DATACITE_USERNAME("datacite.username"),
  DATACITE_PASSWORD("datacite.password"),
  DATACITE_REPOSITORY_PREFIX("datacite.repositoryPrefix"),
  DROPBOX_AVAILABLE("dropbox.available"),
  DROPBOX_LINKING_ENABLED("dropbox.linking.enabled"),
  GOOGLE_DRIVE_AVAILABLE("googledrive.available"),
  GROUP_AUTOSHARING_AVAILABLE("group_autosharing.available"),
  INVENTORY_AVAILABLE("inventory.available"),
  ORCID_AVAILABLE("orcid.available"),
  PUBLIC_LAST_LOGIN_AVAILABLE("publicLastLogin.available"),
  PUBLIC_SHARING("public_sharing"),
  PUBLICDOCS_ALLOW_SEO("publicdocs_allow_seo"),
  RSPACE_ROR("rspaceinstance.ror"),
  SLACK_AVAILABLE("slack.available"),
  SNAPGENE_AVAILABLE("snapgene.available");

  private String propertyName;

  private SystemPropertyName(String propertyName) {
    this.propertyName = propertyName;
  }
}
