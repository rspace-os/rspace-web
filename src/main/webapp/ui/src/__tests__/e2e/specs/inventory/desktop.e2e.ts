import type { Locator } from "@playwright/test";
import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

async function positions(firstCard: Locator, secondCard: Locator) {
  await expect(firstCard).toBeVisible();
  await expect(secondCard).toBeVisible();
  const [first, second] = await Promise.all([firstCard.boundingBox(), secondCard.boundingBox()]);
  if (!first || !second) throw new Error("Expected both cards to have measurable bounding boxes.");
  return { first, second };
}

test.describe(`Inventory Desktop layout`, { tag: tags.INVENTORY }, () => {
  test(`As a user, I can view cards in responsive columns`, async ({ pageInventory, clientInventory, page }) => {
    const sampleName = uniqueName("e2e-desktop-card-sample");

    const sample = await clientInventory.createSample({ name: sampleName, newSampleSubSamplesCount: 30 });
    await page.goto(`/inventory/search?parentGlobalId=${sample.globalId}`);
    await pageInventory.isLoaded();
    await expect(pageInventory.searchPanel.statusText).toContainText("30 subsamples found.");

    await pageInventory.searchPanel.changeView("Card");

    await pageInventory.searchPanel.showRightPanel();
    const firstCard = pageInventory.searchPanel.card(0);
    const secondCard = pageInventory.searchPanel.card(1);
    const { first, second } = await positions(firstCard, secondCard);
    expect(second.y).not.toBe(first.y);
    expect(second.x).toBe(first.x);

    if (await pageInventory.searchPanel.hasRightPanelToggle()) {
      await pageInventory.searchPanel.hideRightPanel();

      await pageInventory.searchPanel.ensureVisible();
      const firstCard = pageInventory.searchPanel.card(0);
      const secondCard = pageInventory.searchPanel.card(1);
      const { first, second } = await positions(firstCard, secondCard);
      expect(second.y).toBe(first.y);
      expect(second.x).toBeGreaterThan(first.x);
    }
  });

  test(`As a user, I can view multiple adjustable List columns`, async ({ pageInventory, clientInventory, page }) => {
    const sampleName = uniqueName("e2e-desktop-list-sample");

    const sample = await clientInventory.createSample({ name: sampleName, newSampleSubSamplesCount: 30 });
    await page.goto(`/inventory/search?parentGlobalId=${sample.globalId}`);
    await pageInventory.isLoaded();

    const hasToggle = await pageInventory.searchPanel.hasRightPanelToggle();

    await pageInventory.searchPanel.hideRightPanel();

    await expect.poll(() => pageInventory.searchPanel.columnOptionsCount()).toBeGreaterThanOrEqual(hasToggle ? 2 : 1);
  });

  test(`As a user, I can open a record preview and return to its search results`, async ({
    pageInventory,
    clientInventory,
    page,
  }) => {
    const sampleName = uniqueName("e2e-desktop-preview-sample");

    const sample = await clientInventory.createSample({ name: sampleName, newSampleSubSamplesCount: 30 });
    await page.goto(`/inventory/search?parentGlobalId=${sample.globalId}`);
    await pageInventory.isLoaded();

    await expect.poll(() => pageInventory.searchPanel.rowCount()).toBe(10);

    await pageInventory.searchPanel.hideRightPanel();
    await pageInventory.searchPanel.openFirstResult();

    await expect(pageInventory.detailsPanel.heading).toContainText(sampleName);

    await pageInventory.searchPanel.ensureVisible();
    await expect(pageInventory.searchPanel.statusText).toContainText("30 subsamples found.");
  });

  test(`As a user, I can follow an external link from a record description`, async ({
    pageInventory,
    componentToasts,
    page,
  }) => {
    const containerName = uniqueName("e2e-desktop-link-container");

    const form = await test.step("Given I start creating a new List container", async () => {
      await pageInventory.open();
      await pageInventory.isLoaded();
      const menu = await pageInventory.openCreateMenu();
      return menu.newContainer();
    });

    await form.fillName(containerName);
    await form.expandSection("Details");
    await form.insertDescriptionLink("https://researchspace.com");
    await form.save();

    await expect(componentToasts.byVariant("success", "successfully created")).toBeVisible();

    await page.route("https://researchspace.com/**", (route) =>
      route.fulfill({ contentType: "text/html", body: "<title>ResearchSpace</title>" }),
    );
    await pageInventory.detailsPanel.expandSection("Details");
    await pageInventory.detailsPanel
      .section("Details")
      .getByRole("group", { name: "Description" })
      .getByRole("link")
      .click();

    await expect(page).toHaveURL("https://researchspace.com/");
  });

  test(`As a user, I can switch from paginated List results to Tree view`, async ({
    pageInventory,
    clientInventory,
    page,
  }) => {
    const sampleName = uniqueName("e2e-desktop-tree-sample");

    const sample = await clientInventory.createSample({ name: sampleName, newSampleSubSamplesCount: 30 });
    await page.goto(`/inventory/search?parentGlobalId=${sample.globalId}`);
    await pageInventory.isLoaded();

    await expect(pageInventory.searchPanel.nextPageButton).toBeVisible();

    await pageInventory.searchPanel.changeView("Tree");

    await expect(pageInventory.searchPanel.tree()).toBeVisible();
    await expect(pageInventory.searchPanel.nextPageButton).not.toBeVisible();
  });
});
