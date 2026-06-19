// Symlinks `.claude/skills` -> `../.agents/skills` so Claude Code auto-discovers
// the repo-local agent skills (the cross-agent convention stores them in
// `.agents/skills/`). Run by the `postinstall` lifecycle script on `pnpm install`.
//
// `.claude/` is gitignored, so this link is per-checkout and never committed.
// Pure Node (no extra dependencies) so it behaves the same on macOS, Linux, and
// Windows. Exits 0 on any problem so the install never fails over a dev convenience.
import { mkdirSync, symlinkSync, lstatSync } from "node:fs";

const link = ".claude/skills";
const target = "../.agents/skills"; // relative to .claude/

try {
  // If anything already exists at `link` — a symlink or a real file/dir — leave
  // it alone (never delete; it may be the user's own link or skills) and warn.
  // lstat sees the path itself, not a symlink's target; it throws when absent.
  lstatSync(link);
  console.warn(`[postinstall] ${link} already exists; leaving it untouched.`);
  process.exit(0);
} catch {
  // Nothing there yet — fall through and create the link.
}

try {
  mkdirSync(".claude", { recursive: true });
  // "junction" is ignored on POSIX and avoids needing admin rights on Windows.
  symlinkSync(target, link, "junction");
  console.log(`[postinstall] linked ${link} -> ${target}`);
} catch (err) {
  console.warn(`[postinstall] could not link ${link}; skipping.`);
  console.warn(String(err && err.message ? err.message : err));
  process.exit(0);
}
