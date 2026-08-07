import { expect, request } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as base, createDynamicUser } from "@/__tests__/e2e/fixtures/dynamicUser";
import { GroupViewPage } from "@/__tests__/e2e/pageObjects/groups/GroupViewPage";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

const INTEGRATION_MODE = env.integrationMode;

const RAID_ALIAS = "mock";
const RAID_LABEL = "Marine Sensor Deployment Study (https://raid.org.au/10.12345/rd-001)";

type RaidFixtures = {
  raidGroup: { groupId: number; groupName: string };
  groupViewPage: GroupViewPage;
};

const test = base.extend<RaidFixtures>({
  groupViewPage: async ({ page, raidGroup }, use) => {
    await use(new GroupViewPage(page, raidGroup.groupId));
  },

  raidGroup: async ({ clientSysadmin, appUser }, use) => {
    const member = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eRaidMember");
    const groupName = alphaNumericUnique("e2eRaidGroup");
    const group = await clientSysadmin.createGroup({
      displayName: groupName,
      type: "PROJECT_GROUP",
      users: [
        { username: appUser.username, roleInGroup: "GROUP_OWNER" },
        { username: member.username, roleInGroup: "DEFAULT" },
      ],
    });

    try {
      await use({ groupId: group.id, groupName });
    } finally {
      const cleanupCtx = await request.newContext({ baseURL: env.baseURL });
      try {
        await cleanupCtx.post("/login", { form: { username: appUser.username, password: appUser.password } });
        await cleanupCtx.post(`/apps/raid/disassociate/${group.id}`);
      } catch (e) {
        console.error(`Failed to disassociate RAiD from group ${group.id} during teardown:`, e);
      } finally {
        await cleanupCtx.dispose();
      }
    }
  },
});

test.describe(`RAiD integration [${INTEGRATION_MODE}]`, { tag: tags.APPS }, () => {
  test.skip(INTEGRATION_MODE === "real", "real mode out of scope: RAiD's real OAuth client tbc");

  test.beforeEach(async ({ flowSysadminConfig }) => {
    await flowSysadminConfig.ensureSettings({
      "raid.available": "ALLOWED",
      "zenodo.available": "ALLOWED",
    });
  });

  test.beforeEach(async ({ pageApps }) => {
    await pageApps.setEnabledWithOAuthConnectMultiServer("RAiD", RAID_ALIAS);
  });

  test.describe("Export to a repository with Report to RAiD", () => {
    test.skip(INTEGRATION_MODE === "real", "mock-only: the export+Report-to-RAiD flow is not exercised in real mode");

    test.beforeEach(async ({ pageApps }) => {
      await pageApps.setEnabledWithApiKey("Zenodo", "mock-zenodo-token");
    });

    test("As a PI, exporting a document shared with my RAiD-associated group to Zenodo with Report to RAiD enabled also updates the RAiD record", async ({
      page,
      raidGroup,
      groupViewPage,
      pageWorkspace,
      clientDocuments,
      pageDocument,
      componentExportWizard,
      componentNotifications,
      componentToasts,
    }) => {
      test.setTimeout(120_000);

      const { raidConnections } = groupViewPage;

      await groupViewPage.open();
      await raidConnections.waitForLoaded();
      await raidConnections.addRaidIdentifier(RAID_LABEL);

      const docName = await test.step("Given I have a document shared with the RAiD-associated group", async () => {
        const doc = await clientDocuments.create({ name: alphaNumericUnique("RAiD export") });
        await pageWorkspace.open();
        await pageWorkspace.searchBar.search(doc.name);
        await pageWorkspace.table.selectRecord(doc.name);
        const shareDialog = await pageWorkspace.selectionBar.share();
        await shareDialog.addRecipient(raidGroup.groupName);
        await shareDialog.save();
        await page.goto(`/workspace/editor/structuredDocument/${doc.id}`);
        await pageDocument.isLoaded();
        return doc.name;
      });

      const baselineNotificationCount = await test.step("And I note the current notification count", () =>
        componentNotifications.getBadgeCount());

      await test.step("When I export it as a PDF to Zenodo with Report to RAiD enabled", async () => {
        await pageDocument.toolbar.actions.exportButton.click();
        await componentExportWizard.waitForOpen();
        await componentExportWizard.selectFormat("xml");
        await componentExportWizard.setExportToRepository(true);
        await componentExportWizard.next();

        await componentExportWizard.next();

        await componentExportWizard.setReportToRaid(true);
        await componentExportWizard.next();

        await componentExportWizard.selectRepository("Zenodo");
        await componentExportWizard.fillTitle(`Title for ${docName}`);
        await componentExportWizard.fillDescription(`Description for ${docName}`);
        await componentExportWizard.submit();
      });

      await test.step("Then the export is accepted for processing", async () => {
        await expect(
          componentToasts.byVariant("success", "Your export generation request has been submitted"),
        ).toBeVisible();
      });

      await test.step("And the notification count increases by 3 (export + deposit + RAiD update)", async () => {
        await expect
          .poll(() => componentNotifications.getBadgeCount(), { timeout: 90_000, intervals: [2_000] })
          .toBeGreaterThanOrEqual(baselineNotificationCount + 3);
      });

      await test.step("And the toolbar bell itself reflects the new count within its polling interval", async () => {
        const uiCount = await componentNotifications.waitForBadgeCountInUI(baselineNotificationCount + 3);
        expect(uiCount).toBeGreaterThanOrEqual(baselineNotificationCount + 3);
      });

      await test.step("And the notifications confirm the export, the Zenodo deposit, and the RAiD update", async () => {
        await componentNotifications.open();
        const notificationTexts = (await componentNotifications.getNotificationTexts()).join("\n");
        expect(notificationTexts).toContain("Your export is completed and generated an archive");
        expect(notificationTexts).toContain("Your deposit to repository Zenodo is complete.");
        expect(notificationTexts).toContain("has been added to your RAiD record");
        await componentNotifications.close();
      });
    });
  });
});
