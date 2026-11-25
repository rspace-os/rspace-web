import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";

// Run the repository's Biome with the current Node, so generation does not need pnpm on PATH.
const biome = createRequire(import.meta.url).resolve("@biomejs/biome/bin/biome");

/** Formats, lints and organises imports in generated files, in place. */
export function biomeWrite(paths: readonly string[]): void {
  execFileSync(process.execPath, [biome, "check", "--write", ...paths], { stdio: "inherit" });
}

/** Returns a generated source after the same `biome check --write`, treating it as `fileName`. */
export function biomeFormat(source: string, fileName: string): string {
  return execFileSync(process.execPath, [biome, "check", "--write", `--stdin-file-path=${fileName}`], {
    input: source,
    encoding: "utf8",
  });
}
