import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

const INTEGRATION_MODE = env.integrationMode;

test.describe(`Inventory IGSN Identifiers`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test.skip(
    INTEGRATION_MODE === "real" && !(env.igsnAccountId && env.igsnPassword && env.igsnRepoPrefix),
    "real mode needs IGSN_ACCOUNT_ID, IGSN_PASSWORD, and IGSN_REPO_PREFIX",
  );

  test(`As a user, I can mint, preview, and publish a Draft identifier`, async ({
    pageInventory,
    componentToasts,
    flowIgsnConfig,
    page,
  }) => {
    void flowIgsnConfig;
    const sampleName = uniqueName("e2e-igsn-sample");
    const today = String(new Date().getDate());

    await pageInventory.open();
    await pageInventory.isLoaded();
    const menu = await pageInventory.openCreateMenu();
    const form = await menu.newSample();
    await form.fillName(sampleName);
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible({ timeout: 30_000 });

    const identifiers = pageInventory.detailsPanel.identifiers();
    await pageInventory.detailsPanel.enterEditMode();
    const createDialog = await pageInventory.detailsPanel.createIdentifier();
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

  test(`As a user, I can retract a published identifier`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
    flowIgsnConfig,
    page,
  }) => {
    void flowIgsnConfig;
    const containerName = uniqueName("e2e-igsn-retract-container");

    const container = await test.step("Given an identifier in a Findable state exists", async () => {
      const created = await clientInventory.createContainer({ name: containerName, cType: "LIST" });
      const info = await clientInventory.registerIdentifier({ parentGlobalId: created.globalId });
      await clientInventory.publishIdentifier(info.id);
      return created;
    });

    const identifiers = pageInventory.detailsPanel.identifiers();
    await page.goto(`/inventory/container/${container.id}`);
    await pageInventory.detailsPanel.expandSection("Identifiers");
    await identifiers.waitForState("Findable");

    await identifiers.clickRetract();
    await expect(componentToasts.byText("has been retracted")).toBeVisible();

    await identifiers.waitForState("Registered");
  });

  test(`As a user, I can delete a Draft identifier`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
    flowIgsnConfig,
    page,
  }) => {
    void flowIgsnConfig;
    const containerName = uniqueName("e2e-igsn-delete-container");

    const container = await test.step("Given an identifier in a Draft state exists", async () => {
      const created = await clientInventory.createContainer({ name: containerName, cType: "LIST" });
      await clientInventory.registerIdentifier({ parentGlobalId: created.globalId });
      return created;
    });

    const identifiers = pageInventory.detailsPanel.identifiers();
    await page.goto(`/inventory/container/${container.id}`);
    await pageInventory.detailsPanel.expandSection("Identifiers");
    await identifiers.waitForState("Draft");

    await identifiers.clickDelete();
    await expect(componentToasts.byText("deleted")).toBeVisible();

    await expect(identifiers.root.getByText("Draft", { exact: true })).toHaveCount(0);
  });
});
