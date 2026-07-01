package com.researchspace.service;

import lombok.Getter;

@Getter
public enum SystemPropertyName {
  API_AVAILABLE("api.available"),
  API_OAUTH_AUTHENTICATION("api.oauth.authentication"),
  BOX_AVAILABLE("box.available"),
  CHEMISTRY_AVAILABLE("chemistry.available"),
  IGSN_DATACITE_ENABLED("igsn.datacite.enabled"),
  IGSN_DATACITE_SERVER_URL("igsn.datacite.server.url"),
  IGSN_DATACITE_USERNAME("igsn.datacite.username"),
  IGSN_DATACITE_PASSWORD("igsn.datacite.password"),
  IGSN_DATACITE_REPOSITORY_PREFIX("igsn.datacite.repositoryPrefix"),
  PIDINST_DATACITE_ENABLED("pidinst.datacite.enabled"),
  PIDINST_DATACITE_SERVER_URL("pidinst.datacite.server.url"),
  PIDINST_DATACITE_USERNAME("pidinst.datacite.username"),
  PIDINST_DATACITE_PASSWORD("pidinst.datacite.password"),
  PIDINST_DATACITE_REPOSITORY_PREFIX("pidinst.datacite.repositoryPrefix"),
  PIDINST_B2INST_ENABLED("pidinst.b2inst.enabled"),
  PIDINST_B2INST_SERVER_URL("pidinst.b2inst.server.url"),
  PIDINST_B2INST_COMMUNITY_ID("pidinst.b2inst.community.id"),
  PIDINST_B2INST_TOKEN("pidinst.b2inst.token"),
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
  SNAPGENE_AVAILABLE("snapgene.available"),
  DIGITAL_COMMON_DATA_AVAILABLE("digitalCommonsData.available"),
  RAID_AVAILABLE("raid.available"),
  ARGOS_AVAILABLE("argos.available"),
  CLUSTER_MARKET_AVAILABLE("clustermarket.available"),
  DATAVERSE_AVAILABLE("dataverse.available"),
  DMPASSISTANT_AVAILABLE("dmpassistant.available"),
  DMPONLINE_AVAILABLE("dmponline.available"),
  DMPTOOL_AVAILABLE("dmptool.available"),
  DRYAD_AVAILABLE("dryad.available"),
  EGNYTE_AVAILABLE("egnyte.available"),
  EVERNOTE_AVAILABLE("evernote.available"),
  FIELDMARK_AVAILABLE("fieldmark.available"),
  FIGSHARE_AVAILABLE("figshare.available"),
  GALAXY_AVAILABLE("galaxy.available"),
  GITHUB_AVAILABLE("github.available"),
  MS_TEAMS_AVAILABLE("msteams.available"),
  NEXTCLOUD_AVAILABLE("nextcloud.available"),
  OMERO_AVAILABLE("omero.available"),
  ONBOARDING_AVAILABLE("onboarding.available"),
  ONEDRIVE_AVAILABLE("onedrive.available"),
  OWNCLOUD_AVAILABLE("owncloud.available"),
  PROTOCOLS_IO_AVAILABLE("protocols_io.available"),
  PYRAT_AVAILABLE("pyrat.available"),
  ZENODO_AVAILABLE("zenodo.available"),
  DSW_AVAILABLE("dsw.available");

  private String propertyName;

  private SystemPropertyName(String propertyName) {
    this.propertyName = propertyName;
  }

  public static SystemPropertyName valueOfPropertyName(String label) {
    for (SystemPropertyName e : values()) {
      if (e.propertyName.equalsIgnoreCase(label)) {
        return e;
      }
    }
    return null;
  }
}
