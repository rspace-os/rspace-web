/**
 * Tag strings for `test.describe`/`test` titles.
 * Append to the title: `` `Login ${tags.SMOKE}` ``
 * Filter runs: `playwright test --grep @smoke` / `--grep-invert @smoke`
 * Playwright's native mechanism — no custom annotation classes needed.
 */
export const tags = {
  SMOKE: "@smoke",
  /** Third-party integration tests (Apps page toggles, external service calls).
   *  Run separately from smoke — not required on every PR. */
  APPS: "@apps",
};
