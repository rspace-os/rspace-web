import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe(`Workspace filters`, () => {
  test(`As a user, I can mark and unmark a document as favourite via the Favorites filter`, async ({
    pageWorkspace,
    clientDocuments,
  }) => {
    const docName = uniqueName("e2e-filter-fav-doc");

    await test.step("Given a document exists", async () => {
      await clientDocuments.create({ name: docName });
    });

    await test.step("Then the Favorites filter starts empty", async () => {
      await pageWorkspace.open();
      await pageWorkspace.toolbar.toggleFilter("favorites");
      await expect(pageWorkspace.table.dataRows).toHaveCount(0);
      await pageWorkspace.toolbar.toggleFilter("favorites");
    });

    await test.step("When I mark the document as favourite", async () => {
      await pageWorkspace.searchBar.search(docName);
      await pageWorkspace.table.selectRecord(docName);
      await pageWorkspace.selectionBar.toggleFavorite();
      await pageWorkspace.table.deselectRecord(docName);
    });

    await test.step("Then the Favorites filter shows exactly that document", async () => {
      await pageWorkspace.toolbar.toggleFilter("favorites");
      await expect(pageWorkspace.table.dataRows).toHaveCount(1);
      await expect(pageWorkspace.table.row(docName)).toBeVisible();
    });

    await test.step("When I unmark it as favourite", async () => {
      await pageWorkspace.table.selectRecord(docName);
      await pageWorkspace.selectionBar.toggleFavorite();
      await pageWorkspace.table.deselectRecord(docName);
    });

    await test.step("Then the Favorites filter is empty again", async () => {
      await pageWorkspace.toolbar.toggleFilter("favorites");
      await pageWorkspace.toolbar.toggleFilter("favorites");
      await expect(pageWorkspace.table.dataRows).toHaveCount(0);
    });
  });

  test(`As a user, the Templates filter shows a saved template, and combines with Favorites`, async ({
    pageWorkspace,
  }) => {
    const templateName = uniqueName("e2e-filter-template");

    await test.step("Given I create a document and save it as a template", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const viewPage = await editor.saveAndView();
      await viewPage.saveAsTemplate(templateName);
      await viewPage.close();
    });

    await test.step("Then the Templates filter shows the new template", async () => {
      if (!(await pageWorkspace.isLoaded())) throw new Error("Workspace did not load.");
      await pageWorkspace.toolbar.toggleFilter("templates");
      await expect(pageWorkspace.table.row(templateName)).toBeVisible();
    });

    await test.step("When I favourite the template while the Templates filter is active", async () => {
      await pageWorkspace.table.selectRecord(templateName);
      await pageWorkspace.selectionBar.toggleFavorite();
      await pageWorkspace.table.deselectRecord(templateName);
    });

    await test.step("Then combining Templates and Favorites still shows it", async () => {
      await pageWorkspace.toolbar.toggleFilter("favorites");
      await expect(pageWorkspace.table.row(templateName)).toBeVisible();
    });

    await test.step("And turning off the Templates filter (Favorites still active) still shows it", async () => {
      await pageWorkspace.toolbar.toggleFilter("templates");
      await expect(pageWorkspace.table.row(templateName)).toBeVisible();
    });
  });

  test.describe("Shared filter (needs a second user)", () => {
    test.describe.configure({ timeout: 120_000 });

    test(`As a user, the Shared filter shows a document shared with my group`, async ({
      pageWorkspace,
      pageLogin,
      clientDocuments,
      clientSysadmin,
      appUser,
      flowCreateUser,
    }) => {
      const groupName = uniqueName("e2e-filter-shared-group");
      const docName = uniqueName("e2e-filter-shared-doc");

      const recipient = await flowCreateUser("ROLE_USER");

      await test.step("Given a second user exists in a lab group with me, and I own a document", async () => {
        await clientSysadmin.createGroup({
          displayName: groupName,
          type: "LAB_GROUP",
          users: [
            { username: appUser.username, roleInGroup: "PI" },
            { username: recipient.username, roleInGroup: "DEFAULT" },
          ],
        });
        await clientDocuments.create({ name: docName });
        await pageWorkspace.open();
        await pageWorkspace.header.logOut();
        await pageLogin.login(appUser.username, appUser.password);
        await pageWorkspace.open();
        if (!(await pageWorkspace.isLoaded())) throw new Error("Workspace did not load.");
      });

      await test.step("When I share the document with the group", async () => {
        await pageWorkspace.searchBar.search(docName);
        await pageWorkspace.table.selectRecord(docName);
        const shareDialog = await pageWorkspace.selectionBar.share();
        await shareDialog.addRecipient(groupName);
        await shareDialog.save();
      });

      await test.step("Then the recipient sees it under the Shared filter", async () => {
        await recipient.workspace.open();
        await recipient.workspace.toolbar.toggleFilter("shared");
        await expect(recipient.workspace.table.row(docName)).toBeVisible();
      });
    });
  });
});
