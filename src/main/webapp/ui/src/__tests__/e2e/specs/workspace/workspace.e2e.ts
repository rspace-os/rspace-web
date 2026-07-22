import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Workspace", () => {
  let createdIds: number[] = [];

  test.afterEach(async ({ clientDocuments }) => {
    for (const id of createdIds) {
      await clientDocuments.deleteById(id);
    }
    createdIds = [];
  });

  test("As a user, I can find an API-created document, view its info, duplicate it, and locate both via simple and advanced search", async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const name = uniqueName("e2e-workspace");
    const doc = await clientDocuments.create({ name });
    createdIds.push(doc.id);
    await pageWorkspace.open();
    if (!(await pageWorkspace.isLoaded())) throw new Error("Workspace did not load.");

    await pageWorkspace.searchBar.search(name);

    await expect(pageWorkspace.table.row(name)).toBeVisible();
    await expect(pageWorkspace.table.globalIdLink(name)).toHaveText(doc.globalId);

    const info = await pageWorkspace.table.openInfoFor(name);
    await expect.poll(() => info.field("Name")).toBe(name);
    await expect.poll(() => info.field("Unique Id")).toBe(doc.globalId);
    await info.close();

    await pageWorkspace.table.selectRecord(name);
    await pageWorkspace.selectionBar.waitUntilVisible();
    await pageWorkspace.selectionBar.clickAction("Duplicate");
    const copyName = `${name}_Copy`;

    const copyRow = pageWorkspace.table.row(copyName);
    await expect(copyRow).toBeVisible();
    const copyGlobalId = await pageWorkspace.table.globalIdLink(copyName).innerText();
    createdIds.push(Number(copyGlobalId.replace("SD", "")));

    await pageWorkspace.searchBar.clearSearch();
    await pageWorkspace.searchBar.openAdvanced();
    await pageWorkspace.searchBar.setRowType(0, "Name");
    await pageWorkspace.searchBar.setRowValue(0, name);
    await pageWorkspace.searchBar.submitAdvanced();

    await expect(pageWorkspace.table.row(name)).toBeVisible();
    await expect(pageWorkspace.table.row(copyName)).toBeVisible();
  });
});
