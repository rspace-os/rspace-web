import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, it } from "vitest";
import type { OpenApiDocument } from "./openapi.ts";
import { generate } from "./test-helper.ts";

it("compiles two-way schema/model assertions and rejects drift", () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../../..");
  const scratch = resolve(root, ".claude");
  mkdirSync(scratch, { recursive: true });
  const directory = mkdtempSync(resolve(scratch, "valibot-types-"));
  try {
    const document: OpenApiDocument = {
      components: {
        schemas: {
          Read: {
            type: "object",
            properties: {
              id: { type: "integer" },
              names: {
                type: "array",
                readOnly: true,
                items: { type: "string" },
              },
              state: { enum: ["open", "closed"] },
            },
            required: ["id"],
          },
        },
      },
    };
    writeFileSync(resolve(directory, "schemas.ts"), generate(document, { modelImport: "./models" }).schemas);
    // Compare against the HTTP models Orval generates from the same document, not hand-written ones.
    writeFileSync(
      resolve(directory, "spec.json"),
      JSON.stringify({
        openapi: "3.1.0",
        info: { title: "fragment", version: "1" },
        paths: {},
        ...document,
      }),
    );
    writeFileSync(
      resolve(directory, "orval.config.mjs"),
      `export default { models: { input: ${JSON.stringify(resolve(directory, "spec.json"))}, output: { target: ${JSON.stringify(resolve(directory, "client/api.ts"))}, schemas: ${JSON.stringify(resolve(directory, "models"))}, client: "fetch", mode: "single" } } };`,
    );
    execFileSync(
      process.execPath,
      [resolve(root, "node_modules/orval/dist/bin/orval.mjs"), "--config", resolve(directory, "orval.config.mjs")],
      { stdio: "pipe" },
    );
    writeFileSync(
      resolve(directory, "tsconfig.json"),
      JSON.stringify({
        compilerOptions: {
          strict: true,
          noEmit: true,
          target: "ES2024",
          module: "ESNext",
          moduleResolution: "bundler",
          skipLibCheck: true,
        },
        include: ["*.ts"],
      }),
    );
    const compile = () =>
      execFileSync(
        process.execPath,
        [resolve(root, "node_modules/typescript/bin/tsc"), "-p", resolve(directory, "tsconfig.json")],
        { encoding: "utf8", stdio: "pipe" },
      );
    expect(compile()).toBe("");
    const read = resolve(directory, "models/read.ts");
    writeFileSync(read, readFileSync(read, "utf8").replace("id: number", "id: string"));
    expect(compile).toThrow();
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}, 20000);
