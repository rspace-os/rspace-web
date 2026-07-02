/*
 * Mapping between the inventory system-settings API shape and the datacite-shaped model the
 * "Configure Inventory (for System Administrators)" dialog uses.
 *
 * The GET /system/settings endpoint now returns an `identifiersSettings` map keyed by identifier
 * type (IGSN, PIDINST), each entry carrying a `provider`. The dialog still only configures the
 * IGSN/DataCite credentials, so we adapt the IGSN entry to/from the existing `datacite` model and
 * leave the rest of the UI untouched. PIDINST configuration is out of scope for this dialog.
 */

export type IntegrationState = "true" | "false";

export type DataCiteServerUrl = "https://api.datacite.org" | "https://api.test.datacite.org";

export type IdentifierProvider = "IGSN_DATACITE" | "PIDINST_DATACITE" | "PIDINST_B2INST";

/** The datacite-shaped settings the dialog reads and edits. */
export type DataciteSettings = {
  enabled: IntegrationState;
  serverUrl: DataCiteServerUrl;
  username: string;
  password: string;
  repositoryPrefix: string;
};

export type SystemSettings = {
  datacite: DataciteSettings;
};

/**
 * A single identifier-settings entry as returned/accepted by the API. Unlike the DataCite-only
 * dialog model, `serverUrl` is free-form here because a B2INST provider points at a B2INST host
 * rather than one of the two DataCite URLs.
 */
export type IdentifierSettings = Omit<DataciteSettings, "serverUrl"> & {
  provider: IdentifierProvider;
  serverUrl: string;
};

/**
 * Shape returned by GET /system/settings. Each setting type holds an array of providers (PIDINST
 * carries both PIDINST_DATACITE and PIDINST_B2INST). The full multi-provider settings UI is
 * RSDEV-1180; here we only keep the existing IGSN/DataCite dialog working.
 */
export type ApiInventorySystemSettings = {
  identifiersSettings: {
    IGSN?: IdentifierSettings[];
    PIDINST?: IdentifierSettings[];
  };
};

const DEFAULT_DATACITE_SETTINGS: DataciteSettings = {
  enabled: "false",
  serverUrl: "https://api.datacite.org",
  username: "",
  password: "",
  repositoryPrefix: "",
};

/**
 * Adapts the API response to the dialog's datacite model, using only the IGSN entry and dropping
 * the `provider` field. Falls back to safe defaults if the IGSN entry is absent.
 */
export function systemSettingsFromApiResponse(response: ApiInventorySystemSettings): SystemSettings {
  const igsnEntries = response?.identifiersSettings?.IGSN;
  const igsn = igsnEntries?.find((entry) => entry.provider === "IGSN_DATACITE") ?? igsnEntries?.[0];
  if (!igsn) {
    return { datacite: { ...DEFAULT_DATACITE_SETTINGS } };
  }
  return {
    datacite: {
      enabled: igsn.enabled,
      // the IGSN/DataCite entry is expected to use one of the DataCite URLs the dialog offers
      serverUrl: igsn.serverUrl as DataCiteServerUrl,
      username: igsn.username,
      password: igsn.password,
      repositoryPrefix: igsn.repositoryPrefix,
    },
  };
}

/**
 * Wraps the datacite settings into a single IGSN identifier-settings object for PUT
 * /system/settings, which is now routed by `provider`.
 */
export function dataciteSettingsToIgsnPayload(datacite: DataciteSettings): IdentifierSettings {
  return {
    provider: "IGSN_DATACITE",
    enabled: datacite.enabled,
    serverUrl: datacite.serverUrl,
    username: datacite.username,
    password: datacite.password,
    repositoryPrefix: datacite.repositoryPrefix,
  };
}
