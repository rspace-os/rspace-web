import { expect } from "@playwright/test";
import { SnippetsClient } from "@/__tests__/e2e/api/clients/SnippetsClient";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { LoginPage } from "@/__tests__/e2e/pageObjects/auth/LoginPage";
import { GalleryPage } from "@/__tests__/e2e/pageObjects/gallery/GalleryPage";
import { alphaNumericUnique, TINY_PNG, uniqueName } from "@/__tests__/e2e/testData";

const CREATED_USER_PASSWORD = "Passw0rd!23";

test.describe("Gallery snippet sharing", () => {
  test("Share is disabled for non-snippet gallery items such as images", async ({ pageGallery, clientFiles }) => {
    const name = `${uniqueName("e2e-snippet-share-disabled")}.png`;

    await test.step("Given an uploaded image", async () => {
      const uploaded = await clientFiles.uploadFile({ name, mimeType: "image/png", buffer: TINY_PNG });
      await pageGallery.open(uploaded.parentFolderId);
      await pageGallery.isLoaded();
    });

    await test.step("When I select it and open Actions", async () => {
      await pageGallery.selectFile(name);
      await pageGallery.actions.open();
    });

    await test.step("Then Share is disabled", async () => {
      expect(await pageGallery.actions.isActionEnabled("Share")).toBe(false);
    });
  });

  test("A PI can share a Gallery snippet with a lab group; a member sees it in their Snippets section", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
  }) => {
    const piUsername = alphaNumericUnique("e2eSnippetPi");
    const memberUsername = alphaNumericUnique("e2eSnippetMember");
    const groupName = uniqueName("e2e-snippet-group");
    const snippetName = uniqueName("e2e-snippet");

    await test.step("Given a PI and a member in the same lab group", async () => {
      await clientSysadmin.createUser({
        username: piUsername,
        password: CREATED_USER_PASSWORD,
        email: `${piUsername}@example.com`,
        firstName: "E2E",
        lastName: "SnippetPi",
        role: "ROLE_PI",
      });
      await clientSysadmin.createUser({
        username: memberUsername,
        password: CREATED_USER_PASSWORD,
        email: `${memberUsername}@example.com`,
        firstName: "E2E",
        lastName: "SnippetMember",
        role: "ROLE_USER",
      });
      await clientSysadmin.createGroup({
        displayName: groupName,
        type: "LAB_GROUP",
        users: [
          { username: piUsername, roleInGroup: "PI" },
          { username: memberUsername, roleInGroup: "DEFAULT" },
        ],
      });
    });

    const piContext = await browser.newContext({ ...browserContextOptions, storageState: undefined });
    const memberContext = await browser.newContext({ ...browserContextOptions, storageState: undefined });
    try {
      const piPage = await piContext.newPage();
      const piLogin = new LoginPage(piPage);
      await piLogin.open();
      await piLogin.login(piUsername, CREATED_USER_PASSWORD);
      await piPage.waitForURL((url) => url.pathname === "/workspace");

      await test.step("When the PI creates a snippet via the API", async () => {
        await new SnippetsClient(piPage).createFromContent({
          name: snippetName,
          content: "<p>snippet content</p>",
        });
      });

      const piGallery = new GalleryPage(piPage);
      await test.step("And the PI shares the snippet with the group", async () => {
        await piGallery.openInSection("Snippets");
        await piGallery.selectFile(snippetName);
        await piGallery.actions.open();
        await piGallery.actions.clickAction("Share");
        await piGallery.shareDialog.waitUntilVisible();
        await piGallery.shareDialog.addRecipient(groupName);
        await piGallery.shareDialog.save();
      });

      const memberPage = await memberContext.newPage();
      const memberLogin = new LoginPage(memberPage);
      await memberLogin.open();
      await memberLogin.login(memberUsername, CREATED_USER_PASSWORD);
      await memberPage.waitForURL((url) => url.pathname === "/workspace");

      const memberGallery = new GalleryPage(memberPage);
      await test.step("Then the member sees the shared snippet in their Snippets section", async () => {
        await memberGallery.openInSection("Snippets");
        await memberGallery.openFolder("SNIPPETS_Shared");
        await memberGallery.openFolder("SNIPPETS_LabGroups");
        await memberGallery.openFolder(`${groupName}_SHARED_SNIPPETS`);
        await expect(memberGallery.fileCell(snippetName)).toBeVisible();
      });
    } finally {
      await piContext.close();
      await memberContext.close();
    }
  });
});
