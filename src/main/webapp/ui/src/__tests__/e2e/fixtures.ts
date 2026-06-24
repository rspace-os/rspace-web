import { test as base, expect } from "@playwright/test";
import { type AppUser, USERS } from "./users";

export type { AppUser };
export type E2EOptions = { appUser: AppUser };

/**
 * Browser projects run concurrently (workers: 2) against ONE backend, and
 * integration enable/disable state is per-user — so each project logs in as a
 * different user (set per project in playwright-e2e.config.ts) to avoid racing
 * on it.
 */
export const test = base.extend<E2EOptions>({
  appUser: [USERS.user1a, { option: true }],
});

export { expect };
