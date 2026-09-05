import { expect } from "@playwright/test";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { ExportImportPage } from "@/__tests__/e2e/pageObjects/myrspace/ExportImportPage";
import { listZipEntries } from "@/__tests__/e2e/zipArchive";

test.describe("My RSpace directory and export", () => {
  test("As a user, I can find another user in the directory and open their profile", async ({ pageMyRSpace }) => {
    await pageMyRSpace.open();
    const directory = await pageMyRSpace.openDirectory();
    const profile = await directory.openUserProfile("user1a");

    await expect(profile.username("user1a")).toBeVisible();
    await expect(profile.changePasswordLink).toHaveCount(0);
    expect(await profile.isProfileEditable()).toBe(false);
    expect(await profile.isApiKeyManagementVisible()).toBe(false);
  });

  test("As a user, I can export all my work as HTML", async ({
    page,
    pageMyRSpace,
    pageWorkspace,
    componentNotifications,
  }) => {
    const description = "My RSpace HTML export";
    await pageMyRSpace.open();
    const notificationCount = await componentNotifications.getBadgeCount();
    const exportPage = await pageMyRSpace.openExportImport();

    await expect.poll(() => page.title()).toBe(ExportImportPage.TITLE);
    expect(await exportPage.isExportAllButtonVisible()).toBe(true);

    const wizard = await exportPage.exportAll();

    await wizard.selectFormat("html");
    await expect(wizard.root.getByRole("checkbox", { name: "Include filestore links" })).not.toBeChecked();
    await wizard.next();
    await wizard.selectLinkedDocumentsDepth("none");
    await wizard.fillExportDescription(description);
    await wizard.submit();

    await expect
      .poll(() => componentNotifications.getBadgeCount(), { timeout: 60_000 })
      .toBeGreaterThan(notificationCount);
    await pageWorkspace.open();
    await componentNotifications.open();
    const notifications = await componentNotifications.getNotificationTexts();
    expect(notifications.some((text) => text.includes(`Your export [${description}] is completed`))).toBe(true);
    const downloadHref = await componentNotifications.getExportDownloadHref(description);
    await componentNotifications.close();

    const archiveResponse = await page.request.get(downloadHref);
    expect(archiveResponse.ok()).toBe(true);
    const entries = listZipEntries(await archiveResponse.body());
    expect(entries.some((entry) => entry.endsWith("/manifest.txt"))).toBe(true);
    expect(entries.some((entry) => entry.endsWith("/index.html"))).toBe(true);
    expect(entries.some((entry) => entry.endsWith(".html") && !entry.endsWith("/index.html"))).toBe(true);
  });
});
