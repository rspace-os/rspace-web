import { createDynamicUser as createSharedDynamicUser } from "../createDynamicUser";
import { WorkspacePage } from "../pageObjects/workspace/WorkspacePage";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "../testData";
import { test } from "./flows";
import { loginInNewContext as sessionLoginInNewContext, performLogin } from "./flows/sessions/userSessions";

export type CreatableRole = "ROLE_USER" | "ROLE_PI" | "ROLE_ADMIN";

/** Compatibility helper for booking specs that need the created user's id and password. */
export async function createDynamicUser(
  clientSysadmin: Parameters<typeof createSharedDynamicUser>[0],
  role: CreatableRole,
  namePrefix: string,
): Promise<Awaited<ReturnType<typeof createSharedDynamicUser>> & { password: string }> {
  const user = await createSharedDynamicUser(clientSysadmin, role, namePrefix);
  return { ...user, password: DYNAMIC_USER_PASSWORD };
}

/** Compatibility login helper accepting the user object returned by createBookingDynamicUser. */
export async function loginInNewContext(
  browser: Parameters<typeof sessionLoginInNewContext>[0],
  browserContextOptions: Parameters<typeof sessionLoginInNewContext>[1],
  user: { username: string; password: string },
) {
  return sessionLoginInNewContext(browser, browserContextOptions, user.username, user.password);
}

type DynamicUserFixtures = {
  flowCreateUser: (
    role: CreatableRole,
    namePrefix?: string,
  ) => Promise<{ username: string; apiKey: string; workspace: WorkspacePage }>;
  flowFreshPiPermissions: (namePrefix?: string) => Promise<{ username: string; apiKey: string; groupName: string }>;
};

export const dynamicUserTest = test.extend<DynamicUserFixtures>({
  appUser: async ({ clientSysadmin }, use) => {
    const { username, apiKey } = await createSharedDynamicUser(clientSysadmin, "ROLE_PI", "e2eDynUser", "DynamicUser");
    await use({ username, password: DYNAMIC_USER_PASSWORD, apiKey, roles: ["ROLE_PI", "ROLE_USER"] });
  },
  storageState: async ({ appUser, browser, browserContextOptions }, use) => {
    // Manual contexts must set baseURL and clear the project's seed-user storage state.
    const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
    try {
      const page = await ctx.newPage();
      await performLogin(page, appUser.username, appUser.password);
      const workspace = new WorkspacePage(page);
      if (!(await workspace.isLoaded())) {
        throw new Error(`Workspace did not load after authenticating dynamic user '${appUser.username}'.`);
      }
      await use(await ctx.storageState());
    } finally {
      await ctx.close();
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
