import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Inventory baskets", { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can add an item to a basket and remove it`, async ({ pageInventory, clientInventory, page }) => {
    const sampleName = uniqueName("e2e-basket-sample");
    const basketName = uniqueName("e2e-basket");

    await clientInventory.createSample({ name: sampleName });

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.searchPanel.search(sampleName);
    await pageInventory.searchPanel.selectItem(sampleName);

    const addDialog = await pageInventory.searchPanel.batchActions.openAddToBasketDialog();
    await addDialog.enterNewBasketName(basketName);
    await addDialog.confirm();

    const basketsMenu1 = await pageInventory.searchPanel.openBasketsMenu();
    await expect(basketsMenu1.basketItem(basketName)).toBeVisible();
    await basketsMenu1.close();

    const basketsMenu2 = await pageInventory.searchPanel.openBasketsMenu();
    await basketsMenu2.deleteBasket(basketName);

    await basketsMenu2.close();
    const reopenedMenu = await pageInventory.searchPanel.openBasketsMenu();
    await expect(reopenedMenu.basketItem(basketName)).toHaveCount(0);
    await reopenedMenu.close();
  });

  test(`As a user, I can edit the name of an existing basket`, async ({ pageInventory, clientInventory, page }) => {
    const basketName = uniqueName("e2e-bskt-a");
    const newBasketName = uniqueName("e2e-bskt-b");

    const basket = await clientInventory.createBasket({ name: basketName });
    const container = await clientInventory.createContainer({
      name: uniqueName("e2e-basket-container"),
      cType: "GRID",
      gridLayout: { columnsNumber: 3, rowsNumber: 3 },
    });
    await clientInventory.addItemsToBasket(basket.id, [container.globalId]);

    await page.goto("/inventory/search?resultType=SAMPLE");
    await pageInventory.isLoaded();

    const basketsMenu1 = await pageInventory.searchPanel.openBasketsMenu();
    await expect(basketsMenu1.basketItem(basketName)).toBeVisible();

    await basketsMenu1.renameBasket(basketName, newBasketName);

    await page.keyboard.press("Escape");

    const basketsMenu2 = await pageInventory.searchPanel.openBasketsMenu();
    const basketItem = basketsMenu2.basketItem(newBasketName);
    await expect(basketItem).toBeVisible();
    await expect(basketItem).toContainText("1");
    await basketsMenu2.close();
  });
});
