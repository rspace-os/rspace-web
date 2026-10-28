import { type APIRequestContext, expect } from "@playwright/test";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

async function createOtherUsersContainer(clientSysadmin: SysadminClient, apiContext: APIRequestContext) {
  const owner = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2ePermAlice");
  const ownerClient = new InventoryClient(apiContext, owner.apiKey);
  const container = await ownerClient.createContainer({
    name: uniqueName("e2e-perm-public-container"),
    cType: "GRID",
    gridLayout: { columnsNumber: 3, rowsNumber: 3 },
  });
  return { container, ownerClient };
}

test.describe(`Inventory public access`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can find a record I can only see publicly by its Global ID, but not by its name`, async ({
    pageInventory,
    clientSysadmin,
    apiContext,
  }) => {
    const { container, ownerClient } = await test.step("Given another user owns a Container I have no access to", () =>
      createOtherUsersContainer(clientSysadmin, apiContext));

    // Without this, the name search below would also come back empty if searching by this name found nothing at all.
    await expect
      .poll(async () => (await ownerClient.search(container.name, "CONTAINER")).map(({ globalId }) => globalId))
      .toContain(container.globalId);

    await pageInventory.openSearch("CONTAINER");
    await pageInventory.searchPanel.search(container.name);
    // Name search is full-text, so other records may match; the status proves these results have rendered.
    await expect(pageInventory.searchPanel.statusText).toContainText("search results found");
    await expect(pageInventory.searchPanel.row(container.name)).toHaveCount(0);

    await pageInventory.searchPanel.search(container.globalId);
    await expect(pageInventory.searchPanel.row(container.name)).toBeVisible();
  });

  test(`As a user, I can add a record I can only see publicly to a basket and find it there`, async ({
    pageInventory,
    clientInventory,
    clientSysadmin,
    apiContext,
  }) => {
    const { container } = await test.step("Given another user owns a Container I have no access to", () =>
      createOtherUsersContainer(clientSysadmin, apiContext));
    const basketName = uniqueName("e2e-perm-basket");

    await pageInventory.openRecordById("container", container.id);
    await expect(pageInventory.detailsPanel.heading).toContainText(container.name);
    await expect(pageInventory.detailsPanel.permissionAlert).toContainText(
      "You do not have permission to see any of the details",
    );

    try {
      const addDialog = await pageInventory.detailsPanel.openAddToBasketDialog();
      await addDialog.enterNewBasketName(basketName);
      await addDialog.confirm();

      await pageInventory.searchPanel.ensureVisible();
      const basketsMenu = await pageInventory.searchPanel.openBasketsMenu();
      await basketsMenu.openBasket(basketName);
      await expect(pageInventory.searchPanel.row(container.name)).toBeVisible();
    } finally {
      const basket = (await clientInventory.getBaskets()).find(({ name }) => name === basketName);
      if (basket) await clientInventory.deleteBasket(basket.id);
    }
  });
});
