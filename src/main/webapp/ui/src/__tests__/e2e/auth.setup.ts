import { storageStatePath } from "./authState";
import { env } from "./env";
import { test as setup } from "./fixtures/flows";
import { SYSADMIN, USERS } from "./users";

const projectAccounts = {
  chromium: USERS.user1a,
  firefox: USERS.user3c,
  webkit: USERS.user4d,
  mobile: USERS.user7g,
};
const selectedProjectAccounts = env.browser
  ? [projectAccounts[env.browser as keyof typeof projectAccounts]].filter((account) => account !== undefined)
  : Object.values(projectAccounts);
const accounts = [
  ...new Map([...selectedProjectAccounts, USERS.user3c, USERS.user6f, SYSADMIN].map((a) => [a.username, a])).values(),
];

for (const account of accounts) {
  setup(`authenticate ${account.username}`, async ({ page, pageLogin, pageWorkspace }) => {
    await pageLogin.open();
    await pageLogin.login(account.username, account.password);
    await page.waitForURL((url) => url.pathname === "/workspace");
    if (!(await pageWorkspace.isLoaded())) {
      throw new Error(`Workspace did not load after authenticating '${account.username}'.`);
    }
    await page.context().storageState({ path: storageStatePath(account.username) });
  });
}
