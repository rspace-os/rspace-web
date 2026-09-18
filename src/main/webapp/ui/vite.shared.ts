import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type { Alias } from "vite";
import { normalizePath } from "vite";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const resolveFromRoot = (relativePath: string) => normalizePath(path.resolve(__dirname, relativePath));

export const sourceAlias: Alias = {
  find: /^@\//,
  replacement: `${resolveFromRoot("src")}/`,
};

export const tinymceDir = path.dirname(createRequire(import.meta.url).resolve("tinymce/package.json"));
const tinymceVersion = (
  JSON.parse(fs.readFileSync(path.join(tinymceDir, "package.json"), "utf8")) as {
    version: string;
  }
).version;

export const browserDefines = (mode: string, tinymceBase = "/ui/dist/tinymce/"): Record<string, string> => ({
  global: "globalThis",
  // Some chemistry dependencies bundle Node's `util` polyfill, which reads
  // bare `process.*` at module-evaluation time. The browser has no process.
  process: `{env:{NODE_ENV:${JSON.stringify(
    mode === "production" ? "production" : "development",
  )}},platform:"browser",browser:true,version:"",versions:{},argv:[],nextTick:(cb)=>Promise.resolve().then(cb),cwd:()=>"/",emitWarning:()=>{}}`,
  __TINYMCE_VERSION__: JSON.stringify(tinymceVersion),
  __TINYMCE_BASE__: JSON.stringify(tinymceBase),
});
