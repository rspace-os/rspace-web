import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

test.describe(`Workspace sorting, pagination, and state`, () => {
  test(`As a user, changing items-per-page changes how many rows are shown`, async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const token = alphaNumericUnique("e2esortpgitems");
    const names = Array.from({ length: 12 }, (_, i) => `e2e-sort-pg-items-${token}-${i}`);

    await test.step("Given 12 documents exist, more than the default page size", async () => {
      for (const name of names) {
        await clientDocuments.create({ name });
      }
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(token);
    });

    await test.step("Then the default items-per-page (10) shows at most 10 rows", async () => {
      await expect(pageWorkspace.pagination.itemsPerPageSelect).toHaveValue("10");
      expect(await pageWorkspace.table.rowCount()).toBeLessThanOrEqual(10);
    });

    await test.step("When I change items-per-page to 30", async () => {
      await pageWorkspace.pagination.setItemsPerPage(30);
    });

    await test.step("Then all 12 rows are shown on a single page", async () => {
      expect(await pageWorkspace.table.rowCount()).toBe(12);
    });
  });

  test(`As a user, a simple search's result set survives an unrelated re-sort`, async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const token = alphaNumericUnique("e2esortpgsearch");
    const names = ["charlie", "alpha", "bravo"].map((n) => `e2e-sort-pg-search-${token}-${n}`);

    await test.step("Given 3 documents sharing a unique search token exist", async () => {
      for (const name of names) {
        await clientDocuments.create({ name });
      }
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(token);
    });

    await test.step("Then the search shows exactly those 3 documents", async () => {
      expect(await pageWorkspace.table.rowCount()).toBe(3);
    });

    await test.step("When I sort by Name", async () => {
      await pageWorkspace.table.sortBy("Name");
    });

    await test.step("Then the search filter is still active and still shows exactly those 3 documents", async () => {
      expect(await pageWorkspace.searchBar.isSearchActive()).toBe(true);
      expect(await pageWorkspace.table.rowCount()).toBe(3);
      for (const name of names) {
        await expect(pageWorkspace.table.row(name)).toBeVisible();
      }
    });

    await test.step("When I sort by Name again", async () => {
      await pageWorkspace.table.sortBy("Name");
      expect(await pageWorkspace.searchBar.isSearchActive()).toBe(true);
      expect(await pageWorkspace.table.rowCount()).toBe(3);
    });
  });
});
