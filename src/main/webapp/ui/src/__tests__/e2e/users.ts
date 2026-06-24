export type AppUser = { username: string; password: string };

/** Password shared by all seed (`user1a`..`user8h`) accounts. */
export const DEFAULT_PASSWORD = "user1234";

const seed = (username: string): AppUser => ({ username, password: DEFAULT_PASSWORD });

/**
 * Default logins created by `drop-recreate-db`. Keyed by username so specs and
 * the playwright config can pick a distinct user per browser/worker.
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

export const SYSADMIN: AppUser = { username: "sysadmin1", password: "sysWisc23!" };
