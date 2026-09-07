import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { env } from "@/__tests__/e2e/env";
import { apiTest } from "@/__tests__/e2e/fixtures/api";

type RepositoryIntegrationFixtures = {
  flowIgsnConfig: undefined;
  flowPidinstDataciteConfig: undefined;
  flowPidinstB2instConfig: undefined;
};

function mockOrReal<T>(mock: T, real: T): T {
  return env.integrationMode === "mock" ? mock : real;
}

async function configureDataciteProvider(
  clientSysadmin: SysadminClient,
  provider: "IGSN_DATACITE" | "PIDINST_DATACITE",
): Promise<void> {
  const account = {
    enabled: "true" as const,
    ...mockOrReal(
      {
        serverUrl: env.mockBackendBaseUrl,
        username: "mock-igsn-account",
        password: "mock-igsn-password",
        repositoryPrefix: "10.99999",
      },
      {
        serverUrl: env.igsnServerUrl,
        username: env.igsnAccountId,
        password: env.igsnPassword,
        repositoryPrefix: env.igsnRepoPrefix,
      },
    ),
  };

  if (provider === "IGSN_DATACITE") {
    await clientSysadmin.configureIgsn({ provider, ...account });
    if (!(await clientSysadmin.testIgsnConnection())) {
      throw new Error("IGSN provider connection test returned false after configuration");
    }
  } else {
    await clientSysadmin.configurePidinst({ provider, ...account });
    if (!(await clientSysadmin.testPidinstConnection())) {
      throw new Error("PIDINST DataCite provider connection test returned false after configuration");
    }
  }
}

export const test = apiTest.extend<RepositoryIntegrationFixtures>({
  flowIgsnConfig: async ({ clientSysadmin }, use) => {
    await configureDataciteProvider(clientSysadmin, "IGSN_DATACITE");
    await use(undefined);
  },

  flowPidinstDataciteConfig: async ({ clientSysadmin }, use) => {
    await configureDataciteProvider(clientSysadmin, "PIDINST_DATACITE");
    await use(undefined);
  },

  flowPidinstB2instConfig: async ({ clientSysadmin }, use) => {
    await clientSysadmin.configurePidinst({
      provider: "PIDINST_B2INST",
      enabled: "true",
      ...mockOrReal(
        { serverUrl: env.mockBackendBaseUrl, username: "mock-b2inst-community", password: "mock-b2inst-token" },
        {
          serverUrl: env.pidinstB2instServerUrl,
          username: env.pidinstB2instCommunityId,
          password: env.pidinstB2instToken,
        },
      ),
      repositoryPrefix: "",
    });
    if (!(await clientSysadmin.testPidinstConnection())) {
      throw new Error("PIDINST B2INST connection test returned false after configuration");
    }
    await use(undefined);
  },
});
