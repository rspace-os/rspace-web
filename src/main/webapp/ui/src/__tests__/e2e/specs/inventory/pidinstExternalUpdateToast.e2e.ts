import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { getB2instDraftUpdate } from "@/__tests__/e2e/mocks/b2instControl";
import { FORCE_EXTERNAL_UPDATE_FAILURE_SENTINEL } from "@/__tests__/e2e/mocks/datacite";
import { getDataciteUpdateCount } from "@/__tests__/e2e/mocks/dataciteControl";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Inventory PIDINST external metadata update on save`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test.describe(`B2INST provider`, () => {
    test.skip(
      INTEGRATION_MODE === "real" && !(env.pidinstB2instCommunityId && env.pidinstB2instToken),
      "real mode needs PIDINST_B2INST_COMMUNITY_ID and PIDINST_B2INST_TOKEN",
    );

    test(`As a user, saving an instrument with a Draft PIDINST identifier updates it silently`, async ({
      pageInventory,
      clientInventory,
      componentToasts,
      flowPidinstB2instConfig,
    }) => {
      void flowPidinstB2instConfig;
      const instrument = await clientInventory.createInstrument({ name: uniqueName("e2e-pidinst-toast-b2inst") });
      const info = await clientInventory.registerIdentifier({ parentGlobalId: instrument.globalId });
      // A name the provider has never seen, so the push must carry the edit rather than registration-time metadata.
      const renamed = uniqueName("e2e-pidinst-toast-b2inst-renamed");

      await pageInventory.openInstrument(instrument.id);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.rename(renamed);
      await pageInventory.detailsPanel.saveEdit();

      await expect(componentToasts.byVariant("success", "updated successfully.")).toBeVisible();
      await expect(componentToasts.byText("Instrument PID")).toHaveCount(0);

      if (INTEGRATION_MODE !== "real") {
        const draftUpdate = await getB2instDraftUpdate(info.doi);
        expect(draftUpdate?.metadata?.Name).toBe(renamed);
      }
    });
  });

  test.describe(`DataCite provider`, () => {
    test.skip(
      INTEGRATION_MODE === "real" && !(env.igsnAccountId && env.igsnPassword && env.igsnRepoPrefix),
      "real mode needs IGSN_ACCOUNT_ID, IGSN_PASSWORD, and IGSN_REPO_PREFIX",
    );

    test(`As a user, saving an instrument with a Findable PIDINST identifier leaves it unchanged`, async ({
      pageInventory,
      clientInventory,
      componentToasts,
      flowPidinstDataciteConfig,
    }) => {
      void flowPidinstDataciteConfig;
      const instrument = await clientInventory.createInstrument({ name: uniqueName("e2e-pidinst-toast-datacite") });
      const info = await clientInventory.registerIdentifier({ parentGlobalId: instrument.globalId });
      await clientInventory.publishIdentifier(info.id);
      const updatesBeforeSave = INTEGRATION_MODE === "real" ? 0 : await getDataciteUpdateCount(info.doi);

      await pageInventory.openInstrument(instrument.id);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.saveEdit();

      const toast = componentToasts.byVariant("notice", "Instrument PID left unchanged");
      await expect(toast).toBeVisible();
      await expect(toast).toContainText("Publishing or republishing the identifier sends its current metadata.");
      if (INTEGRATION_MODE !== "real") {
        expect(await getDataciteUpdateCount(info.doi)).toBe(updatesBeforeSave);
      }
    });

    test(`As a user, saving an instrument fails to update a PIDINST identifier the provider rejects`, async ({
      pageInventory,
      clientInventory,
      componentToasts,
      flowPidinstDataciteConfig,
    }, testInfo) => {
      testInfo.skip(INTEGRATION_MODE === "real", "the forced-failure sentinel is only honoured by the mock server");
      void flowPidinstDataciteConfig;
      const instrument = await clientInventory.createInstrument({
        name: uniqueName(FORCE_EXTERNAL_UPDATE_FAILURE_SENTINEL),
      });
      await clientInventory.registerIdentifier({ parentGlobalId: instrument.globalId });

      await pageInventory.openInstrument(instrument.id);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.saveEdit();

      const toast = componentToasts.byVariant("error", "Instrument PID not updated");
      await expect(toast).toBeVisible();
      await expect(toast).toContainText("saving it again will try the update once more");
    });
  });
});
