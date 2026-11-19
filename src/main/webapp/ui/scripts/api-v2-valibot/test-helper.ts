import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import type { OpenApiDocument } from "orval";
import { generate as adapt, type GeneratedSources } from "./generate.ts";
import { isReference, operationsOf, responsesOf } from "./openapi.ts";
import { assertSupportedSpec } from "./preflight.ts";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../../..");

/** Test documents are deliberately partial, and some deliberately invalid, so the scripts' runtime checks can reject them. */
export const fixture = (document: unknown): OpenApiDocument => document as OpenApiDocument;

export function generate(document: OpenApiDocument, options?: { modelImport?: string }): GeneratedSources {
  assertSupportedSpec(document);
  const scratch = resolve(root, "target/api-v2-scratch");
  mkdirSync(scratch, { recursive: true });
  const directory = mkdtempSync(resolve(scratch, "native-fragment-"));
  try {
    const spec: OpenApiDocument = Object.assign(
      { openapi: "3.1.0", info: { title: "fragment", version: "1" }, paths: {} },
      structuredClone(document),
    );
    for (const path of Object.values(spec.paths ?? {}))
      for (const [, op] of operationsOf(path)) {
        if (!op.operationId) continue;
        if (!Object.keys(op.responses ?? {}).length) op.responses = { 204: { description: "No content" } };
        for (const [, response] of responsesOf(op)) if (!isReference(response)) response.description ??= "Response";
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
