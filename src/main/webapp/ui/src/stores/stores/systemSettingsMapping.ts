/*
 * Mapping between the inventory system-settings API shape and the model the
 * "Configure Inventory (for System Administrators)" dialog uses.
 *
 * The GET /system/settings endpoint returns an `identifiersSettings` map keyed by identifier
 * type (IGSN, PIDINST), each entry carrying a `provider`. The dialog configures IGSN and PIDINST
 * credentials. We adapt each entry from the API response to/from typed models.
 */

export type IntegrationState = "true" | "false";

export type DataCiteServerUrl = "https://api.datacite.org" | "https://api.test.datacite.org";

export type IdentifierProvider = "IGSN_DATACITE" | "PIDINST_DATACITE" | "PIDINST_B2INST";

/** The DataCite-shaped settings model used by IGSN and PIDINST DataCite panels. */
export type DataciteSettings = {
  enabled: IntegrationState;
  serverUrl: DataCiteServerUrl;
  username: string;
  password: string;
  repositoryPrefix: string;
};

/** The B2INST-shaped settings model. username = community id, password = token, repositoryPrefix always empty. */
export type B2InstSettings = {
  enabled: IntegrationState;
  serverUrl: string;
  username: string;
  password: string;
  repositoryPrefix: string;
};

export type SystemSettings = {
  igsnDatacite: DataciteSettings;
  pidinstDatacite: DataciteSettings;
  pidinstB2Inst: B2InstSettings;
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
 * carries both PIDINST_DATACITE and PIDINST_B2INST).
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

const DEFAULT_B2INST_SETTINGS: B2InstSettings = {
  enabled: "false",
  serverUrl: "",
  username: "",
  password: "",
  repositoryPrefix: "",
};

function toDataciteSettings(entry: IdentifierSettings): DataciteSettings {
  return {
    enabled: entry.enabled,
    serverUrl: entry.serverUrl as DataCiteServerUrl,
    username: entry.username,
    password: entry.password,
    repositoryPrefix: entry.repositoryPrefix,
  };
}

function toB2InstSettings(entry: IdentifierSettings): B2InstSettings {
  return {
    enabled: entry.enabled,
    serverUrl: entry.serverUrl,
    username: entry.username,
    password: entry.password,
    repositoryPrefix: entry.repositoryPrefix,
  };
}

/**
 * Adapts the API response to the dialog's system settings model.
 */
export function systemSettingsFromApiResponse(response: ApiInventorySystemSettings): SystemSettings {
  const igsnEntries = response?.identifiersSettings?.IGSN;
  const igsnEntry = igsnEntries?.find((e) => e.provider === "IGSN_DATACITE") ?? igsnEntries?.[0];

  const pidinstEntries = response?.identifiersSettings?.PIDINST;
  const pidinstDataciteEntry = pidinstEntries?.find((e) => e.provider === "PIDINST_DATACITE");
  const pidinstB2InstEntry = pidinstEntries?.find((e) => e.provider === "PIDINST_B2INST");

  return {
    igsnDatacite: igsnEntry ? toDataciteSettings(igsnEntry) : { ...DEFAULT_DATACITE_SETTINGS },
    pidinstDatacite: pidinstDataciteEntry ? toDataciteSettings(pidinstDataciteEntry) : { ...DEFAULT_DATACITE_SETTINGS },
    pidinstB2Inst: pidinstB2InstEntry ? toB2InstSettings(pidinstB2InstEntry) : { ...DEFAULT_B2INST_SETTINGS },
  };
}

/**
 * Wraps IGSN DataCite settings into the API payload shape.
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

/**
 * Wraps PIDINST DataCite settings into the API payload shape.
 */
export function pidinstDataciteSettingsToPayload(datacite: DataciteSettings): IdentifierSettings {
  return {
    provider: "PIDINST_DATACITE",
    enabled: datacite.enabled,
    serverUrl: datacite.serverUrl,
    username: datacite.username,
    password: datacite.password,
    repositoryPrefix: datacite.repositoryPrefix,
  };
}

/**
 * Wraps PIDINST B2INST settings into the API payload shape.
 */
export function pidinstB2InstSettingsToPayload(b2inst: B2InstSettings): IdentifierSettings {
  return {
    provider: "PIDINST_B2INST",
    enabled: b2inst.enabled,
    serverUrl: b2inst.serverUrl,
    username: b2inst.username,
    password: b2inst.password,
    repositoryPrefix: "",
  };
}
