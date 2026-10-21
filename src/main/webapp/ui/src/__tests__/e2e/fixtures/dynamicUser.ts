import { createDynamicUser as createSharedDynamicUser } from "../createDynamicUser";
import { WorkspacePage } from "../pageObjects/workspace/WorkspacePage";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "../testData";
import { test } from "./flows";
import { performLogin, loginInNewContext as sessionLoginInNewContext } from "./flows/sessions/userSessions";

type CreatableRole = "ROLE_USER" | "ROLE_PI" | "ROLE_ADMIN";

type DynamicUserFixtures = {
  flowCreateUser: (
    role: CreatableRole,
    namePrefix?: string,
  ) => Promise<{ username: string; apiKey: string; workspace: WorkspacePage }>;
  flowFreshPiPermissions: (namePrefix?: string) => Promise<{ username: string; apiKey: string; groupName: string }>;
};

export const DYNAMIC_APP_USER_LAST_NAME = "DynamicUser";

export const dynamicUserTest = test.extend<DynamicUserFixtures>({
  appUser: async ({ clientSysadmin }, use) => {
    const { username, apiKey } = await createSharedDynamicUser(
      clientSysadmin,
      "ROLE_PI",
      "e2eDynUser",
      DYNAMIC_APP_USER_LAST_NAME,
    );
    await use({ username, password: DYNAMIC_USER_PASSWORD, apiKey, roles: ["ROLE_PI", "ROLE_USER"] });
  },
  storageState: async ({ appUser, browser, browserContextOptions }, use) => {
    const { context, page, close } = await sessionLoginInNewContext(
      browser,
      browserContextOptions,
      appUser.username,
      appUser.password,
    );
    try {
      const workspace = new WorkspacePage(page);
      if (!(await workspace.isLoaded())) {
        throw new Error(`Workspace did not load after authenticating dynamic user '${appUser.username}'.`);
      }
      await use(await context.storageState());
    } finally {
      await close();
    }
  },

  flowCreateUser: async ({ clientSysadmin, browser, browserContextOptions }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async (role, namePrefix = "e2eDynUser2") => {
        const user = await createSharedDynamicUser(clientSysadmin, role, namePrefix);
        const { page, close } = await sessionLoginInNewContext(
          browser,
          browserContextOptions,
          user.username,
          DYNAMIC_USER_PASSWORD,
        );
        closers.push(close);
        return { username: user.username, apiKey: user.apiKey, workspace: new WorkspacePage(page) };
      });
    } finally {
      const results = await Promise.allSettled(closers.map((close) => close()));
      for (const result of results) {
        if (result.status === "rejected") {
          console.error("Failed to close a flowCreateUser browser context during teardown:", result.reason);
        }
      }
    }
  },

  flowFreshPiPermissions: async ({ appUser, clientSysadmin, flowCreateUser, page, pageWorkspace }, use) => {
    await use(async (namePrefix = "e2ePublishMember") => {
      const member = await flowCreateUser("ROLE_USER", namePrefix);
      const groupName = uniqueName(`${namePrefix}-group`);
      await clientSysadmin.createGroup({
        displayName: groupName,
        type: "LAB_GROUP",
        users: [
          { username: appUser.username, roleInGroup: "PI" },
          { username: member.username, roleInGroup: "DEFAULT" },
        ],
      });
      await pageWorkspace.open();
      await pageWorkspace.header.logOut();
      await performLogin(page, appUser.username, appUser.password);
      return { ...member, groupName };
    });
  },
});
