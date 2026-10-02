import type { Browser, BrowserContext, BrowserContextOptions, Page } from "@playwright/test";
import { GroupInvitationBanner } from "@/__tests__/e2e/components/system/groups/GroupInvitationBanner";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test as sysadminSessionTest } from "@/__tests__/e2e/fixtures/flows/sessions/sysadminSessions";
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
import type { AppUser } from "@/__tests__/e2e/users";

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

export async function performLogin(page: Page, username: string, password: string): Promise<void> {
  const loginPage = new LoginPage(page);
  await loginPage.open();
  await loginPage.login(username, password);
  await page.waitForURL((url) => url.pathname === "/workspace");
}

export async function loginInNewContext(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  username: string,
  password: string,
): Promise<{ page: Page; context: BrowserContext; close: () => Promise<void> }> {
  const context = await browser.newContext({ ...browserContextOptions, storageState: undefined });
  try {
    const page = await context.newPage();
    await performLogin(page, username, password);
    return { page, context, close: () => context.close() };
  } catch (error) {
    try {
      await context.close();
    } catch (cleanupError) {
      console.error("Failed to close the browser context after authentication failed:", cleanupError);
    }
    throw error;
  }
}

/**
 * Logs the user out and back in so Shiro reloads permissions granted after an earlier login,
 * such as a group membership just created. Shiro caches authorization per principal, not per
 * session, and clears that cache only on logout; a new browser context alone can still see the
 * cached, pre-change permissions.
 */
export async function refreshOwnSessionAfterGroupChange(
  page: Page,
  pageWorkspace: WorkspacePage,
  appUser: Pick<AppUser, "username" | "password">,
): Promise<void> {
  await pageWorkspace.open();
  await pageWorkspace.header.logOut();
  await performLogin(page, appUser.username, appUser.password);
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
