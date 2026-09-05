import { expect } from "@playwright/test";
import type { PublicationKind } from "@/__tests__/e2e/components/myrspace/PublishDialogComponent";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { uniqueName } from "@/__tests__/e2e/testData";

async function publishRecord(
  pageWorkspace: WorkspacePage,
  name: string,
  kind: PublicationKind,
  summary: string,
  displayContactDetails: boolean,
): Promise<void> {
  await pageWorkspace.table.selectRecord(name);
  const dialog = await pageWorkspace.selectionBar.publish();
  await dialog.publish(kind, summary, displayContactDetails);
}

test.describe("publishing documents", () => {
  test.beforeEach(async ({ flowPublicSharing }) => {
    void flowPublicSharing;
  });

  test("As a user, an internet publication exposes metadata to an anonymous visitor", async ({
    appUser,
    clientDocuments,
    flowFreshPiPermissions,
    flowOpenAnonymousDocument,
    pageMyRSpace,
    pageWorkspace,
  }) => {
    await flowFreshPiPermissions("e2ePublishMember");

    const name = uniqueName("e2e-internet-publication");
    const summary = "Public metadata summary";
    await clientDocuments.create({ name });

    await pageWorkspace.open();
    await pageWorkspace.searchBar.search(name);
    await publishRecord(pageWorkspace, name, "internet", summary, true);

    await pageMyRSpace.open();
    const published = await pageMyRSpace.openPublishedDocuments();
    const publicPage = await flowOpenAnonymousDocument(await published.publicHref(name));
    await expect(publicPage.title(name)).toBeVisible();
    await expect(publicPage.summary(summary)).toBeVisible();
    await expect(publicPage.contact(`${appUser.username}@example.com`)).toBeVisible();
    expect(await publicPage.descriptionMeta()).toBe(summary);
    expect(await publicPage.robotsMeta()).toBe("noarchive");
  });

  test("As a user, a link-only publication is anonymous but excluded from search indexing", async ({
    appUser,
    clientDocuments,
    flowFreshPiPermissions,
    flowOpenAnonymousDocument,
    pageMyRSpace,
    pageWorkspace,
  }) => {
    await flowFreshPiPermissions("e2eLinkMember");

    const name = uniqueName("e2e-link-publication");
    const summary = "Link-only metadata summary";
    await clientDocuments.create({ name });

    await pageWorkspace.open();
    await pageWorkspace.searchBar.search(name);
    await publishRecord(pageWorkspace, name, "link", summary, false);

    await pageMyRSpace.open();
    const published = await pageMyRSpace.openPublishedDocuments();
    const publicPage = await flowOpenAnonymousDocument(await published.publicHref(name));
    await expect(publicPage.title(name)).toBeVisible();
    await expect(publicPage.summary(summary)).toBeVisible();
    await expect(publicPage.contact(`${appUser.username}@example.com`)).toHaveCount(0);
    expect(await publicPage.robotsMeta()).toBe("noindex, nofollow");

    await published.unpublish(name);
    await publicPage.reload();
    await expect(publicPage.title(name)).toHaveCount(0);
  });
});

test.describe("publishing notebooks", () => {
  test.beforeEach(async ({ flowPublicSharing }) => {
    void flowPublicSharing;
  });

  test("As a user, publishing and unpublishing a notebook exposes and then removes all its entries for an anonymous visitor", async ({
    flowFreshPiPermissions,
    flowOpenAnonymousDocument,
    pageMyRSpace,
    pageWorkspace,
  }) => {
    await flowFreshPiPermissions("e2eNotebookMember");

    const notebookName = uniqueName("e2e-publish-notebook");
    const summary = "Notebook publish summary";
    await pageWorkspace.open();
    const notebook = await pageWorkspace.createNotebook(notebookName);
    await notebook.addEntryNamed(uniqueName("e2e-notebook-entry"));

    await pageWorkspace.open();
    await publishRecord(pageWorkspace, notebookName, "internet", summary, false);

    await pageMyRSpace.open();
    const published = await pageMyRSpace.openPublishedDocuments();
    const publicPage = await flowOpenAnonymousDocument(await published.publicHref(notebookName));
    await expect(publicPage.title(notebookName)).toBeVisible();
    await expect(publicPage.summary(summary)).toBeVisible();
    await expect(publicPage.entryCounter(1, 1)).toBeVisible();

    await published.unpublish(notebookName);
    await publicPage.reload();
    await expect(publicPage.title(notebookName)).toHaveCount(0);
  });

  test("As a user, entries added to a notebook after publishing are visible to the public", async ({
    flowFreshPiPermissions,
    flowOpenAnonymousDocument,
    pageMyRSpace,
    pageWorkspace,
  }) => {
    await flowFreshPiPermissions("e2eNotebookFutureMember");

    const notebookName = uniqueName("e2e-future-entries-notebook");
    const firstEntryName = uniqueName("e2e-first-entry");
    await pageWorkspace.open();
    const notebook = await pageWorkspace.createNotebook(notebookName);
    await notebook.addEntryNamed(firstEntryName);

    await pageWorkspace.open();
    await publishRecord(pageWorkspace, notebookName, "internet", "Future entries summary", false);

    await pageMyRSpace.open();
    const published = await pageMyRSpace.openPublishedDocuments();
    const publicPage = await flowOpenAnonymousDocument(await published.publicHref(notebookName));
    await expect(publicPage.entryCounter(1, 1)).toBeVisible();

    await pageWorkspace.open();
    await pageWorkspace.table.openRecord(notebookName);
    const secondEntryName = uniqueName("e2e-future-entry");
    await notebook.addEntryNamed(secondEntryName);

    await publicPage.reload();
    await expect(publicPage.entryThumbnail(firstEntryName)).toBeVisible();
    await expect(publicPage.entryThumbnail(secondEntryName)).toBeVisible();
  });

  test("As a user, an entry explicitly published on its own remains public after its parent notebook is unpublished", async ({
    flowFreshPiPermissions,
    flowOpenAnonymousDocument,
    pageMyRSpace,
    pageWorkspace,
  }) => {
    await flowFreshPiPermissions("e2eNotebookEntryMember");

    const notebookName = uniqueName("e2e-entry-survives-notebook");
    const entryName = uniqueName("e2e-surviving-entry");
    await pageWorkspace.open();
    const notebook = await pageWorkspace.createNotebook(notebookName);
    await notebook.addEntryNamed(entryName);

    await pageWorkspace.open();
    await pageWorkspace.table.openRecord(notebookName);
    await publishRecord(pageWorkspace, entryName, "internet", "Entry summary", false);

    await pageMyRSpace.open();
    const publishedAfterEntry = await pageMyRSpace.openPublishedDocuments();
    const entryHref = await publishedAfterEntry.publicHref(entryName);

    await pageWorkspace.open();
    await publishRecord(pageWorkspace, notebookName, "internet", "Notebook summary", false);

    await pageMyRSpace.open();
    const publishedAfterNotebook = await pageMyRSpace.openPublishedDocuments();
    await expect(publishedAfterNotebook.row(notebookName)).toBeVisible();
    await publishedAfterNotebook.unpublish(notebookName);

    const entryPublicPage = await flowOpenAnonymousDocument(entryHref);
    await expect(entryPublicPage.title(entryName)).toBeVisible();
  });
});
