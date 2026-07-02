import { storageStatePath } from "./authState";
import { test as setup } from "./fixtures";
import { SYSADMIN, USERS } from "./users";

/**
 * `setup` project (see `playwright-e2e.config.ts`) — authenticates once per
 * account actually assigned to a browser project, plus sysadmin, and saves
 * each session as storageState under `playwright/.auth/`. Browser projects
 * depend on this project and start every test already authenticated, so no
 * spec drives the login form (or writes to the `User` row — see below) just
 * to establish a session.
 *
 * Login writes `User.lastLogin` on every call (`BaseLoginHelperImpl`), which
 * is version-checked by Hibernate. Multiple specs logging in as the *same*
 * seed user concurrently (e.g. a `beforeAll` re-authenticating the project's
 * `appUser` while another test's fixture does the same) can race that
 * optimistic lock and fail with `HibernateOptimisticLockingFailureException`.
 * Authenticating once here, up front, removes that race entirely.
 *
 * Add an entry only when a new project/account is introduced — don't
 * pre-harvest seed users nothing currently uses.
 */
const accounts = [USERS.user1a, USERS.user3c, USERS.user4d, SYSADMIN];

for (const account of accounts) {
  setup(`authenticate ${account.username}`, async ({ page, pageLogin }) => {
    await pageLogin.open();
    await pageLogin.login(account.username, account.password);
    await page.waitForURL((url) => !url.pathname.includes("/login"));
    await page.context().storageState({ path: storageStatePath(account.username) });
  });
}
