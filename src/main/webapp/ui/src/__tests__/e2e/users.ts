import { env } from "./env";

export type Role = "ROLE_SYSADMIN" | "ROLE_PI" | "ROLE_USER" | "ROLE_ADMIN";

export type AppUser = {
  readonly username: string;
  readonly password: string;
  readonly apiKey: string;
  readonly roles: ReadonlyArray<Role>;
};

/** Password shared by all seed accounts after `drop-recreate-db`. */
export const DEFAULT_PASSWORD = "user1234";

const seed = (username: string, apiKey: string, roles: ReadonlyArray<Role>): AppUser => ({
  username,
  password: DEFAULT_PASSWORD,
  apiKey,
  roles,
});

/**
 * Seed users created by `drop-recreate-db`. Keyed by username so specs and
 * the playwright config can pick a distinct user per browser/worker to avoid
 * shared-state collisions when running cross-browser in parallel.
 *
 * All entries match `initial-seed-devtest.sql` exactly.
 * Keys are dev-test fixture data committed in SQL — not production secrets.
 */
export const USERS = {
  user1a: seed("user1a", "abcdefghijklmnop1", ["ROLE_PI", "ROLE_USER"]),
  user2b: seed("user2b", "abcdefghijklmnop3", ["ROLE_USER"]),
  user3c: seed("user3c", "abcdefghijklmnop7", ["ROLE_PI", "ROLE_USER"]),
  user4d: seed("user4d", "abcdefghijklmnop8", ["ROLE_PI", "ROLE_USER"]),
  user5e: seed("user5e", "abcdefghijklmnop9", ["ROLE_USER"]),
  user6f: seed("user6f", "abcdefghijklmnop10", ["ROLE_USER"]),
  user7g: seed("user7g", "abcdefghijklmnop4", ["ROLE_PI", "ROLE_USER"]),
  user8h: seed("user8h", "abcdefghijklmnop5", ["ROLE_USER"]),
  user9i: seed("user9i", "abcdefghijklmnop6", ["ROLE_USER"]),
  user10: seed("user10", "abcdefghijklmnop11", ["ROLE_USER"]),
} satisfies Record<string, AppUser>;

/**
 * Sysadmin account. Username and password are env-driven (non-seed environments
 * differ). API key falls back to the devtest seed value; override via
 * RSPACE_SYSADMIN_API_KEY for environments where the seed key differs.
 */
export const SYSADMIN: AppUser = {
  get username() {
    return env.sysadminUsername;
  },
  get password() {
    return env.sysadminPassword;
  },
  get apiKey() {
    return env.sysadminApiKey;
  },
  roles: ["ROLE_SYSADMIN"],
};
