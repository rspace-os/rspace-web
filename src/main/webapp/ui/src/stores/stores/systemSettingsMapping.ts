/*
 * Mapping between the inventory system-settings API shape and the datacite-shaped model the
 * "Configure Inventory (for System Administrators)" dialog uses.
 *
 * The GET /system/settings endpoint now returns an `identifiersSettings` map keyed by identifier
 * type (IGSN, PDINST), each entry carrying a `provider`. The dialog still only configures the
 * IGSN/DataCite credentials, so we adapt the IGSN entry to/from the existing `datacite` model and
 * leave the rest of the UI untouched. PDINST configuration is out of scope for this dialog.
 */

export type IntegrationState = "true" | "false";

export type DataCiteServerUrl =
  | "https://api.datacite.org"
  | "https://api.test.datacite.org";

export type IdentifierProvider =
  | "IGSN_DATACITE"
  | "PDINST_DATACITE"
  | "PDINST_B2INST";

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

/** A single identifier-settings entry as returned/accepted by the API. */
export type IdentifierSettings = DataciteSettings & {
  provider: IdentifierProvider;
};

/** Shape returned by GET /system/settings. */
export type ApiInventorySystemSettings = {
  identifiersSettings: {
    IGSN?: IdentifierSettings;
    PDINST?: IdentifierSettings;
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
export function systemSettingsFromApiResponse(
  response: ApiInventorySystemSettings
): SystemSettings {
  const igsn = response?.identifiersSettings?.IGSN;
  if (!igsn) {
    return { datacite: { ...DEFAULT_DATACITE_SETTINGS } };
  }
  return {
    datacite: {
      enabled: igsn.enabled,
      serverUrl: igsn.serverUrl,
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
export function dataciteSettingsToIgsnPayload(
  datacite: DataciteSettings
): IdentifierSettings {
  return {
    provider: "IGSN_DATACITE",
    enabled: datacite.enabled,
    serverUrl: datacite.serverUrl,
    username: datacite.username,
    password: datacite.password,
    repositoryPrefix: datacite.repositoryPrefix,
  };
}
