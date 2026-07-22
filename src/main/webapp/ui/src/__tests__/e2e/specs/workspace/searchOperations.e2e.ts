import { expect } from "@playwright/test";
import { storageStatePath } from "@/__tests__/e2e/authState";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";

test.describe(`Workspace search operations`, () => {
  test(`As a user, I can simple-search by wildcard name and by full-text content`, async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-search-wildcard-doc");
    const wildcardToken = docName.split("-").pop();

    await test.step("Given a document exists", async () => {
      await clientDocuments.create({ name: docName });
      await pageWorkspace.open();
    });

    await test.step("Then I can find it by a hyphen-free wildcard prefix of its name", async () => {
      await pageWorkspace.searchBar.search(`${wildcardToken}*`);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
      await pageWorkspace.searchBar.clearSearch();
    });

    await test.step("And I can find it by its full name", async () => {
      await pageWorkspace.searchBar.search(docName);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });
  });

  test(`As a user, I can simple-search by owner`, async ({ pageWorkspace, clientDocuments, appUser }) => {
    const docName = uniqueName("e2e-search-owner-doc");

    await test.step("Given a document exists, owned by me", async () => {
      await clientDocuments.create({ name: docName });
      await pageWorkspace.open();
    });

    await test.step("When I search by my own username as owner", async () => {
      await pageWorkspace.searchBar.searchByOwner(appUser.username);
    });

    await test.step("Then my document is found", async () => {
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });
  });

  test(`As a user, advanced search with Name AND Tag(s) matches only when both conditions hold`, async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-search-and-doc");
    const tag = uniqueName("e2e-search-and-tag");
    const nonMatchingTag = uniqueName("e2e-search-and-tag-other");

    await test.step("Given a tagged document and a differently-tagged one exist", async () => {
      await clientDocuments.create({ name: docName, tags: tag });
      await clientDocuments.create({ name: uniqueName("e2e-search-and-other-doc"), tags: nonMatchingTag });
      await pageWorkspace.open();
      await pageWorkspace.searchBar.openAdvanced();
    });

    await test.step("Then Name=<name> AND Tag(s)=<tag> finds it", async () => {
      const row0 = await pageWorkspace.searchBar.addRow();
      await pageWorkspace.searchBar.setRowType(row0, "Name");
      await pageWorkspace.searchBar.setRowValue(row0, docName);
      const row1 = await pageWorkspace.searchBar.addRow();
      await pageWorkspace.searchBar.setRowType(row1, "Tag(s)");
      await pageWorkspace.searchBar.setRowTag(row1, tag);
      await pageWorkspace.searchBar.setMatchMode("all");
      await pageWorkspace.searchBar.submitAdvanced();
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });

    await test.step("Then Name=<name> AND Tag(s)=<the other document's tag> finds nothing", async () => {
      await pageWorkspace.searchBar.resetAdvanced();
      const row0 = await pageWorkspace.searchBar.addRow();
      await pageWorkspace.searchBar.setRowType(row0, "Name");
      await pageWorkspace.searchBar.setRowValue(row0, docName);
      const row1 = await pageWorkspace.searchBar.addRow();
      await pageWorkspace.searchBar.setRowType(row1, "Tag(s)");
      await pageWorkspace.searchBar.setRowTag(row1, nonMatchingTag);
      await pageWorkspace.searchBar.setMatchMode("all");
      await pageWorkspace.searchBar.submitAdvanced();
      await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
    });
  });

  test(`As a user, advanced search with OR matches either of two documents`, async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const doc1 = uniqueName("e2e-search-or-doc1");
    const doc2 = uniqueName("e2e-search-or-doc2");

    await test.step("Given two unrelated documents exist", async () => {
      await clientDocuments.create({ name: doc1 });
      await clientDocuments.create({ name: doc2 });
      await pageWorkspace.open();
    });

    await test.step("When I search Name=doc1 OR Name=doc2", async () => {
      await pageWorkspace.searchBar.openAdvanced();
      const row0 = await pageWorkspace.searchBar.addRow();
      await pageWorkspace.searchBar.setRowType(row0, "Name");
      await pageWorkspace.searchBar.setRowValue(row0, doc1);
      const row1 = await pageWorkspace.searchBar.addRow();
      await pageWorkspace.searchBar.setRowType(row1, "Name");
      await pageWorkspace.searchBar.setRowValue(row1, doc2);
      await pageWorkspace.searchBar.setMatchMode("any");
      await pageWorkspace.searchBar.submitAdvanced();
    });

    await test.step("Then both documents are found", async () => {
      await expect(pageWorkspace.table.row(doc1)).toBeVisible();
      await expect(pageWorkspace.table.row(doc2)).toBeVisible();
    });
  });

  test(`As a user, the Favorites filter combines with an active simple search`, async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const sharedPrefix = uniqueName("e2e-search-fav");
    const favDoc = `${sharedPrefix}-fav`;
    const otherDoc = `${sharedPrefix}-other`;

    await test.step("Given two documents share a search prefix, one favourited", async () => {
      await clientDocuments.create({ name: favDoc });
      await clientDocuments.create({ name: otherDoc });
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(favDoc);
      await pageWorkspace.table.selectRecord(favDoc);
      await pageWorkspace.selectionBar.toggleFavorite();
      await pageWorkspace.table.deselectRecord(favDoc);
      await pageWorkspace.searchBar.clearSearch();
    });

    await test.step("When I search the shared prefix with the Favorites filter active", async () => {
      await pageWorkspace.toolbar.toggleFilter("favorites");
      await pageWorkspace.searchBar.search(sharedPrefix);
    });

    await test.step("Then only the favourited document is shown", async () => {
      await expect(pageWorkspace.table.row(favDoc)).toBeVisible();
      await expect(pageWorkspace.table.row(otherDoc)).toHaveCount(0);
    });
  });

  test(`As a sysadmin, a too-short search term is rejected with a validation message`, async ({
    browser,
    browserContextOptions,
  }) => {
    const ctx = await browser.newContext({
      ...browserContextOptions,
      storageState: storageStatePath(SYSADMIN.username),
    });
    try {
      const page = await ctx.newPage();
      const workspace = new WorkspacePage(page);

      await test.step("Given I am logged in as sysadmin", async () => {
        await workspace.open();
      });

      await test.step("When I search with a term shorter than 5 characters", async () => {
        await workspace.searchBar.search("abcd");
      });

      await test.step("Then a validation message tells me the term must be at least 5 characters", async () => {
        await expect(page.getByText(/at least 5 characters/i)).toBeVisible();
      });
    } finally {
      await ctx.close();
    }
  });
});
