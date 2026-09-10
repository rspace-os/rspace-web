import { createDynamicUser } from "../createDynamicUser";
import { WorkspacePage } from "../pageObjects/workspace/WorkspacePage";
import { DYNAMIC_USER_PASSWORD } from "../testData";
import { test } from "./flows";
import { loginInNewContext } from "./flows/userSessions";

type CreatableRole = "ROLE_USER" | "ROLE_PI" | "ROLE_ADMIN";

type DynamicUserFixtures = {
  flowCreateUser: (
    role: CreatableRole,
    namePrefix?: string,
  ) => Promise<{ username: string; apiKey: string; workspace: WorkspacePage }>;
};

export const dynamicUserTest = test.extend<DynamicUserFixtures>({
  appUser: async ({ clientSysadmin }, use) => {
    const { username, apiKey } = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2eDynUser", "DynamicUser");
    await use({ username, password: DYNAMIC_USER_PASSWORD, apiKey, roles: ["ROLE_PI", "ROLE_USER"] });
  },
  storageState: async ({ appUser, browser, browserContextOptions }, use) => {
    const { context, page, close } = await loginInNewContext(
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
});
