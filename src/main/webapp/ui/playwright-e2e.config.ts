import { defineConfig, devices } from "@playwright/test";
import type { E2EOptions } from "./src/__tests__/e2e/fixtures";
import { INTEGRATION_MODE } from "./src/__tests__/e2e/integrationMode";
import { USERS } from "./src/__tests__/e2e/users";

// Full-stack integration e2e runner. Discovers only `*.e2e.ts`, so it
// never collects the `*.spec.{ts,tsx}` (browser-mode) or `*.test.*` (unit) files.
// `@/` resolves via tsconfig `paths`. Mock/real is the E2E_INTEGRATION_MODE env.
const BASE_URL = process.env.RSPACE_BASE_URL ?? "http://localhost:8080";
const PUBCHEM_MOCK_URL =
  "http://localhost:9099/rest/pug/compound/name/aspirin/property/Title,SMILES,MolecularFormula/json";

// Restrict to a single engine when set (CI shards by browser). Empty = all.
const ONLY_BROWSER = process.env.E2E_BROWSER;
const ALL_PROJECTS = [
  { name: "chromium", use: { ...devices["Desktop Chrome"], appUser: USERS.user1a } },
  { name: "firefox", use: { ...devices["Desktop Firefox"], appUser: USERS.user2b } },
  { name: "webkit", use: { ...devices["Desktop Safari"], appUser: USERS.user3c } },
];

export default defineConfig<E2EOptions>({
  testDir: "./src",
  testMatch: "**/*.e2e.ts",
  // Specs are describe.serial and mutate shared backend state.
  fullyParallel: false,
  // Match the GitHub Actions setup.
  workers: 2,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI
    ? [["github"], ["html", { open: "never" }], ["junit", { outputFile: "e2e-junit.xml" }]]
    : "list",
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  // Mock mode: start the fake PubChem (MSW handlers + Hono) the backend is
  // pointed at via -Dpubchem.base.url. Real mode hits live PubChem, no server.
  webServer:
    INTEGRATION_MODE === "mock"
      ? {
          command: "node src/__tests__/e2e/mockServer.mjs",
          url: PUBCHEM_MOCK_URL,
          reuseExistingServer: !process.env.CI,
          timeout: 30_000,
        }
      : undefined,
  // Distinct user per browser: with workers: 2 two projects run at once, and
  // integration state is per-user, so they must not share a login.
  // CI shards by browser via E2E_BROWSER so each job runs (and installs) only
  // its engine — passing --project through pnpm is unreliable (the `--` leaks
  // to Playwright as a file filter).
  projects: ALL_PROJECTS.filter((p) => !ONLY_BROWSER || p.name === ONLY_BROWSER),
});
