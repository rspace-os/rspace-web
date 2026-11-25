import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, it } from "vitest";
import { generateResources } from "./resources.ts";
import { fixture, generate } from "./test-helper.ts";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../../..");

function scratchDirectory(prefix: string): string {
  const scratch = resolve(root, "target/api-v2-scratch");
  mkdirSync(scratch, { recursive: true });
  return mkdtempSync(resolve(scratch, prefix));
}

/** Returns a function that type-checks the `.ts` files in `directory`, and what they import, and throws on errors. */
function compiler(directory: string): () => string {
  writeFileSync(
    resolve(directory, "tsconfig.json"),
    JSON.stringify({
      compilerOptions: {
        strict: true,
        noEmit: true,
        target: "ES2024",
        module: "ESNext",
        moduleResolution: "bundler",
        allowImportingTsExtensions: true,
        skipLibCheck: true,
      },
      include: ["*.ts"],
    }),
  );
  return () =>
    execFileSync(
      process.execPath,
      [resolve(root, "node_modules/typescript/bin/tsc"), "-p", resolve(directory, "tsconfig.json")],
      { encoding: "utf8", stdio: "pipe" },
    );
}

it("compiles two-way schema/model assertions and rejects drift", () => {
  const directory = scratchDirectory("valibot-types-");
  try {
    const document = fixture({
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
    });
    writeFileSync(resolve(directory, "schemas.ts"), generate(document, { modelImport: "./models" }).schemas);
    // Compare against the HTTP models Orval generates from the same document, not hand-written ones.
    writeFileSync(
      resolve(directory, "spec.json"),
      JSON.stringify(
        Object.assign({ openapi: "3.1.0", info: { title: "fragment", version: "1" }, paths: {} }, document),
      ),
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
    const compile = compiler(directory);
    expect(compile()).toBe("");
    const read = resolve(directory, "models/read.ts");
    writeFileSync(read, readFileSync(read, "utf8").replace("id: number", "id: string"));
    expect(compile).toThrow();
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}, 20000);

it("infers registry metadata as literals and rejects an operation that diverges from the shared shape", async () => {
  const directory = scratchDirectory("registry-types-");
  const shared = resolve(root, "src/main/webapp/ui/src/modules/common/api/v2");
  try {
    const document = fixture({
      components: {
        parameters: {
          ExamplesWhere: {
            in: "query",
            name: "where",
            "x-rspace-filter": { $ref: "#/components/x-rspace-filters/Instruments" },
          },
        },
        "x-rspace-filters": { Instruments: { selectors: {} } },
      },
      paths: {
        "/api/v2/examples": {
          get: {
            operationId: "example",
            tags: ["examples"],
            "x-rspace-operation": "LIST",
            parameters: [{ $ref: "#/components/parameters/ExamplesWhere" }],
          },
        },
        "/api/v2/examples/fields/{namespace}": {
          get: {
            operationId: "fields",
            tags: ["examples"],
            "x-rspace-operation": "RUNTIME_FIELDS",
            parameters: [{ in: "path", name: "namespace", schema: { type: "string", enum: ["a", "b"] } }],
          },
        },
      },
    });
    // Shaped like Orval's output for the fragment.
    const operations = (page: string) => `import type { ApiV2Where } from ${JSON.stringify(`${shared}/apiV2Where.ts`)};
type Params = { where?: ApiV2Where<"Instruments">; sort?: string; page?: ${page}; limit?: number; totals?: boolean; max?: number; depth?: number; fields?: { instruments?: string }; exclude?: { instruments?: string } };
type FieldParams = { q?: string; ids?: string; page?: number; limit?: number };
export const example = async (_params?: Params, _options?: RequestInit): Promise<{ id: number }> => ({ id: 1 });
export const getExampleQueryKey = (params?: Params) => ["/api/v2/examples", ...(params ? [params] : [])] as const;
export const fields = async (_namespace: "a" | "b", _params?: FieldParams, _options?: RequestInit): Promise<object> => ({});
export const getFieldsQueryKey = (namespace: "a" | "b", params?: FieldParams) => [\`/api/v2/examples/fields/\${namespace}\`, ...(params ? [params] : [])] as const;
`;
    mkdirSync(resolve(directory, "generated/examples"), { recursive: true });
    const target = resolve(directory, "generated/examples/examples.ts");
    writeFileSync(target, operations("number"));
    writeFileSync(
      resolve(directory, "generated/apiV2Resources.ts"),
      await generateResources(document, () => operations("number")),
    );
    writeFileSync(
      resolve(directory, "apiV2Resources.ts"),
      `export * from ${JSON.stringify(`${shared}/apiV2Resources.ts`)};\n`,
    );
    writeFileSync(
      resolve(directory, "check.ts"),
      `import type { apiV2Resources } from "./generated/apiV2Resources.ts";
type Equal<A, B> = (<T>() => T extends A ? 1 : 2) extends <T>() => T extends B ? 1 : 2 ? true : false;
type Assert<T extends true> = T;
export type Checks = [
  Assert<Equal<(typeof apiV2Resources)["examples"]["filterResource"], "Instruments">>,
  Assert<Equal<(typeof apiV2Resources)["examples"]["runtimeFieldNamespaces"], readonly ["a", "b"]>>,
];
`,
    );
    const compile = compiler(directory);
    expect(compile()).toBe("");
    writeFileSync(target, operations("string"));
    const errors = (() => {
      try {
        return compile();
      } catch (error) {
        return error instanceof Error && "stdout" in error ? String(error.stdout) : "";
      }
    })();
    expect(errors).toContain("generated/apiV2Resources.ts");
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}, 20000);
