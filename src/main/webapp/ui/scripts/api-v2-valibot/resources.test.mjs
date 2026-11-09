import { execFileSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL, URL } from "node:url";
import { describe, expect, it } from "vitest";
import { apiV2Resources } from "../../src/modules/common/api/v2/apiV2Resources";

const apiRoot = resolve("src/modules/common/api/v2");
const document = JSON.parse(await readFile(resolve(apiRoot, "openapi.generated.json"), "utf8"));
const golden = JSON.parse(await readFile(resolve("scripts/api-v2-valibot/resources-golden.json"), "utf8"));
const generated = pathToFileURL(`${resolve(apiRoot, "generated")}/`);
const op = (kind, id = "example") => ({
  operationId: id,
  tags: ["examples"],
  ...(kind == null ? {} : { "x-rspace-operation": kind }),
});
const spec = (operation, path = "/api/v2/examples") => ({ paths: { [path]: { get: operation } } });

// Orval loads esbuild, which requires Node's typed-array realm rather than jsdom's.
function invokeMany(cases) {
  const script = `
    import { collectionOperations, generateResources } from ${JSON.stringify(pathToFileURL(resolve("scripts/api-v2-valibot/resources.mjs")).href)};
    import { readFile } from "node:fs/promises";
    import { readFileSync } from "node:fs";
    const cases = JSON.parse(readFileSync(0, "utf8"));
    const results = [];
    for (const { document, source, mode = "operations" } of cases) {
    try {
      const value = mode === "operations"
        ? Object.fromEntries([...collectionOperations(document)].map(([name, entries]) => [name, Object.fromEntries([...entries].map(([slot, op]) => [slot, op.id]))]))
        : await generateResources(document, (file) => {
            if (source === null) throw new Error("missing file");
            return source === undefined ? readFile(new URL(file, ${JSON.stringify(generated.href)}), "utf8") : source;
          });
      results.push({ value });
    } catch (error) { results.push({ error: error.message }); }
    }
    process.stdout.write(JSON.stringify(results));
  `;
  return JSON.parse(
    execFileSync(process.execPath, ["--input-type=module", "-e", script], {
      encoding: "utf8",
      input: JSON.stringify(cases),
    }),
  );
}

function invoke(document, mode = "operations", source) {
  return invokeMany([{ document, mode, source }])[0];
}

describe("collection resource registry emitter", () => {
  it("maps every supported kind to its registry slot", () => {
    const expected = {
      LIST: "list",
      COUNT: "count",
      READ: "get",
      CREATE: "create",
      UPDATE: "update",
      DELETE: "delete",
      BULK_CREATE: "createMany",
      BULK_UPDATE: "updateMany",
      BULK_DELETE: "deleteMany",
      ACCESS_READ: "access",
      ACCESS_REPLACE: "replaceAccess",
      ACCESS_LEAVE: "leaveAccess",
      ACCESS_GRANTEE_SEARCH: "grantees",
      AUDIT_LIST: "audit",
      RUNTIME_FIELDS: "runtimeFields",
    };
    const paths = Object.fromEntries(
      Object.keys(expected).map((kind) => [`/api/v2/examples/${kind}`, { get: op(kind, kind) }]),
    );
    expect(invoke({ paths }).value).toEqual({
      examples: Object.fromEntries(Object.entries(expected).map(([kind, slot]) => [slot, kind])),
    });
  });
  it("uses explicit runtime-field metadata and skips CUSTOM-only tags", () => {
    expect(invoke(spec(op("RUNTIME_FIELDS"))).value).toEqual({ examples: { runtimeFields: "example" } });
    expect(invoke(spec(op("CUSTOM"))).value).toEqual({});
    const resources = invoke(document).value;
    expect(resources.maintenances).not.toHaveProperty("audit");
    expect(Object.keys(resources)).toHaveLength(7);
  });
  it("groups operations by tag instead of guessing from the route", () => {
    expect(invoke(spec(op("LIST"), "/api/v2/different")).value).toEqual({ examples: { list: "example" } });
  });
  it("rejects unknown kinds and untagged routes", () => {
    const results = invokeMany([
      { document: spec(op("SURPRISE")) },
      { document: spec(op("constructor")) },
      { document: spec(op(null)) },
      { document: spec(op(null), "/api/v2/examples/fields/{namespace}") },
    ]);
    expect(results.map(({ error }) => error)).toEqual([
      expect.stringContaining("Unknown operation kind"),
      expect.stringContaining("Unknown operation kind"),
      expect.stringContaining("Untagged operation"),
      expect.stringContaining("Untagged operation"),
    ]);
  });
  it("rejects duplicate slots", () => {
    expect(invoke({ paths: { "/api/v2/examples": { get: op("LIST"), post: op("LIST", "other") } } }).error).toContain(
      "Duplicate slot",
    );
  });
  it("rejects missing request and query-key exports", () => {
    const results = invokeMany(
      ["", "export const example = () => {};", null].map((source) => ({
        document: spec(op("LIST")),
        mode: "generate",
        source,
      })),
    );
    expect(results.map(({ error }) => error)).toEqual([
      expect.stringContaining("Missing import target example"),
      expect.stringContaining("Missing import target getExampleQueryKey"),
      expect.stringContaining("missing file"),
    ]);
  });
  it("reproduces the committed generated registry", async () => {
    expect(invoke(document, "generate").value).toBe(await readFile(new URL("apiV2Resources.ts", generated), "utf8"));
  });
  it("preserves the golden slots and exact generated function identities", async () => {
    expect(Object.keys(apiV2Resources).sort()).toEqual(Object.keys(golden).sort());
    const modules = import.meta.glob("../../src/modules/common/api/v2/generated/*/*.ts");
    const loaded = await Promise.all(
      Object.entries(modules)
        .filter(([path]) => !path.includes("/models/") && !path.includes("/valibot/"))
        .map(([, load]) => load()),
    );
    const exports = Object.assign({}, ...loaded);
    for (const [resource, slots] of Object.entries(golden)) {
      const entry = apiV2Resources[resource];
      expect(entry.name).toBe(resource);
      expect(
        Object.keys(entry)
          .filter((key) => key !== "name")
          .sort(),
      ).toEqual(Object.keys(slots).sort());
      for (const [slot, id] of Object.entries(slots)) {
        const value = entry[slot];
        if (typeof value === "function") expect(value).toBe(exports[id]);
        else {
          expect(value.request).toBe(exports[id]);
          expect(value.queryKey).toBe(exports[`get${id[0].toUpperCase()}${id.slice(1)}QueryKey`]);
        }
      }
    }
  });
});
