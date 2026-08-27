import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";
import type { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const INTEGRATION_MODE = env.integrationMode;

async function deleteDraftPidinstIdentifier(
  namePrefix: string,
  {
    pageInventory,
    clientInventory,
    componentToasts,
    page,
  }: { pageInventory: InventoryPage; clientInventory: InventoryClient; componentToasts: ToastsComponent; page: Page },
): Promise<void> {
  const instrumentName = uniqueName(namePrefix);

  const instrument = await test.step("Given a Draft PIDINST identifier exists for an Instrument", async () => {
    const created = await clientInventory.createInstrument({ name: instrumentName });
    await clientInventory.registerIdentifier({ parentGlobalId: created.globalId });
    return created;
  });

  const identifiers = pageInventory.detailsPanel.identifiers();
  await page.goto(`/inventory/instrument/${instrument.id}`);
  await pageInventory.detailsPanel.expandSection("Identifiers");
  await identifiers.waitForState("Draft");

  await identifiers.clickDelete();
  await expect(componentToasts.byText("deleted")).toBeVisible();

  await expect(identifiers.root.getByText("Draft", { exact: true })).toHaveCount(0);
}

test.describe(`Inventory PIDINST Identifiers`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test.describe(`B2INST provider`, () => {
    test.skip(
      INTEGRATION_MODE === "real" && !(env.pidinstB2instCommunityId && env.pidinstB2instToken),
      "real mode needs PIDINST_B2INST_COMMUNITY_ID and PIDINST_B2INST_TOKEN",
    );

    test(`As a user, I can mint and preview a Draft PIDINST identifier for an Instrument`, async ({
      pageInventory,
      componentToasts,
      flowPidinstB2instConfig,
      page,
    }) => {
      void flowPidinstB2instConfig;
      const instrumentName = uniqueName("e2e-pidinst-instrument");
      const today = String(new Date().getDate());

      await pageInventory.open();
      await pageInventory.isLoaded();
      const menu = await pageInventory.openCreateMenu();
      const form = await menu.newInstrument();
      await form.fillName(instrumentName);
      await form.save();

      await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible({ timeout: 30_000 });

      const identifiers = pageInventory.detailsPanel.identifiers();
      await pageInventory.detailsPanel.enterEditMode();
      const createDialog = await pageInventory.detailsPanel.createIdentifier("PIDINST");
      await createDialog.confirm(identifiers);

      await identifiers.addSubject({
        subject: "TestSubject",
        schema: "TestSchema",
        schemaUri: "https://example.com/schema",
        valueUri: "https://example.com/value",
        code: "12345678",
      });
      await identifiers.addDescription("Abstract", "TestDescriptionAbstract");
      await identifiers.addDateAndEventType(today, "Accepted");
      await pageInventory.detailsPanel.saveEdit();
      await expect(componentToasts.byVariant("success", "updated successfully.")).toBeVisible();

      await identifiers.waitForState("Draft");

      await identifiers.clickPreview();
      await expect(identifiers.subjects).toBeVisible();
      await page.getByRole("button", { name: "Close", exact: true }).click();
    });

    test(`As a user, I can delete a Draft PIDINST identifier`, async ({
      pageInventory,
      clientInventory,
      componentToasts,
      flowPidinstB2instConfig,
      page,
    }) => {
      void flowPidinstB2instConfig;
      await deleteDraftPidinstIdentifier("e2e-pidinst-delete-instrument", {
        pageInventory,
        clientInventory,
        componentToasts,
        page,
      });
    });
  });

  test.describe(`DataCite provider`, () => {
    test.skip(
      INTEGRATION_MODE === "real" && !(env.igsnAccountId && env.igsnPassword && env.igsnRepoPrefix),
      "real mode needs IGSN_ACCOUNT_ID, IGSN_PASSWORD, and IGSN_REPO_PREFIX",
    );

    test(`As a user, I can mint, preview, and publish a Draft PIDINST identifier for an Instrument`, async ({
      pageInventory,
      componentToasts,
      flowPidinstDataciteConfig,
      page,
    }) => {
      void flowPidinstDataciteConfig;
      const instrumentName = uniqueName("e2e-pidinst-datacite-instrument");
      const today = String(new Date().getDate());

      await pageInventory.open();
      await pageInventory.isLoaded();
      const menu = await pageInventory.openCreateMenu();
      const form = await menu.newInstrument();
      await form.fillName(instrumentName);
      await form.save();

      await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible({ timeout: 30_000 });

      const identifiers = pageInventory.detailsPanel.identifiers();
      await pageInventory.detailsPanel.enterEditMode();
      const createDialog = await pageInventory.detailsPanel.createIdentifier("PIDINST");
      await createDialog.confirm(identifiers);

      await identifiers.addSubject({
        subject: "TestSubject",
        schema: "TestSchema",
        schemaUri: "https://example.com/schema",
        valueUri: "https://example.com/value",
        code: "12345678",
      });
      await identifiers.addDescription("Abstract", "TestDescriptionAbstract");
      await identifiers.addDateAndEventType(today, "Created");
      await pageInventory.detailsPanel.saveEdit();
      await expect(componentToasts.byVariant("success", "updated successfully.")).toBeVisible();

      await identifiers.waitForState("Draft");

      await identifiers.clickPreview();
      await expect(identifiers.subjects).toBeVisible();
      await page.getByRole("button", { name: "Close", exact: true }).click();

      await identifiers.clickPublish();
      await expect(componentToasts.byText("published")).toBeVisible();

      await identifiers.waitForState("Findable");
    });

    test(`As a user, I can retract a published PIDINST identifier`, async ({
      pageInventory,
      clientInventory,
      componentToasts,
      flowPidinstDataciteConfig,
      page,
    }) => {
      void flowPidinstDataciteConfig;
      const instrumentName = uniqueName("e2e-pidinst-datacite-retract-instrument");

      const instrument = await test.step("Given an identifier in a Findable state exists", async () => {
        const created = await clientInventory.createInstrument({ name: instrumentName });
        const info = await clientInventory.registerIdentifier({ parentGlobalId: created.globalId });
        await clientInventory.publishIdentifier(info.id);
        return created;
      });

      const identifiers = pageInventory.detailsPanel.identifiers();
      await page.goto(`/inventory/instrument/${instrument.id}`);
      await pageInventory.detailsPanel.expandSection("Identifiers");
      await identifiers.waitForState("Findable");

      await identifiers.clickRetract();
      await expect(componentToasts.byText("has been retracted")).toBeVisible();

      await identifiers.waitForState("Registered");
    });

    test(`As a user, I can delete a Draft PIDINST identifier`, async ({
      pageInventory,
      clientInventory,
      componentToasts,
      flowPidinstDataciteConfig,
      page,
    }) => {
      void flowPidinstDataciteConfig;
      await deleteDraftPidinstIdentifier("e2e-pidinst-datacite-delete-instrument", {
        pageInventory,
        clientInventory,
        componentToasts,
        page,
      });
    });
  });
});
