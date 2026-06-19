// Symlinks `.claude/skills` -> `../.agents/skills` so Claude Code auto-discovers
// the repo-local agent skills (the cross-agent convention stores them in
// `.agents/skills/`). Run by the `postinstall` lifecycle script on `pnpm install`.
//
// `.claude/` is gitignored, so this link is per-checkout and never committed.
// Pure Node (no extra dependencies) so it behaves the same on macOS, Linux, and
// Windows. Exits 0 on any problem so the install never fails over a dev convenience.
import { mkdirSync, symlinkSync, lstatSync, readlinkSync, rmSync } from "node:fs";

const link = ".claude/skills";
const target = "../.agents/skills"; // relative to .claude/

try {
  // Already correct? (lstat to inspect the link itself, not its target)
  try {
    if (lstatSync(link).isSymbolicLink() && readlinkSync(link) === target) {
      process.exit(0);
    }
    // Path exists but isn't our link — leave it alone rather than clobber.
    rmSync(link, { recursive: false });
  } catch {
    // Nothing at the path yet — fall through and create it.
  }

  mkdirSync(".claude", { recursive: true });
  // "junction" is ignored on POSIX and avoids needing admin rights on Windows.
  symlinkSync(target, link, "junction");
  console.log(`[postinstall] linked ${link} -> ${target}`);
} catch (err) {
  console.warn(`[postinstall] could not link ${link}; skipping.`);
  console.warn(String(err && err.message ? err.message : err));
  process.exit(0);
}
