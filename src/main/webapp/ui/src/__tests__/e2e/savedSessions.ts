import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { request } from "@playwright/test";
import { storageStatePath } from "./authState";
import { env } from "./env";
import { type AppUser, SYSADMIN, USERS } from "./users";

type Credentials = Pick<AppUser, "username" | "password">;
type SavedLogin = { cookies: unknown[]; origins: unknown[] };

/**
 * Returns the path of a seeded account's saved login, logging in again first if it has expired.
 * auth.setup.ts saves each login once per run, but web.xml expires sessions idle for 30 minutes,
 * which a long single-worker run exceeds for accounts it isn't currently using. Renewal uses HTTP
 * only, so API-only projects that install no browser can still depend on it.
 */
export async function freshStorageState(account: Credentials): Promise<string> {
  const path = storageStatePath(account.username);
  if (await isLoggedIn(path)) return path;
  const api = await request.newContext({ baseURL: env.baseURL, ignoreHTTPSErrors: true });
  try {
    const response = await api.post("/login", {
      form: { username: account.username, password: account.password },
    });
    if (new URL(response.url()).pathname !== "/workspace") {
      throw new Error(`Logging '${account.username}' in again landed on ${response.url()}, not /workspace.`);
    }
    const { cookies } = await api.storageState();
    const origins = existsSync(path) ? (JSON.parse(readFileSync(path, "utf8")) as SavedLogin).origins : [];
    writeFileSync(path, JSON.stringify({ cookies, origins } satisfies SavedLogin));
  } finally {
    await api.dispose();
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
