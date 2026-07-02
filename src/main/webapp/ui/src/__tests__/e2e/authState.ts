import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// `import.meta.url`, not `__dirname` — this module is loaded as ESM both from
// the top-level config (CJS-shimmed by Playwright's loader) and from plain
// test/spec files (true ESM, no `__dirname` shim), so it must work without it.
const currentDir = dirname(fileURLToPath(import.meta.url));

/**
 * Path of the saved Playwright storageState for a seed account, written by
 * `auth.setup.ts` (the `setup` project) and read by:
 *   - browser projects in `playwright-e2e.config.ts` (`use.storageState`)
 *   - `flowSysadminConfig` and any `beforeAll` that opens a secondary context
 *     via `browser.newContext()` for a specific account
 *
 * Lives under `playwright/.auth/` (gitignored — see root .gitignore). States
 * are re-harvested at the start of every run, so cross-run expiry is not a
 * concern.
 */
export function storageStatePath(username: string): string {
  return resolve(currentDir, "../../../playwright/.auth", `${username}.json`);
}
