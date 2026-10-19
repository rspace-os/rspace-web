import type { Page } from "@playwright/test";
import { GroupRequestToastComponent } from "@/__tests__/e2e/components/groups/GroupRequestToastComponent";
import { NotificationsDialogComponent } from "@/__tests__/e2e/components/shared/NotificationsDialogComponent";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { loginInNewContext, test as userSessionTest } from "@/__tests__/e2e/fixtures/flows/sessions/userSessions";
import { CloudVerificationPage } from "@/__tests__/e2e/pageObjects/auth/CloudVerificationPage";
import { LoginPage } from "@/__tests__/e2e/pageObjects/auth/LoginPage";
import { SignupPage } from "@/__tests__/e2e/pageObjects/auth/SignupPage";
import { CreateCloudGroupPage } from "@/__tests__/e2e/pageObjects/groups/CreateCloudGroupPage";
import { DirectoryPage } from "@/__tests__/e2e/pageObjects/myrspace/DirectoryPage";
import { UserProfilePage } from "@/__tests__/e2e/pageObjects/myrspace/UserProfilePage";
import { GroupDetailsPage } from "@/__tests__/e2e/pageObjects/system/groups/GroupDetailsPage";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { DYNAMIC_USER_PASSWORD } from "@/__tests__/e2e/testData";

/** A logged-in community user in their own browser context. */
export type CloudUser = {
  username: string;
  fullName: string;
  email: string;
  page: Page;
  createGroup: CreateCloudGroupPage;
  profile: UserProfilePage;
  groupDetails: GroupDetailsPage;
  verification: CloudVerificationPage;
  workspace: WorkspacePage;
  notifications: NotificationsDialogComponent;
  groupRequest: (groupName: string) => GroupRequestToastComponent;
};

/** A logged-out browser context, for flows that start from an emailed link. */
export type CloudVisitor = {
  page: Page;
  signup: SignupPage;
  login: LoginPage;
  verification: CloudVerificationPage;
  directory: DirectoryPage;
  groupRequest: (groupName: string) => GroupRequestToastComponent;
};

type CloudSessionFixtures = {
  flowCloudUser: (namePrefix: string) => Promise<CloudUser>;
  flowCloudVisitor: () => Promise<CloudVisitor>;
};

export const test = userSessionTest.extend<CloudSessionFixtures>({
  flowCloudUser: async ({ browser, browserContextOptions, clientSysadmin }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async (namePrefix) => {
        const { username, fullName, email } = await createDynamicUser(clientSysadmin, "ROLE_USER", namePrefix);
        const { page, close } = await loginInNewContext(
          browser,
          browserContextOptions,
          username,
          DYNAMIC_USER_PASSWORD,
        );
        closers.push(close);
        return {
          username,
          fullName,
          email,
          page,
          createGroup: new CreateCloudGroupPage(page),
          profile: new UserProfilePage(page),
          groupDetails: new GroupDetailsPage(page),
          verification: new CloudVerificationPage(page),
          workspace: new WorkspacePage(page),
          notifications: new NotificationsDialogComponent(page),
          groupRequest: (groupName) => new GroupRequestToastComponent(page, groupName),
        };
      });
    } finally {
      await Promise.all(closers.map((close) => close()));
    }
  },

  flowCloudVisitor: async ({ browser, browserContextOptions }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async () => {
        // Manual contexts must clear the project's seed-user storage state.
        const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
        closers.push(() => ctx.close());
        const page = await ctx.newPage();
        return {
          page,
          signup: new SignupPage(page),
          login: new LoginPage(page),
          verification: new CloudVerificationPage(page),
          directory: new DirectoryPage(page),
          groupRequest: (groupName) => new GroupRequestToastComponent(page, groupName),
        };
      });
    } finally {
      await Promise.all(closers.map((close) => close()));
    }
  },
});
