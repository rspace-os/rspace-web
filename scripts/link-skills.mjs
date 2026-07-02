// Symlinks `.claude/skills` -> `../.agents/skills` so Claude Code auto-discovers
// the repo-local agent skills (the cross-agent convention stores them in
// `.agents/skills/`). Run by the `postinstall` lifecycle script on `pnpm install`.
//
// `.claude/` is gitignored, so this link is per-checkout and never committed.
// Pure Node (no extra dependencies) so it behaves the same on macOS, Linux, and
// Windows. Exits 0 on any problem so the install never fails over a dev convenience.
import { mkdirSync, symlinkSync, lstatSync, readlinkSync } from "node:fs";

const link = ".claude/skills";
const target = "../.agents/skills"; // relative to .claude/

try {
  // Something already exists at `link` (lstat sees the path itself, not a
  // symlink's target; it throws when absent). Already our link → done, quietly.
  // Anything else — a real file/dir, or a symlink pointing elsewhere — is left
  // alone (never deleted; it may be the user's own) with a warning.
  const stat = lstatSync(link);
  const isSymlink = stat.isSymbolicLink();
  if (!(isSymlink && readlinkSync(link) === target)) {
    console.warn(`[postinstall] ${link} already exists; leaving it untouched.`);
    if (isSymlink) {
      // Stale symlink — safe to repoint. (Don't suggest this for a real
      // file/dir, where -f would clobber the user's data.)
      console.warn(`[postinstall] to repoint it: ln -snf ${target} ${link}`);
    } else {
      // Real file/dir — could be the user's own skills. Suggest moving them into
      // the shared dir, then removing the path so the link can be created. We
      // never delete it for them.
      console.warn(`[postinstall] if these are your own skills, move them into .agents/skills/`);
      console.warn(`[postinstall] then remove the path and re-link: rm -rf ${link} && ln -snf ${target} ${link}`);
    }
  }
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
