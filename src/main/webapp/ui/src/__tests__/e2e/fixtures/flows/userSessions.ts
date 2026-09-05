import type { Browser, BrowserContextOptions, Page } from "@playwright/test";
import { GroupInvitationBanner } from "@/__tests__/e2e/components/system/groups/GroupInvitationBanner";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test as sysadminSessionTest } from "@/__tests__/e2e/fixtures/flows/sysadminSessions";
import { LoginPage } from "@/__tests__/e2e/pageObjects/auth/LoginPage";
import { MyRSpacePage } from "@/__tests__/e2e/pageObjects/myrspace/MyRSpacePage";
import { CreateAccountPage } from "@/__tests__/e2e/pageObjects/system/accounts/CreateAccountPage";
import { DirectoryPage } from "@/__tests__/e2e/pageObjects/system/groups/DirectoryPage";
import { GroupDetailsPage } from "@/__tests__/e2e/pageObjects/system/groups/GroupDetailsPage";
import { ProjectGroupPage } from "@/__tests__/e2e/pageObjects/system/groups/ProjectGroupPage";
import { SelfServiceLabGroupPage } from "@/__tests__/e2e/pageObjects/system/groups/SelfServiceLabGroupPage";
import { SystemUsersPage } from "@/__tests__/e2e/pageObjects/system/users/SystemUsersPage";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD } from "@/__tests__/e2e/testData";

export type SelfServicePiActor = {
  username: string;
  lastName: string;
  selfServiceLabGroup: SelfServiceLabGroupPage;
  projectGroup: ProjectGroupPage;
  groupDetails: GroupDetailsPage;
  workspace: WorkspacePage;
  directory: DirectoryPage;
};

export type UserSession = {
  groupDetails: GroupDetailsPage;
  groupInvitation: GroupInvitationBanner;
  workspace: WorkspacePage;
  users: SystemUsersPage;
  createAccount: CreateAccountPage;
  myRSpace: MyRSpacePage;
  directory: DirectoryPage;
};

type UserSessionFixtures = {
  flowSelfServicePi: () => Promise<SelfServicePiActor>;
  flowUserSession: (username: string, password: string) => Promise<UserSession>;
};

export async function loginInNewContext(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  username: string,
  password: string,
): Promise<{ page: Page; close: () => Promise<void> }> {
  const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
  const page = await ctx.newPage();
  const loginPage = new LoginPage(page);
  await loginPage.open();
  await loginPage.login(username, password);
  await page.waitForURL((url) => url.pathname === "/workspace");
  return { page, close: () => ctx.close() };
}

export const test = sysadminSessionTest.extend<UserSessionFixtures>({
  flowSelfServicePi: async ({ browser, browserContextOptions, clientSysadmin, flowSysadminConfig }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async () => {
        await flowSysadminConfig.ensureSetting("self_service_labgroups", "ALLOWED");
        await flowSysadminConfig.ensureSetting("allow_project_groups", "ALLOWED");

        const lastName = alphaNumericUnique("SelfServicePi");
        const { username } = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eSelfServicePi", lastName);

        const { page, close } = await loginInNewContext(
          browser,
          browserContextOptions,
          username,
          DYNAMIC_USER_PASSWORD,
        );
        closers.push(close);
        return {
          username,
          lastName,
          selfServiceLabGroup: new SelfServiceLabGroupPage(page),
          projectGroup: new ProjectGroupPage(page),
          groupDetails: new GroupDetailsPage(page),
          workspace: new WorkspacePage(page),
          directory: new DirectoryPage(page),
        };
      });
    } finally {
      await Promise.all(closers.map((close) => close()));
    }
  },
  flowUserSession: async ({ browser, browserContextOptions }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async (username, password) => {
        const { page, close } = await loginInNewContext(browser, browserContextOptions, username, password);
        closers.push(close);
        return {
          groupDetails: new GroupDetailsPage(page),
          groupInvitation: new GroupInvitationBanner(page),
          workspace: new WorkspacePage(page),
          users: new SystemUsersPage(page),
          createAccount: new CreateAccountPage(page),
          myRSpace: new MyRSpacePage(page),
          directory: new DirectoryPage(page),
        };
      });
    } finally {
      await Promise.all(closers.map((close) => close()));
    }
  },
});
