/**
 * Mock/real switch for the integration e2e suite, resolved once at startup.
 *
 *   E2E_INTEGRATION_MODE=mock (default) — backend points at a fake third-party
 *       server (see mockServer.mjs); deterministic, PR-gating.
 *   E2E_INTEGRATION_MODE=real           — backend hits the live third party;
 *       scheduled runs only, allowed to fail.
 */
export type IntegrationMode = "mock" | "real";

export const INTEGRATION_MODE: IntegrationMode = process.env.E2E_INTEGRATION_MODE === "real" ? "real" : "mock";
