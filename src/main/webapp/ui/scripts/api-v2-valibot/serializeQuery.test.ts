import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { apiV2SerializeQuery } from "../../src/modules/common/api/v2/apiV2SerializeQuery";
import { filteredWhereOperations } from "./filters.ts";
import { type OpenApiDocument, orvalUrlName } from "./openapi.ts";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../../../../..");
const ui = resolve(root, "src/main/webapp/ui");
const document: OpenApiDocument = JSON.parse(
  readFileSync(resolve(ui, "src/modules/common/api/v2/openapi.generated.json"), "utf8"),
);
const operations = filteredWhereOperations(document);
const components = (document.components?.parameters ?? {}) as Record<string, { name?: string }>;
const declared = new Map(
  Object.values(document.paths ?? {}).flatMap((item) =>
    Object.values(item).flatMap((operation) => {
      const op = operation as { operationId?: string; parameters?: { $ref?: string; name?: string }[] };
      if (!op?.operationId || !operations.has(op.operationId)) return [];
      const names = (op.parameters ?? []).map((p) =>
        p.$ref ? components[p.$ref.split("/").at(-1) ?? ""]?.name : p.name,
      );
      return [[op.operationId, new Set(names)] as const];
    }),
  ),
);
const samples: Record<string, unknown>[] = [
  {},
  { where: "id==7" },
  { where: 'name=="a b&c=d"', page: 2, limit: 10, sort: "-name,id", totals: true, depth: 1, max: 5 },
  { fields: { users: "id,email", containers: "id" }, exclude: { users: "email" }, page: 1 },
  { where: undefined, page: null, totals: false, fields: { users: null, containers: undefined } },
];
let directory: string;
let defaults: Record<string, (params?: Record<string, unknown>) => string>;

// Orval's own query serialization, generated from the same spec without the paramsSerializer override,
// is the reference: apiV2SerializeQuery must produce identical URLs for everything except where objects.
beforeAll(async () => {
  const scratch = resolve(root, ".claude");
  mkdirSync(scratch, { recursive: true });
  directory = mkdtempSync(resolve(scratch, "params-parity-"));
  writeFileSync(
    resolve(directory, "orval.config.mjs"),
    `export default { parity: { input: ${JSON.stringify(resolve(ui, "src/modules/common/api/v2/openapi.generated.json"))}, output: { target: ${JSON.stringify(resolve(directory, "client.ts"))}, client: "fetch", mode: "single", override: { fetch: { includeHttpResponseReturnType: false, arrayFormat: "repeat" } } } } };`,
  );
  execFileSync(
    process.execPath,
    [resolve(root, "node_modules/orval/dist/bin/orval.mjs"), "--config", resolve(directory, "orval.config.mjs")],
    { stdio: "pipe" },
  );
  defaults = await import(/* @vite-ignore */ resolve(directory, "client.ts"));
}, 120_000);
afterAll(() => rmSync(directory, { recursive: true, force: true }));

describe("apiV2SerializeQuery", () => {
  it("matches Orval's default serialization for each filtered operation", () => {
    expect(operations.size).toBeGreaterThan(0);
    for (const operationId of operations.keys()) {
      const name = orvalUrlName(operationId);
      const reference = defaults[name];
      expect(reference, name).toBeTypeOf("function");
      for (const sample of samples) {
        // Only parameters the operation declares; undeclared ones are a type error at call sites.
        const params = Object.fromEntries(
          Object.entries(sample).filter(([key]) => declared.get(operationId)?.has(key)),
        );
        const url = reference(params);
        const query = apiV2SerializeQuery(params);
        expect(query ? `${url.split("?")[0]}?${query}` : url.split("?")[0], `${name}(${JSON.stringify(params)})`).toBe(
          url,
        );
      }
    }
  });

  it("serializes a where object as RSQL in the same position as a string", () => {
    expect(apiV2SerializeQuery({ where: { email: { contains: "a b" }, id: { in: [1, 2] } }, page: 1 })).toBe(
      apiV2SerializeQuery({ where: 'email=contains="a b";id=in=(1,2)', page: 1 }),
    );
    expect(apiV2SerializeQuery({ where: {}, page: 1 })).toBe("page=1");
  });
});
