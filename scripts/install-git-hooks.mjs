// Installs local git hooks via lefthook. Run automatically by the `prepare`
// lifecycle script on `pnpm install`.
//
// Hooks are only useful for an interactive developer working in a real git
// checkout, so this is skipped (exiting 0 so the install never fails) when:
//   * running in CI — hooks are not wanted on CI runners; or
//   * there is no accessible git repository — e.g. the Docker dev stack in
//     docker/dev/, where the container bind-mounts the worktree but not the
//     parent repo's gitdir, so a worktree's `.git` pointer cannot be resolved.
//
// Pure Node (no extra dependencies) so it behaves the same on macOS, Linux,
// and Windows.
import { execSync } from "node:child_process";

const isCI =
  process.env.CI != null ||
  process.env.CONTINUOUS_INTEGRATION != null ||
  process.env.GITHUB_ACTIONS != null ||
  process.env.JENKINS_URL != null ||
  process.env.BUILD_NUMBER != null;

if (isCI) {
  console.log("[prepare] CI detected — skipping lefthook git hook install.");
  process.exit(0);
}

try {
  execSync("git rev-parse --git-dir", { stdio: "ignore" });
} catch {
  console.log(
    "[prepare] No accessible git repository — skipping lefthook git hook install.",
  );
  process.exit(0);
}

try {
  execSync("lefthook install", { stdio: "inherit" });
} catch (err) {
  // Don't fail the install just because hooks could not be set up.
  console.warn(
    "[prepare] lefthook install failed; continuing without git hooks.",
  );
  console.warn(String(err && err.message ? err.message : err));
  process.exit(0);
}
