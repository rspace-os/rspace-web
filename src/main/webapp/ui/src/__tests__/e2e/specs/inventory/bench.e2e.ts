import { expect } from "@playwright/test";
import { storageStatePath } from "@/__tests__/e2e/authState";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { USERS } from "@/__tests__/e2e/users";

test.describe(`Inventory My Bench`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can move an item from another location to My Bench`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
    page,
  }) => {
    const containerName = uniqueName("e2e-bench-list-container");
    const sampleName = uniqueName("e2e-bench-sample");
    const subsampleName = `${sampleName}.01`;

    await clientInventory.createContainer({ name: containerName, cType: "LIST" });

    await page.goto("/inventory/search?resultType=CONTAINER");
    await pageInventory.searchPanel.search(containerName);
    await pageInventory.searchPanel.open(containerName);

    const createDialog = await pageInventory.detailsPanel.openCreateItemDialog();
    const newSample = await createDialog.chooseSample();

    await expect(newSample.root.getByRole("heading", { level: 2, name: "New Sample" })).toBeVisible();

    await newSample.fillName(sampleName);
    await newSample.save();

    await expect(componentToasts.byText("successfully created.")).toBeVisible();

    await page.goto("/inventory/search?resultType=SUBSAMPLE");
    await pageInventory.searchPanel.search(subsampleName);
    await pageInventory.searchPanel.open(subsampleName);

    const subsampleUrl =
      (await pageInventory.detailsPanel.root
        .getByRole("link", { name: /^SS\d+$/ })
        .first()
        .getAttribute("href")) ?? "";
    const moveDialog = await pageInventory.detailsPanel.openMoveDialog();
    await moveDialog.confirmMove();

    await expect(componentToasts.byText("Subsample successfully moved.")).toBeVisible();

    await page.goto(subsampleUrl);

    const breadcrumb = pageInventory.detailsPanel.section("Overview").getByRole("navigation", { name: "breadcrumb" });
    await expect(breadcrumb.getByRole("listitem").first()).toHaveText("My Bench");
    await expect(breadcrumb).not.toContainText(containerName);
  });

  test.describe("lab-group member", () => {
    test.use({ appUser: USERS.user3c, storageState: storageStatePath(USERS.user3c.username) });

    test(`As a lab-group member, I can see the group's other members`, async ({ page, pageInventory }) => {
      await page.goto("/inventory/search?resultType=SAMPLE");
      await pageInventory.isLoaded();

      await pageInventory.searchPanel.filterChip("Bench").click();
      await expect(page.getByRole("option", { name: "user4d" })).toBeVisible();

      await page.reload();
      await pageInventory.isLoaded();

      await pageInventory.searchPanel.filterChip("Owner").click();
      await expect(page.getByRole("option", { name: "user4d" })).toBeVisible();
    });
  });
});
