import { expectRepositoryDepositCompletes } from "@/__tests__/e2e/components/shared/repositoryExportScenario";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const INTEGRATION_MODE = env.integrationMode;
const DATAVERSE_ALIAS = INTEGRATION_MODE === "real" ? env.dataverseName : "mock";
const DATAVERSE_SERVER_URL = INTEGRATION_MODE === "real" ? env.dataverseServerUrl : env.mockBackendBaseUrl;
const DATAVERSE_API_KEY = INTEGRATION_MODE === "real" ? env.dataverseApiToken : "mock-dataverse-token";

test.describe(`Dataverse integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !(DATAVERSE_ALIAS && DATAVERSE_SERVER_URL && DATAVERSE_API_KEY),
    "real mode needs DATAVERSE_NAME, DATAVERSE_SERVER_URL and DATAVERSE_API_TOKEN in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("dataverse.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithMultiConnection("Dataverse", {
      aliasFieldLabel: "Dataverse Name",
      aliasValue: DATAVERSE_ALIAS,
      serverUrl: DATAVERSE_SERVER_URL,
      apiKey: DATAVERSE_API_KEY,
      configuredFormAriaLabelPrefix: "Configured Dataverse with name",
    });
  });

  test("As a user, I can export a document to Dataverse and receive a completion notification", async ({
    clientDocuments,
    pageDocument,
    componentExportWizard,
    componentNotifications,
    componentToasts,
    page,
  }) => {
    await expectRepositoryDepositCompletes({
      repoDisplayName: "Dataverse",

      selectRepository: (wizard) => wizard.selectRepository("Dataverse", DATAVERSE_ALIAS),

      afterSelectRepository: (wizard) => wizard.selectRepositorySubject("Chemistry"),
      clientDocuments,
      pageDocument,
      componentExportWizard,
      componentNotifications,
      componentToasts,
      page,
    });
  });
});
