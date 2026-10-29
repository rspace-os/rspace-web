import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { type APIRequestContext, request } from "@playwright/test";
import { storageStatePath } from "./authState";
import { env } from "./env";
import { type AppUser, SYSADMIN, USERS } from "./users";

export type Credentials = Pick<AppUser, "username" | "password">;
type SavedLogin = { cookies: unknown[]; origins: unknown[] };
type StorageState = Awaited<ReturnType<APIRequestContext["storageState"]>>;

/**
 * Returns the path of a seeded account's saved login, logging in again first if it has expired.
 * auth.setup.ts saves each login once per run, but web.xml expires sessions idle for 30 minutes,
 * which a long single-worker run exceeds for accounts it isn't currently using. Renewal uses HTTP
 * only, so API-only projects that install no browser can still depend on it.
 */
export async function freshStorageState(account: Credentials): Promise<string> {
  const path = storageStatePath(account.username);
  if (await isLoggedIn(path)) return path;
  const { cookies } = await loginOverHttp(account);
  const origins = existsSync(path) ? (JSON.parse(readFileSync(path, "utf8")) as SavedLogin).origins : [];
  writeFileSync(path, JSON.stringify({ cookies, origins } satisfies SavedLogin));
  return path;
}

/**
 * A logged-in storage state for `account`: the saved file for a seeded account, or a login held in
 * memory for anyone else, so throwaway test users leave no files behind.
 */
export async function storageStateFor(account: Credentials): Promise<string | StorageState> {
  return savedLoginAccount(storageStatePath(account.username)) ? freshStorageState(account) : loginOverHttp(account);
}

async function loginOverHttp(account: Credentials): Promise<StorageState> {
  // Inside a test, Playwright fills any unset option from the project's context options, so without
  // an explicit empty storageState this login would carry the running test's session cookie.
  const api = await request.newContext({
    baseURL: env.baseURL,
    ignoreHTTPSErrors: true,
    storageState: { cookies: [], origins: [] },
  });
  try {
    const response = await api.post("/login", {
      form: { username: account.username, password: account.password },
    });
    if (new URL(response.url()).pathname !== "/workspace") {
      throw new Error(`Logging '${account.username}' in landed on ${response.url()}, not /workspace.`);
    }
    return await api.storageState();
  } finally {
    await api.dispose();
  }
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
