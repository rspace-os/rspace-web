import { env } from "./env";

export type AppUser = { username: string; password: string };

/** Password shared by all seed (`user1a`..`user8h`) accounts after `drop-recreate-db`. */
export const DEFAULT_PASSWORD = "user1234";

const seed = (username: string): AppUser => ({ username, password: DEFAULT_PASSWORD });

/**
 * Default logins created by `drop-recreate-db`. Keyed by username so specs and
 * the playwright config can pick a distinct user per browser/worker to avoid
 * shared-state collisions when running cross-browser in parallel.
 */
export const USERS = {
  user1a: seed("user1a"),
  user2b: seed("user2b"),
  user3c: seed("user3c"),
  user4d: seed("user4d"),
  user5e: seed("user5e"),
  user6f: seed("user6f"),
  user7g: seed("user7g"),
  user8h: seed("user8h"),
} satisfies Record<string, AppUser>;

/**
 * Sysadmin account. Credentials come from env vars — never hardcoded.
 * Set RSPACE_SYSADMIN_USERNAME (default: sysadmin1) and RSPACE_SYSADMIN_PASSWORD.
 */
export const SYSADMIN: AppUser = {
  get username() {
    return env.sysadminUsername;
  },
  get password() {
    return env.sysadminPassword;
  },
};
