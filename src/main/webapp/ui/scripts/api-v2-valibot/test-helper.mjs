import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { generate as adapt } from "./generate.mjs";
import { assertSupportedSpec } from "./preflight.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../../..");
export function generate(document, options) {
  assertSupportedSpec(document);
  const scratch = resolve(root, ".claude");
  mkdirSync(scratch, { recursive: true });
  const directory = mkdtempSync(resolve(scratch, "native-fragment-"));
  try {
    const spec = {
      openapi: "3.1.0",
      info: { title: "fragment", version: "1" },
      paths: {},
      ...structuredClone(document),
    };
    for (const path of Object.values(spec.paths))
      for (const op of Object.values(path)) {
        if (!op?.operationId) continue;
        if (!Object.keys(op.responses ?? {}).length) op.responses = { 204: { description: "No content" } };
        for (const response of Object.values(op.responses)) response.description ??= "Response";
      }
    writeFileSync(resolve(directory, "spec.json"), JSON.stringify(spec));
    writeFileSync(
      resolve(directory, "config.mjs"),
      `export default { validation: { input: ${JSON.stringify(resolve(directory, "spec.json"))}, output: { target: ${JSON.stringify(resolve(directory, "native.ts"))}, client: "valibot", mode: "single", override: { valibot: { generateReusableSchemas: true } } } } };`,
    );
    execFileSync(
      process.execPath,
      [resolve(root, "node_modules/orval/dist/bin/orval.mjs"), "--config", resolve(directory, "config.mjs")],
      { stdio: "pipe" },
    );
    const result = adapt(
      document,
      existsSync(resolve(directory, "native.ts"))
        ? readFileSync(resolve(directory, "native.ts"), "utf8")
        : 'import * as v from "valibot";\n',
      options,
    );
    // Inline only in the test fixture, so behavioural tests and type tests can load it.
    return {
      ...result,
      schemas:
        result.native +
        result.schemas.replace(/^import (?:type \* as v|\* as native).*$/gm, "").replaceAll("native.", ""),
    };
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}
