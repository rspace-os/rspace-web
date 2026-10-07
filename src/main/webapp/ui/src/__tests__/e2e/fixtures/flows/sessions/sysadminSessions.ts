import type { Browser, BrowserContextOptions, Page } from "@playwright/test";
import { storageStatePath } from "@/__tests__/e2e/authState";
import { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";
import { env } from "@/__tests__/e2e/env";
import { apiTest } from "@/__tests__/e2e/fixtures/api";
import { GalleryPage } from "@/__tests__/e2e/pageObjects/gallery/GalleryPage";
import { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { AuditTrailPage } from "@/__tests__/e2e/pageObjects/myrspace/AuditTrailPage";
import { MyRSpacePage } from "@/__tests__/e2e/pageObjects/myrspace/MyRSpacePage";
import { UserProfilePage } from "@/__tests__/e2e/pageObjects/myrspace/UserProfilePage";
import { CreateAccountPage } from "@/__tests__/e2e/pageObjects/system/accounts/CreateAccountPage";
import { CommunitiesPage } from "@/__tests__/e2e/pageObjects/system/communities/CommunitiesPage";
import { DirectoryPage } from "@/__tests__/e2e/pageObjects/system/groups/DirectoryPage";
import { GroupAdminPage } from "@/__tests__/e2e/pageObjects/system/groups/GroupAdminPage";
import { GroupDetailsPage } from "@/__tests__/e2e/pageObjects/system/groups/GroupDetailsPage";
import { SystemConfigPage } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";
import { SystemUsersPage } from "@/__tests__/e2e/pageObjects/system/users/SystemUsersPage";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { SYSADMIN } from "@/__tests__/e2e/users";

type SysadminSessionFixtures = {
  flowSysadminConfig: SystemConfigPage;
  flowSysadminInventory: InventoryPage;
  flowSysadminGroupAdmin: {
    groupAdmin: GroupAdminPage;
    groupDetails: GroupDetailsPage;
    users: SystemUsersPage;
    workspace: WorkspacePage;
    communities: CommunitiesPage;
    createAccount: CreateAccountPage;
    directory: DirectoryPage;
    auditTrail: AuditTrailPage;
    toasts: ToastsComponent;
    profile: UserProfilePage;
    gallery: GalleryPage;
    myRSpace: MyRSpacePage;
  };
};

async function withSysadminPage<T>(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  open: (page: Page) => Promise<T>,
  use: (pageObject: T) => Promise<void>,
): Promise<void> {
  const ctx = await browser.newContext({
    ...browserContextOptions,
    storageState: storageStatePath(SYSADMIN.username),
  });
  try {
    const page = await ctx.newPage();
    await use(await open(page));
  } finally {
    await ctx.close();
  }
}

export const test = apiTest.extend<SysadminSessionFixtures>({
  flowSysadminConfig: async ({ browser, browserContextOptions }, use) =>
    withSysadminPage(
      browser,
      browserContextOptions,
      async (page) => {
        const configPage = new SystemConfigPage(page);
        await configPage.open();
        return configPage;
      },
      use,
    ),

  flowSysadminInventory: async ({ browser, browserContextOptions }, use) =>
    withSysadminPage(
      browser,
      browserContextOptions,
      async (page) => {
        const inventory = new InventoryPage(page);
        await inventory.open();
        return inventory;
      },
      use,
    ),

  flowSysadminGroupAdmin: async ({ browser, browserContextOptions }, use) => {
    env.assertGlobalMutationsAllowed("createLabGroup");
    await withSysadminPage(
      browser,
      browserContextOptions,
      async (page) => ({
        groupAdmin: new GroupAdminPage(page),
        groupDetails: new GroupDetailsPage(page),
        users: new SystemUsersPage(page),
        workspace: new WorkspacePage(page),
        communities: new CommunitiesPage(page),
        createAccount: new CreateAccountPage(page),
        directory: new DirectoryPage(page),
        auditTrail: new AuditTrailPage(page),
        toasts: new ToastsComponent(page),
        profile: new UserProfilePage(page),
        gallery: new GalleryPage(page),
        myRSpace: new MyRSpacePage(page),
      }),
      use,
    );
  },
});
