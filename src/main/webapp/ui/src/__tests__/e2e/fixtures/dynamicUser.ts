import { createDynamicUser } from "../createDynamicUser";
import { WorkspacePage } from "../pageObjects/workspace/WorkspacePage";
import { storageStateFor } from "../savedSessions";
import { DYNAMIC_USER_PASSWORD, uniqueName } from "../testData";
import { test } from "./flows";
import { loginInNewContext, performLogin } from "./flows/sessions/userSessions";

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
    const { username, apiKey } = await createDynamicUser(
      clientSysadmin,
      "ROLE_PI",
      "e2eDynUser",
      DYNAMIC_APP_USER_LAST_NAME,
    );
    await use({ username, password: DYNAMIC_USER_PASSWORD, apiKey, roles: ["ROLE_PI", "ROLE_USER"] });
  },
  // An HTTP login still runs first-login setup, which happens when /workspace is served.
  storageState: async ({ appUser }, use) => {
    await use(await storageStateFor(appUser));
  },

  flowCreateUser: async ({ clientSysadmin, browser, browserContextOptions }, use) => {
    const closers: Array<() => Promise<void>> = [];
    try {
      await use(async (role, namePrefix = "e2eDynUser2") => {
        const { username, apiKey } = await createDynamicUser(clientSysadmin, role, namePrefix);
        const { page, close } = await loginInNewContext(
          browser,
          browserContextOptions,
          username,
          DYNAMIC_USER_PASSWORD,
        );
        closers.push(close);
        return { username, apiKey, workspace: new WorkspacePage(page) };
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
