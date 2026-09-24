import { existsSync } from "node:fs";
import { type Browser, type BrowserContextOptions, request } from "@playwright/test";
import { storageStatePath } from "./authState";
import { env } from "./env";
import { LoginPage } from "./pageObjects/auth/LoginPage";
import { type AppUser, SYSADMIN, USERS } from "./users";

type Credentials = Pick<AppUser, "username" | "password">;

/**
 * Returns the path of a seeded account's saved login, logging in again first if it has expired.
 * auth.setup.ts saves each login once per run, but web.xml expires sessions idle for 30 minutes,
 * which a long single-worker run exceeds for accounts it isn't currently using.
 */
export async function freshStorageState(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  account: Credentials,
): Promise<string> {
  const path = storageStatePath(account.username);
  if (await isLoggedIn(path)) return path;
  const context = await browser.newContext({ ...browserContextOptions, storageState: undefined });
  try {
    const page = await context.newPage();
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login(account.username, account.password);
    await page.waitForURL((url) => url.pathname === "/workspace");
    await context.storageState({ path });
  } finally {
    await context.close();
  }
  return path;
}

/** The seeded account whose saved login is stored at `path`, if any. */
export function savedLoginAccount(path: string): Credentials | undefined {
  return [...Object.values(USERS), SYSADMIN].find((account) => storageStatePath(account.username) === path);
}

async function isLoggedIn(path: string): Promise<boolean> {
  if (!existsSync(path)) return false;
  const api = await request.newContext({ baseURL: env.baseURL, storageState: path, ignoreHTTPSErrors: true });
  try {
    // An expired session is redirected to /login.
    return (await api.get("/workspace", { maxRedirects: 0 })).status() === 200;
  } finally {
    await api.dispose();
  }
}
