import { storageStatePath } from "./authState";
import { env } from "./env";
import { test as setup } from "./fixtures/flows";
import { selectBrowserProjects } from "./projects";
import { SYSADMIN, USERS } from "./users";

const selectedProjectAccounts = selectBrowserProjects(env.browser, { cloud: env.cloud }).map(({ appUser }) => appUser);
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
