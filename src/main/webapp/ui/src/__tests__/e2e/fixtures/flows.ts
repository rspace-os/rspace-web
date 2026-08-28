import type { Browser, BrowserContext, BrowserContextOptions, Page } from "@playwright/test";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { storageStatePath } from "@/__tests__/e2e/authState";
import { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";
import { GroupInvitationBanner } from "@/__tests__/e2e/components/system/groups/GroupInvitationBanner";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { env } from "@/__tests__/e2e/env";
import { LoginPage } from "@/__tests__/e2e/pageObjects/auth/LoginPage";
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
import { ProjectGroupPage } from "@/__tests__/e2e/pageObjects/system/groups/ProjectGroupPage";
import { SelfServiceLabGroupPage } from "@/__tests__/e2e/pageObjects/system/groups/SelfServiceLabGroupPage";
import { SystemConfigPage } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";
import { SystemUsersPage } from "@/__tests__/e2e/pageObjects/system/users/SystemUsersPage";
import { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";
import { apiTest } from "./api";

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

type FlowFixtures = {
  flowIgsnConfig: undefined;
  flowPidinstDataciteConfig: undefined;
  flowPidinstB2instConfig: undefined;
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

  flowSelfServicePi: () => Promise<SelfServicePiActor>;
  flowUserSession: (username: string, password: string) => Promise<UserSession>;
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

function mockOrReal<T>(mock: T, real: T): T {
  return env.integrationMode === "mock" ? mock : real;
}

async function configureDataciteProvider(
  clientSysadmin: SysadminClient,
  provider: "IGSN_DATACITE" | "PIDINST_DATACITE",
): Promise<void> {
  const account = {
    enabled: "true" as const,
    ...mockOrReal(
      {
        serverUrl: env.mockBackendBaseUrl,
        username: "mock-igsn-account",
        password: "mock-igsn-password",
        repositoryPrefix: "10.99999",
      },
      {
        serverUrl: env.igsnServerUrl,
        username: env.igsnAccountId,
        password: env.igsnPassword,
        repositoryPrefix: env.igsnRepoPrefix,
      },
    ),
  };

  if (provider === "IGSN_DATACITE") {
    await clientSysadmin.configureIgsn({ provider, ...account });
    if (!(await clientSysadmin.testIgsnConnection())) {
      throw new Error("IGSN provider connection test returned false after configuration");
    }
  } else {
    await clientSysadmin.configurePidinst({ provider, ...account });
    if (!(await clientSysadmin.testPidinstConnection())) {
      throw new Error("PIDINST DataCite provider connection test returned false after configuration");
    }
  }
}

export const test = apiTest.extend<FlowFixtures>({
  flowIgsnConfig: async ({ clientSysadmin }, use) => {
    await configureDataciteProvider(clientSysadmin, "IGSN_DATACITE");
    await use(undefined);
  },

  flowPidinstDataciteConfig: async ({ clientSysadmin }, use) => {
    await configureDataciteProvider(clientSysadmin, "PIDINST_DATACITE");
    await use(undefined);
  },

  flowPidinstB2instConfig: async ({ clientSysadmin }, use) => {
    await clientSysadmin.configurePidinst({
      provider: "PIDINST_B2INST",
      enabled: "true",
      ...mockOrReal(
        { serverUrl: env.mockBackendBaseUrl, username: "mock-b2inst-community", password: "mock-b2inst-token" },
        {
          serverUrl: env.pidinstB2instServerUrl,
          username: env.pidinstB2instCommunityId,
          password: env.pidinstB2instToken,
        },
      ),
      repositoryPrefix: "",
    });
    if (!(await clientSysadmin.testPidinstConnection())) {
      throw new Error("PIDINST B2INST connection test returned false after configuration");
    }
    await use(undefined);
  },
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
    const ctx = await browser.newContext({
      ...browserContextOptions,
      storageState: storageStatePath(SYSADMIN.username),
    });
    try {
      const page = await ctx.newPage();
      await use({
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
      });
    } finally {
      await ctx.close();
    }
  },

  flowSelfServicePi: async ({ browser, browserContextOptions, clientSysadmin, flowSysadminConfig }, use) => {
    const contexts: BrowserContext[] = [];
    try {
      await use(async () => {
        await flowSysadminConfig.ensureSetting("self_service_labgroups", "ALLOWED");
        await flowSysadminConfig.ensureSetting("allow_project_groups", "ALLOWED");

        const lastName = alphaNumericUnique("SelfServicePi");
        const { username } = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eSelfServicePi", lastName);

        const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
        contexts.push(ctx);
        const page = await ctx.newPage();
        const loginPage = new LoginPage(page);
        await loginPage.open();
        await loginPage.login(username, DYNAMIC_USER_PASSWORD);
        await page.waitForURL((url) => url.pathname === "/workspace");
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
      await Promise.all(contexts.map((ctx) => ctx.close()));
    }
  },
  flowUserSession: async ({ browser, browserContextOptions }, use) => {
    const contexts: BrowserContext[] = [];
    try {
      await use(async (username, password) => {
        const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
        contexts.push(ctx);
        const page = await ctx.newPage();
        const loginPage = new LoginPage(page);
        await loginPage.open();
        await loginPage.login(username, password);
        await page.waitForURL((url) => url.pathname === "/workspace");
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
      await Promise.all(contexts.map((ctx) => ctx.close()));
    }
  },
});
