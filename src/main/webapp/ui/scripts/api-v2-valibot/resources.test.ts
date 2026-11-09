import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import type { OpenApiOperationObject } from "orval";
import { describe, expect, it } from "vitest";

type Operations = Record<string, Record<string, string>>;
interface Case {
  document: unknown;
  source?: string | null;
  mode?: "operations" | "generate";
}
interface Result<T> {
  value: T;
  error?: string;
}

const generated = pathToFileURL(`${resolve("src/modules/common/api/v2/generated")}/`);
const op = (kind: string | null, id = "example"): OpenApiOperationObject => ({
  operationId: id,
  tags: ["examples"],
  ...(kind == null ? {} : { "x-rspace-operation": kind }),
});
const spec = (operation: OpenApiOperationObject, path = "/api/v2/examples") => ({
  paths: { [path]: { get: operation } },
});

// Orval loads esbuild, which requires Node's typed-array realm rather than jsdom's.
function invokeMany<T = Operations>(cases: Case[]): Result<T>[] {
  const script = `
    import { collectionOperations, generateResources } from ${JSON.stringify(pathToFileURL(resolve("scripts/api-v2-valibot/resources.ts")).href)};
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

function invoke<T = Operations>(document: unknown, mode: Case["mode"] = "operations", source?: string): Result<T> {
  return invokeMany<T>([{ document, mode, source }])[0];
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
      RUNTIME_FIELD_SEARCH: "runtimeFieldSearch",
    };
    const namespace = { in: "path", name: "namespace", schema: { type: "string", enum: ["custom"] } };
    const paths = Object.fromEntries(
      Object.keys(expected).map((kind) => [
        `/api/v2/examples/${kind}`,
        { get: { ...op(kind, kind), ...(kind === "RUNTIME_FIELDS" && { parameters: [namespace] }) } },
      ]),
    );
    expect(invoke({ paths }).value).toEqual({
      examples: Object.fromEntries(Object.entries(expected).map(([kind, slot]) => [slot, kind])),
    });
  });
  it("skips CUSTOM-only tags", () => {
    expect(invoke(spec(op("CUSTOM"))).value).toEqual({});
  });
  it("groups operations by tag instead of guessing from the route", () => {
    expect(invoke(spec(op("LIST"), "/api/v2/different")).value).toEqual({
      examples: { list: "example" },
    });
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
    expect(
      invoke({
        paths: {
          "/api/v2/examples": { get: op("LIST"), post: op("LIST", "other") },
        },
      }).error,
    ).toContain("Duplicate slot");
  });
  it("rejects missing request and query-key exports", () => {
    const results = invokeMany(
      ["", "export const example = () => {};", null].map(
        (source): Case => ({
          document: spec(op("LIST")),
          mode: "generate",
          source,
        }),
      ),
    );
    expect(results.map(({ error }) => error)).toEqual([
      expect.stringContaining("Missing import target example"),
      expect.stringContaining("Missing import target getExampleQueryKey"),
      expect.stringContaining("missing file"),
    ]);
  });
  it("emits the list filter and runtime-field namespaces with operation assertions, omitting an absent filter", () => {
    const source = ["example", "getExampleQueryKey", "fields", "getFieldsQueryKey", "search", "getSearchQueryKey"]
      .map((name) => `export const ${name} = 1;`)
      .join("\n");
    const where = { $ref: "#/components/parameters/ExamplesWhere" };
    const namespace = (schema: object) => ({ in: "path", name: "namespace", schema });
    const documentWith = (list: object, runtimeFields?: object, search = false) => ({
      components: {
        parameters: {
          ExamplesWhere: {
            in: "query",
            name: "where",
            "x-rspace-filter": { $ref: "#/components/x-rspace-filters/Examples" },
          },
        },
        "x-rspace-filters": { Examples: { selectors: {} } },
      },
      paths: {
        "/api/v2/examples": { get: { ...op("LIST"), ...list } },
        ...(runtimeFields && {
          "/api/v2/examples/fields/{namespace}": { get: { ...op("RUNTIME_FIELDS", "fields"), ...runtimeFields } },
        }),
        ...(search && { "/api/v2/examples/fields": { get: op("RUNTIME_FIELD_SEARCH", "search") } }),
      },
    });
    const [filtered, unfiltered, invalidNamespace, searchOnly] = invokeMany<string>(
      [
        documentWith({ parameters: [where] }, { parameters: [namespace({ type: "string", enum: ["a", "b"] })] }, true),
        documentWith({}),
        documentWith({}, { parameters: [namespace({ type: "string" })] }),
        documentWith({}, undefined, true),
      ].map((document): Case => ({ document, mode: "generate", source })),
    );
    expect(filtered.value).toContain('filterResource: "Examples",');
    expect(filtered.value).toContain('runtimeFieldNamespaces: ["a", "b"] as const,');
    expect(filtered.value).toMatch(/ApiV2ListOperation<\s*\(typeof apiV2Resources\)\["examples"\]\["filterResource"\]/);
    expect(filtered.value).toMatch(/ApiV2RuntimeFieldsOperation<\s*\(typeof apiV2Resources\)\["examples"\]/);
    expect(filtered.value).toContain(
      "runtimeFieldSearch: { request: resource0.search, queryKey: resource0.getSearchQueryKey },",
    );
    expect(filtered.value).toMatch(/ApiV2RuntimeFieldSearchOperation<\s*\(typeof apiV2Resources\)\["examples"\]/);
    expect(unfiltered.error).toBeUndefined();
    expect(unfiltered.value).not.toContain("filterResource");
    expect(unfiltered.value).not.toContain("ApiV2ResourceOperationChecks");
    expect(invalidNamespace.error).toContain("RUNTIME_FIELDS operation needs one path parameter with a string enum");
    expect(searchOnly.error).toContain("RUNTIME_FIELD_SEARCH operation needs a RUNTIME_FIELDS operation");
  });
});
