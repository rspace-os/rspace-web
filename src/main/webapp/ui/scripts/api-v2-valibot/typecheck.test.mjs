import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, it } from "vitest";
import { generate } from "./test-helper.mjs";

it("compiles two-way schema/model assertions, including cycles, and rejects drift", () => {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../../..");
  const scratch = resolve(root, ".claude");
  mkdirSync(scratch, { recursive: true });
  const directory = mkdtempSync(resolve(scratch, "valibot-types-"));
  try {
    const result = generate(
      {
        components: {
          schemas: {
            Read: {
              type: "object",
              properties: {
                id: { type: "integer" },
                names: { type: "array", readOnly: true, items: { type: "string" } },
                state: { enum: ["open", "closed"] },
              },
              required: ["id"],
            },
            Node: {
              type: "object",
              properties: { next: { anyOf: [{ $ref: "#/components/schemas/Node" }, { type: "null" }] } },
            },
            Tree: {
              type: "object",
              properties: {
                branches: { type: "array", readOnly: true, items: { $ref: "#/components/schemas/Branch" } },
              },
            },
            Branch: { type: "object", properties: { tree: { $ref: "#/components/schemas/Tree" } } },
            Names: { type: "array", items: { type: "string" } },
            Nested: {
              type: "object",
              properties: {
                names: { $ref: "#/components/schemas/Names", readOnly: true },
                nullable: { type: ["array", "null"], readOnly: true, items: { type: "string" } },
                composed: { anyOf: [{ type: "array", items: { type: "integer" } }, { type: "null" }], readOnly: true },
                nested: {
                  type: "object",
                  properties: { names: { type: "array", readOnly: true, items: { type: "string" } } },
                },
              },
            },
            Dictionary: { type: "object", additionalProperties: { type: "string" } },
            Extended: {
              type: "object",
              properties: { id: { type: "string" } },
              required: ["id"],
              additionalProperties: true,
            },
          },
        },
      },
      { modelImport: "./models" },
    );
    writeFileSync(resolve(directory, "schemas.ts"), result.schemas);
    const models =
      'export interface Read { id: number; names?: readonly string[]; state?: "open" | "closed" }\nexport interface Node { next?: Node | null }\nexport interface Tree { branches?: readonly Branch[] }\nexport interface Branch { tree?: Tree }\nexport type Names = string[];\nexport interface Nested { names?: readonly string[]; nullable?: readonly string[] | null; composed?: readonly number[] | null; nested?: { names?: readonly string[] } }\nexport type Dictionary = Record<string, string>;\nexport interface Extended { id: string; [key: string]: unknown }\n';
    writeFileSync(resolve(directory, "models.ts"), models);
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
    writeFileSync(resolve(directory, "models.ts"), models.replace("id: number", "id: string"));
    expect(compile).toThrow();
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}, 20000);
