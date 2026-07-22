import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";

const EXPECTED = { eartagOrId: "MOCK-001" };

const INTEGRATION_MODE = env.integrationMode;
const PYRAT_ALIAS = "mock";
const PYRAT_API_KEY = INTEGRATION_MODE === "real" ? env.pyratApiKey : "mock-pyrat-token";

test.describe(`PyRAT integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSetting("pyrat.available", "ALLOWED");
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithApiKey("PyRAT", PYRAT_API_KEY, {
      textboxName: `API Key for ${PYRAT_ALIAS}`,
      serverAlias: PYRAT_ALIAS,
    });
  });

  test("As a user, I can insert a PyRAT animal into a document field", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const docEditor = await pageWorkspace.createBasicDocument();
    const dialog = await docEditor.openPyratDialog();

    await dialog.selectAnimal(EXPECTED.eartagOrId);
    await dialog.clickInsert();

    const field = await docEditor.getField("New List of Materials");
    await expect.poll(() => field.getText()).toContain(EXPECTED.eartagOrId);
  });
});
