import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { freshStorageState } from "@/__tests__/e2e/savedSessions";
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

  test(`As a user, I can bookmark a search, save it as my default and run it again`, async ({
    page,
    pageWorkspace,
    clientDocuments,
  }) => {
    const doc1 = uniqueName("e2e-search-bookmark-doc1");
    const doc2 = uniqueName("e2e-search-bookmark-doc2");
    let searchUrl = "";

    await test.step("Given two documents exist", async () => {
      await clientDocuments.create({ name: doc1 });
      await clientDocuments.create({ name: doc2 });
      await pageWorkspace.open();
    });

    await test.step("When I search Name=doc1 OR Name=doc2, the address bar holds the search", async () => {
      await pageWorkspace.searchBar.openAdvanced();
      const row0 = await pageWorkspace.searchBar.addRow();
      await pageWorkspace.searchBar.setRowType(row0, "Name");
      await pageWorkspace.searchBar.setRowValue(row0, doc1);
      const row1 = await pageWorkspace.searchBar.addRow();
      await pageWorkspace.searchBar.setRowType(row1, "Name");
      await pageWorkspace.searchBar.setRowValue(row1, doc2);
      await pageWorkspace.searchBar.setMatchMode("any");
      await pageWorkspace.searchBar.submitAdvanced();
      await expect(page).toHaveURL(/\/workspace\?options%5B%5D=name&options%5B%5D=name&.*operator=OR/);
      searchUrl = page.url();
    });

    await test.step("Then opening that URL shows the same results with the advanced panel filled in", async () => {
      await pageWorkspace.searchBar.openSearchUrl(searchUrl);
      await expect(pageWorkspace.table.row(doc1)).toBeVisible();
      await expect(pageWorkspace.table.row(doc2)).toBeVisible();
      await expect(page.getByRole("radio", { name: "Satisfy at least one condition" })).toBeChecked();
    });

    await test.step("When I save it as my default search", async () => {
      await pageWorkspace.searchBar.saveDefaultButton.click();
    });

    await test.step("Then the toolbar has a Default search button and the bar says it is the default", async () => {
      await expect(pageWorkspace.searchBar.defaultSearchButton).toBeVisible();
      await expect(pageWorkspace.searchBar.isDefaultMessage).toBeVisible();
      await pageWorkspace.searchBar.openSearchUrl(page.url());
      await expect(pageWorkspace.searchBar.defaultSearchButton).toBeVisible();
    });

    await test.step("When I clear the search, the address bar returns to /workspace", async () => {
      await pageWorkspace.searchBar.clearSearch();
      await expect(page).toHaveURL(/\/workspace$/);
    });

    await test.step("Then Default search shows the results again", async () => {
      await pageWorkspace.searchBar.runDefaultSearch();
      await expect(pageWorkspace.table.row(doc1)).toBeVisible();
      await expect(pageWorkspace.table.row(doc2)).toBeVisible();
      await expect(pageWorkspace.searchBar.isDefaultMessage).toBeVisible();
    });

    await test.step("And removing the default hides the button", async () => {
      await pageWorkspace.searchBar.removeDefaultSearch();
      await expect(pageWorkspace.searchBar.defaultSearchButton).toHaveCount(0);
      await pageWorkspace.open();
      await expect(pageWorkspace.searchBar.defaultSearchButton).toHaveCount(0);
    });
  });

  test(`As a user, a search with quotes survives returning from a document twice`, async ({
    page,
    pageWorkspace,
    pageDocument,
    clientDocuments,
  }) => {
    const docName = uniqueName('e2e "quoted"');

    await test.step("Given a document with a quoted word in its name exists", async () => {
      await clientDocuments.create({ name: docName });
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search('"quoted"');
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });

    for (const visit of ["first", "second"]) {
      await test.step(`Then after the ${visit} return from the document the search is still shown`, async () => {
        await pageWorkspace.table.openRecord(docName);
        await pageDocument.close();
        await expect(pageWorkspace.table.row(docName)).toBeVisible();
        await expect(pageWorkspace.searchBar.searchInput).toHaveValue('"quoted"');
        await expect(pageWorkspace.searchBar.clearSearchButton).toBeVisible();
        await expect(page).toHaveURL(/\/workspace\?options%5B%5D=global&terms%5B%5D=%22quoted%22&/);
      });
    }
  });

  test(`As a user, a search link naming an owner I cannot see keeps the search toolbar`, async ({
    page,
    pageWorkspace,
  }) => {
    await pageWorkspace.searchBar.openSearchUrl("/workspace?options%5B%5D=owner&terms%5B%5D=nosuchuser");
    await expect(page.getByRole("combobox", { name: "Select owner(s)" })).toBeVisible();
  });

  test(`As a user in tree view, a search link shows only its results and a failed one keeps tree view`, async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-search-tree-link");
    await clientDocuments.create({ name: docName });
    await pageWorkspace.open();
    await pageWorkspace.toolbar.switchLayout("tree");

    await pageWorkspace.searchBar.openSearchUrl(`/workspace?options%5B%5D=name&terms%5B%5D=${docName}`);
    await expect(pageWorkspace.table.row(docName)).toBeVisible();
    await expect(pageWorkspace.tree.root).toBeHidden();

    await pageWorkspace.open();
    await pageWorkspace.toolbar.switchLayout("tree");
    await pageWorkspace.searchBar.openSearchUrl("/workspace?options%5B%5D=global&terms%5B%5D=*abc");
    await pageWorkspace.open();
    await expect(pageWorkspace.tree.root).toBeVisible();
  });

  test(`As a user in tree view, I can run my default search`, async ({ pageWorkspace, clientDocuments }) => {
    const docName = uniqueName("e2e-search-tree-default");
    await clientDocuments.create({ name: docName });
    await pageWorkspace.searchBar.openSearchUrl(`/workspace?options%5B%5D=name&terms%5B%5D=${docName}`);
    await pageWorkspace.searchBar.saveDefaultButton.click();
    await expect(pageWorkspace.searchBar.isDefaultMessage).toBeVisible();
    await pageWorkspace.open();
    await pageWorkspace.toolbar.switchLayout("tree");

    await pageWorkspace.searchBar.runDefaultSearch();

    await expect(pageWorkspace.table.row(docName)).toBeVisible();
    await expect(pageWorkspace.tree.root).toBeHidden();
    await pageWorkspace.searchBar.removeDefaultSearch();
    await expect(pageWorkspace.searchBar.defaultSearchButton).toHaveCount(0);
  });

  test(`As a user in tree view, closing a document opened from a search link shows the search again`, async ({
    pageWorkspace,
    pageDocument,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-search-tree-return");
    await clientDocuments.create({ name: docName });
    await pageWorkspace.open();
    await pageWorkspace.toolbar.switchLayout("tree");

    await pageWorkspace.searchBar.openSearchUrl(`/workspace?options%5B%5D=name&terms%5B%5D=${docName}`);
    await pageWorkspace.table.openRecord(docName);
    await pageDocument.close();

    await expect(pageWorkspace.table.row(docName)).toBeVisible();
    await expect(pageWorkspace.searchBar.clearSearchButton).toBeVisible();
    await expect(pageWorkspace.tree.root).toBeHidden();
  });

  test(`As a user, switching to tree view during a search ends the search`, async ({
    page,
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-search-then-tree");
    await clientDocuments.create({ name: docName });
    await pageWorkspace.open();
    await pageWorkspace.searchBar.search(docName);

    await pageWorkspace.toolbar.switchLayout("tree");
    await expect(page).toHaveURL(/\/workspace$/);

    await pageWorkspace.toolbar.switchLayout("list");
    await expect(pageWorkspace.searchBar.clearSearchButton).toBeHidden();
    await expect(pageWorkspace.searchBar.searchInput).toHaveValue("");
  });

  test(`As a user, a search link that cannot be run says why and leaves the search box empty`, async ({
    page,
    pageWorkspace,
  }) => {
    await test.step("A link the page cannot read shows the generic message", async () => {
      await page.goto("/workspace?options%5B%5D=bogus&terms%5B%5D=leftover");
      await expect(page.getByText("This search link is not valid, so the folder is shown instead.")).toBeVisible();
      await expect(pageWorkspace.searchBar.searchInput).toHaveValue("");
    });

    await test.step("A link the server rejects shows the server's reason", async () => {
      await pageWorkspace.searchBar.openSearchUrl("/workspace?options%5B%5D=global&terms%5B%5D=*abc");
      await expect(page.getByText(/This search link could not be run: .*Invalid search input/)).toBeVisible();
      await expect(pageWorkspace.searchBar.searchInput).toHaveValue("");
      await expect(page).toHaveURL(/\/workspace$/);
    });

    await test.step("The server's reason is shown as text", async () => {
      await page.route("**/workspace/ajax/search**", (route) =>
        route.fulfill({
          status: 400,
          contentType: "text/html",
          body: '<html><body><span id="ajaxErrorMsg">bad &lt;b&gt;term&lt;/b&gt;</span></body></html>',
        }),
      );
      await pageWorkspace.searchBar.openSearchUrl("/workspace?options%5B%5D=global&terms%5B%5D=leftover");
      await expect(page.getByText("This search link could not be run: bad <b>term</b>")).toBeVisible();
      await page.unroute("**/workspace/ajax/search**");
    });

    await test.step("A dropped connection says so and keeps the link", async () => {
      await page.route("**/workspace/ajax/search**", (route) => route.abort());
      await page.goto("/workspace?options%5B%5D=global&terms%5B%5D=leftover");
      await expect(page.getByText("Could not run the search. Check your connection and reload.")).toBeVisible();
      await expect(pageWorkspace.searchBar.searchInput).toHaveValue("");
      await expect(page).toHaveURL(/terms%5B%5D=leftover/);
    });

    await test.step("After a dropped connection, sorting lists the folder again", async () => {
      await page.unroute("**/workspace/ajax/search**");
      const folderListing = page.waitForResponse((response) => response.url().includes("/workspace/ajax/view/"));
      await pageWorkspace.table.sortBy("Name");
      expect((await folderListing).status()).toBe(200);
      await expect(pageWorkspace.table.row("Examples")).toBeVisible();
    });
  });

  test(`As a user, a folder opened while a search link is loading is not replaced by its results`, async ({
    page,
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-search-slow-link");
    await clientDocuments.create({ name: docName });
    let releaseSearch: () => void = () => {};
    const searchHeld = new Promise<void>((resolve) => {
      releaseSearch = resolve;
    });
    await page.route("**/workspace/ajax/search**", async (route) => {
      await searchHeld;
      await route.continue();
    });

    await page.goto(`/workspace?options%5B%5D=name&terms%5B%5D=${docName}`);
    // jQuery may run its ready handlers just after the load event; the jQuery blockUI
    // overlay has no role or label, so wait for it by its class.
    await page.locator("#record_list_frame > .blockOverlay").waitFor();
    await expect(
      pageWorkspace.table.row("Examples").getByRole("link", { name: "Examples", exact: true }).click({ timeout: 1000 }),
    ).rejects.toThrow();

    // The overlay stops the mouse but not the keyboard
    await pageWorkspace.table.row("Examples").getByRole("link", { name: "Examples", exact: true }).focus();
    await page.keyboard.press("Enter");
    await pageWorkspace.waitUntilBreadcrumbShows("Examples");

    const searchResponse = page.waitForResponse((response) => response.url().includes("/workspace/ajax/search"));
    releaseSearch();
    await searchResponse;
    // Past the fade before a listing is drawn
    await page.waitForTimeout(1000);

    await expect(pageWorkspace.breadcrumbFolderName).toHaveText(/Examples/);
    await expect(pageWorkspace.table.row(docName)).toHaveCount(0);
    // The new user's Examples folder is empty, so there is no column to sort by
    expect(
      await page.evaluate(() => (window as unknown as { workspaceSettings: { url: string } }).workspaceSettings.url),
    ).toContain("/workspace/ajax/view/");
  });

  test(`As a user, a folder that fails to open while a search link is loading leaves the listing usable`, async ({
    page,
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-search-link-failed-folder");
    await clientDocuments.create({ name: docName });
    let releaseSearch: () => void = () => {};
    const searchHeld = new Promise<void>((resolve) => {
      releaseSearch = resolve;
    });
    let searchSent: () => void = () => {};
    const searchStarted = new Promise<void>((resolve) => {
      searchSent = resolve;
    });
    await page.route("**/workspace/ajax/search**", async (route) => {
      searchSent();
      await searchHeld;
      await route.continue();
    });

    await page.goto(`/workspace?options%5B%5D=name&terms%5B%5D=${docName}`);
    // The toolbar sends the search once the page, including its folder links, is set up
    await searchStarted;
    // The jQuery blockUI overlay has no role or label, so it is found by its class
    const overlay = page.locator("#record_list_frame > .blockOverlay");
    await overlay.waitFor();
    await page.route("**/workspace/ajax/view/**", (route) => route.fulfill({ status: 500 }));
    const folderFailed = page.waitForResponse((response) => response.url().includes("/workspace/ajax/view/"));
    await pageWorkspace.table.row("Examples").getByRole("link", { name: "Examples", exact: true }).focus();
    await page.keyboard.press("Enter");
    await folderFailed;

    const searchResponse = page.waitForResponse((response) => response.url().includes("/workspace/ajax/search"));
    releaseSearch();
    await searchResponse;

    await expect(overlay).toHaveCount(0);
    await expect(pageWorkspace.table.row("Examples")).toBeVisible();
  });

  test(`As a user, the folder listing stays covered until a slow search link's results are shown`, async ({
    page,
    pageWorkspace,
    clientDocuments,
  }) => {
    test.setTimeout(60_000);
    const docName = uniqueName("e2e-search-slower-link");
    await clientDocuments.create({ name: docName });
    await page.route("**/workspace/ajax/search**", async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 17_000));
      await route.continue();
    });

    await page.goto(`/workspace?options%5B%5D=name&terms%5B%5D=${docName}`);
    // The jQuery blockUI overlay has no role or label, so it is found by its class
    const overlay = page.locator("#record_list_frame > .blockOverlay");
    await overlay.waitFor();
    await page.waitForTimeout(16_000);
    await expect(overlay).toBeVisible();
    await expect(page.getByText("Loading records")).toHaveCount(1);

    await expect(pageWorkspace.table.row(docName)).toBeVisible({ timeout: 10_000 });
    await expect(overlay).toHaveCount(0);
  });

  test(`As a user, Back from a search result shows the search again`, async ({
    page,
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-search-back");
    await clientDocuments.create({ name: docName });
    await pageWorkspace.open();
    await pageWorkspace.searchBar.search(docName);
    await pageWorkspace.table.openRecord(docName);

    const searches: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname.endsWith("/workspace/ajax/search")) searches.push(request.url());
    });
    await page.goBack();

    await expect(pageWorkspace.table.row(docName)).toBeVisible();
    await expect(pageWorkspace.searchBar.clearSearchButton).toBeVisible();
    await expect(pageWorkspace.searchBar.searchInput).toHaveValue(docName);
    expect(searches.length).toBeLessThanOrEqual(1);
  });

  test(`As a sysadmin, a too-short search term is rejected with a validation message`, async ({
    browser,
    browserContextOptions,
  }) => {
    const ctx = await browser.newContext({
      ...browserContextOptions,
      storageState: await freshStorageState(SYSADMIN),
    });
    try {
      const page = await ctx.newPage();
      const workspace = new WorkspacePage(page);

      await test.step("Given I am logged in as sysadmin", async () => {
        await workspace.open();
      });

      await workspace.searchBar.searchExpectingValidationError("abcd", "at least 5 characters");
    } finally {
      await ctx.close();
    }
  });
});
