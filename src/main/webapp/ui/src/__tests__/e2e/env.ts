import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

const envFile = fileURLToPath(new URL("../../../.env", import.meta.url));
if (existsSync(envFile)) {
  process.loadEnvFile(envFile);
}

function optional(name: string, fallback: string): string {
  const value = process.env[name];
  return value === undefined || value === "" ? fallback : value;
}

function isLocalTarget(url: string): boolean {
  try {
    return ["localhost", "127.0.0.1", "::1"].includes(new URL(url).hostname);
  } catch {
    return false;
  }
}

export const env = {
  baseURL: optional("RSPACE_BASE_URL", "http://localhost:8080"),
  headless: optional("HEADLESS", "true") !== "false",
  ci: process.env.CI === "true",
  integrationMode: optional("E2E_INTEGRATION_MODE", "mock") === "real" ? ("real" as const) : ("mock" as const),
  browser: optional("E2E_BROWSER", ""),
  mockPort: optional("E2E_MOCK_PORT", "9099"),
  playwrightLog: optional("PW_LOG", "off") as "trace" | "info" | "off",

  mockBaseUrl: `http://localhost:${optional("E2E_MOCK_PORT", "9099")}`,

  get mockBackendBaseUrl(): string {
    return optional("E2E_MOCK_BACKEND_URL", this.mockBaseUrl);
  },

  enablePlaywrightApiDebug(): void {
    if (!optional("DEBUG", "")) {
      process.env.DEBUG = "pw:api";
    }
  },

  sysadminUsername: optional("RSPACE_SYSADMIN_USERNAME", "sysadmin1"),
  sysadminPassword: optional("RSPACE_SYSADMIN_PASSWORD", "sysWisc23!"),
  sysadminApiKey: optional("RSPACE_SYSADMIN_API_KEY", "abcdefghijklmnop12"),
  fieldmarkApiKey: optional("FIELDMARK_API_KEY", ""),
  zenodoApiKey: optional("ZENODO_API_KEY", ""),
  galaxyEuApiKey: optional("GALAXY_EU_APIKEY", ""),
  dataverseApiToken: optional("DATAVERSE_API_TOKEN", ""),
  dataverseServerUrl: optional("DATAVERSE_SERVER_URL", ""),
  dataverseName: optional("DATAVERSE_NAME", ""),
  dswApiKey: optional("DSW_API_KEY", ""),
  dswServerUrl: optional("DSW_SERVER_URL", ""),
  pyratApiKey: optional("PYRAT_API_KEY", ""),
  omeroUsername: optional("OMERO_USERNAME", ""),
  omeroPassword: optional("OMERO_PASSWORD", ""),
  igsnAccountId: optional("IGSN_ACCOUNT_ID", ""),
  igsnPassword: optional("IGSN_PASSWORD", ""),
  igsnRepoPrefix: optional("IGSN_REPO_PREFIX", ""),
  igsnServerUrl: optional("IGSN_SERVER_URL", "https://api.test.datacite.org"),

  // Some fixtures create real sysadmin-owned accounts or overwrite instance-global
  // settings (IGSN/DataCite config) with no restore step. Only safe against a
  // disposable local/CI instance, or one explicitly opted into via this flag.
  get allowsGlobalMutations(): boolean {
    return isLocalTarget(this.baseURL) || optional("E2E_ALLOW_GLOBAL_MUTATIONS", "") === "true";
  },
  assertGlobalMutationsAllowed(action: string): void {
    if (!this.allowsGlobalMutations) {
      throw new Error(
        `${action} mutates instance-global state and refuses to run against a non-local RSPACE_BASE_URL ` +
          `(${this.baseURL}). Set E2E_ALLOW_GLOBAL_MUTATIONS=true to override.`,
      );
    }
  },
};
