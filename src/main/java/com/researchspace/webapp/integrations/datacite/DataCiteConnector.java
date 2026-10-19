package com.researchspace.webapp.integrations.datacite;

import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;
import java.util.Optional;

/**
 * Connects to DataCite for registering identifiers. Holds one client per {@link
 * InventorySettingType}: IGSN (configured from igsn.datacite.* system properties) and PIDINST (from
 * pidinst.datacite.* system properties). The no-arg variants operate on the IGSN client. PID lookup
 * uses a third, anonymous client for the public registry (deployment property {@code
 * pidinst.lookup.datacite.url}, ADR 0011).
 */
public interface DataCiteConnector {

  DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  boolean deleteDoi(String s, InventorySettingType settingType);

  DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  /**
   * Rewrites a DOI's metadata in place, leaving its state alone. Unlike publish and retract, which
   * are the same call carrying an {@code event}, this sends no event, so a {@code draft} DOI stays
   * a draft; that is what makes it the external metadata update for a draft (RSDEV-1251, ADR 0008).
   * A findable DOI is refreshed through the existing Republish instead.
   */
  DataCiteDoi updateDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi, InventorySettingType settingType);

  /**
   * The DOI as the PUBLIC registry (deployment property {@code pidinst.lookup.datacite.url}) holds
   * it, read anonymously, or empty when DataCite answers 404 or the value is not a DOI (ADR 0011).
   */
  Optional<DataCiteDoi> findPublicDoi(String doiId);

  /**
   * One page (0-based {@code pageNumber} of {@code pageSize}) of FINDABLE instrument DOIs of the
   * public registry matching the query, newest update first, read anonymously (ADR 0011). Findable
   * only, because only a publicly resolvable DOI may be linked to an instrument (RSDEV-1326);
   * DataCite applies the filter itself through its {@code state} request parameter, so {@code
   * meta.total} describes the same set as the page.
   *
   * <p>The query reaches DataCite's {@code query} parameter as written, and that parameter is
   * Elasticsearch query-string syntax rather than plain text: bare words work as free text, and a
   * caller may also pass a clause such as {@code doi:*suffix*}. Escaping is the caller's, because
   * unbalanced syntax answers 400 rather than no hits.
   */
  DataCiteDoiSearchResult searchPublicInstrumentDois(String query, int pageNumber, int pageSize);

  void reloadDataCiteClient();

  boolean isDataCiteConfiguredAndEnabled(InventorySettingType settingType);

  boolean testDataCiteConnection(InventorySettingType settingType);

  default DataCiteDoi registerDoi(DataCiteDoi dataCiteDoi) {
    return registerDoi(dataCiteDoi, InventorySettingType.IGSN);
  }

  default boolean deleteDoi(String s) {
    return deleteDoi(s, InventorySettingType.IGSN);
  }

  default DataCiteDoi publishDoi(DataCiteDoi dataCiteDoi) {
    return publishDoi(dataCiteDoi, InventorySettingType.IGSN);
  }

  default DataCiteDoi retractDoi(DataCiteDoi dataCiteDoi) {
    return retractDoi(dataCiteDoi, InventorySettingType.IGSN);
  }

  default boolean isDataCiteConfiguredAndEnabled() {
    return isDataCiteConfiguredAndEnabled(InventorySettingType.IGSN);
  }

  default boolean testDataCiteConnection() {
    return testDataCiteConnection(InventorySettingType.IGSN);
  }
}
