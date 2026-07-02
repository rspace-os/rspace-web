import type { ApiDocument } from "@/__tests__/e2e/api/models/document";
import { expect, tags, test } from "@/__tests__/e2e/fixtures";

test.describe(`Workspace ${tags.SMOKE}`, () => {
  let createdIds: number[] = [];

  test.afterEach(async ({ clientDocuments }) => {
    for (const id of createdIds) {
      await clientDocuments.deleteById(id).catch(() => {});
    }
    createdIds = [];
  });

  test("As a user, I can find an API-created document, view its info, duplicate it, and locate both via simple and advanced search", async ({
    flowLogin,
    clientDocuments,
  }) => {
    const name = `e2e-workspace-${Date.now()}`;
    let doc!: ApiDocument;

    await test.step("Given I have created a document via the API", async () => {
      doc = await clientDocuments.create({ name });
      createdIds.push(doc.id);
    });

    await test.step("When I simple-search the Workspace for the document by name", async () => {
      await flowLogin.searchBar.search(name);
    });

    await test.step("Then the document appears in the results with the expected Global ID", async () => {
      await expect(flowLogin.table.row(name)).toBeVisible();
      await expect(flowLogin.table.globalIdLink(name)).toHaveText(doc.globalId);
    });

    await test.step("And its Record Info shows the expected name and unique id", async () => {
      const info = await flowLogin.table.openInfoFor(name);
      expect(await info.field("Name")).toBe(name);
      expect(await info.field("Unique Id")).toBe(doc.globalId);
      await info.close();
    });

    let copyName!: string;
    await test.step("When I select the document and duplicate it", async () => {
      await flowLogin.table.selectRecord(name);
      await flowLogin.selectionBar.waitUntilVisible();
      await flowLogin.selectionBar.clickAction("Duplicate");
      copyName = `${name}_Copy`;
    });

    await test.step("Then a copy appears in the still-filtered results", async () => {
      const copyRow = flowLogin.table.row(copyName);
      await expect(copyRow).toBeVisible();
      const copyGlobalId = await flowLogin.table.globalIdLink(copyName).innerText();
      createdIds.push(Number(copyGlobalId.replace(/^SD/, "")));
    });

    await test.step("When I clear the search and use advanced search to find both by Name", async () => {
      await flowLogin.searchBar.clearSearch();
      await flowLogin.searchBar.openAdvanced();
      await flowLogin.searchBar.setRowType(0, "Name");
      await flowLogin.searchBar.setRowValue(0, name);
      await flowLogin.searchBar.submitAdvanced();
    });

    await test.step("Then both the original and the duplicate appear in the advanced-search results", async () => {
      await expect(flowLogin.table.row(name)).toBeVisible();
      await expect(flowLogin.table.row(copyName)).toBeVisible();
    });
  });
});
