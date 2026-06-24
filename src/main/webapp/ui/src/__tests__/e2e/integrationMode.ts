/**
 * Mock/real switch for the integration e2e suite (`*.e2e.ts`), resolved once.
 *
 *   E2E_INTEGRATION_MODE=mock (default) — backend points at the fake third-party
 *       server (see __tests__/e2e/mockServer.mjs); deterministic, PR-gating.
 *   E2E_INTEGRATION_MODE=real           — backend hits the live third party;
 *       allowed to fail.
 *
 * The switch decides how the app is booted and whether the mock server starts
 * (playwright-e2e.config.ts webServer); the specs themselves are mode-agnostic.
 */
export type IntegrationMode = "mock" | "real";

export const INTEGRATION_MODE: IntegrationMode = process.env.E2E_INTEGRATION_MODE === "real" ? "real" : "mock";
