import { expectRepositoryDepositCompletes } from "@/__tests__/e2e/components/shared/repositoryExportScenario";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const INTEGRATION_MODE = env.integrationMode;
const ZENODO_API_KEY = INTEGRATION_MODE === "real" ? env.zenodoApiKey : "mock-zenodo-token";

test.describe(`Zenodo integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !ZENODO_API_KEY,
    "real mode needs ZENODO_API_KEY (a Zenodo personal access token) in .env / CI secrets",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("zenodo.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithApiKey("Zenodo", ZENODO_API_KEY);
  });

  test("As a user, I can export a document to Zenodo and receive a completion notification", async ({
    clientDocuments,
    pageDocument,
    componentExportWizard,
    componentNotifications,
    componentToasts,
    page,
  }) => {
    await expectRepositoryDepositCompletes({
      repoDisplayName: "Zenodo",
      selectRepository: (wizard) => wizard.selectRepository("Zenodo"),
      clientDocuments,
      pageDocument,
      componentExportWizard,
      componentNotifications,
      componentToasts,
      page,
    });
  });
});
