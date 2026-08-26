import { expectRepositoryDepositCompletes } from "@/__tests__/e2e/components/shared/repositoryExportScenario";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const INTEGRATION_MODE = env.integrationMode;
const REPO_DISPLAY_NAME = "Digital Commons Data / Mendeley Data";

test.describe(`Digital Commons Data integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(
    INTEGRATION_MODE === "real",
    "real mode is out of scope: connecting requires automating Elsevier/Mendeley's real login and consent pages",
  );

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("digitalCommonsData.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnect(REPO_DISPLAY_NAME, { successName: "Digital Commons Data" });
  });

  test("As a user, I can export a document to Digital Commons Data and receive a completion notification", async ({
    clientDocuments,
    pageDocument,
    componentExportWizard,
    componentNotifications,
    componentToasts,
    page,
  }) => {
    await expectRepositoryDepositCompletes({
      repoDisplayName: REPO_DISPLAY_NAME,
      selectRepository: (wizard) => wizard.selectRepository(REPO_DISPLAY_NAME),
      clientDocuments,
      pageDocument,
      componentExportWizard,
      componentNotifications,
      componentToasts,
      page,
    });
  });
});
