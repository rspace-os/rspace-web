import { describe, expect, it } from "vitest";
import {
  fetchRuntimeFieldCatalog,
  fetchRuntimeFieldDefinitions,
  type RuntimeFieldDefinition,
} from "../../../adapters/apiV2/runtimeFieldCatalog";

const namespace = {
  catalog: "/api/v2/instruments/custom-fields",
  catalogMaximumIds: 50,
};

function definition(overrides: Partial<RuntimeFieldDefinition> = {}): RuntimeFieldDefinition {
  return {
    id: "SF104",
    selector: "customFields.SF104",
    label: "Hazard class",
    type: "text",
    jsonType: "string",
    operators: ["==", "!=", "=in=", "=out=", "=contains=", "=like=", "=exists="],
    supportsWildcards: false,
    columnSelectable: true,
    sortable: false,
    source: { id: "IT9", label: "Cell line template" },
    options: [],
    ...overrides,
  };
}

describe("fetchRuntimeFieldCatalog", () => {
  it("validates and returns the actor-scoped catalog", async () => {
    const stub: typeof globalThis.fetch = async (input) => {
      expect(String(input)).toBe(namespace.catalog);
      return new Response(
        JSON.stringify({ fields: [definition()], totalFields: 1, hasMore: false, page: 1, limit: 50 }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    };

    await expect(fetchRuntimeFieldCatalog(namespace, { fetch: stub })).resolves.toEqual({
      fields: [definition()],
      totalFields: 1,
      hasMore: false,
      page: 1,
      limit: 50,
    });
  });

  it("rejects a catalog entry with an unknown operator", async () => {
    const stub: typeof globalThis.fetch = async () =>
      new Response(
        JSON.stringify({
          fields: [{ ...definition(), operators: ["=regex="] }],
          totalFields: 1,
          hasMore: false,
          page: 1,
          limit: 50,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );

    await expect(fetchRuntimeFieldCatalog(namespace, { fetch: stub })).rejects.toThrow();
  });

  it("accepts a page that reports more results without an exact total", async () => {
    const stub: typeof globalThis.fetch = async () =>
      new Response(JSON.stringify({ fields: [definition()], hasMore: true, page: 1, limit: 50 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const result = await fetchRuntimeFieldCatalog(namespace, { fetch: stub });

    expect(result.hasMore).toBe(true);
    expect(result.totalFields).toBeUndefined();
  });

  it("reports a failed catalog request", async () => {
    const stub: typeof globalThis.fetch = async () => new Response("", { status: 403 });

    await expect(fetchRuntimeFieldCatalog(namespace, { fetch: stub })).rejects.toThrow(
      "Runtime field catalog request failed with status 403",
    );
  });

  it("hydrates saved definitions in batches within the published ID limit", async () => {
    const requested: string[][] = [];
    const stub: typeof globalThis.fetch = async (input) => {
      const ids = new URL(String(input), "https://example.test").searchParams.get("ids")?.split(",") ?? [];
      requested.push(ids);
      return new Response(
        JSON.stringify({
          fields: ids.map((id) => definition({ id, selector: `customFields.${id}` })),
          totalFields: ids.length,
          hasMore: false,
          page: 1,
          limit: 50,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    };
    const ids = Array.from({ length: 51 }, (_, index) => `SF${index + 1}`);

    const fields = await fetchRuntimeFieldDefinitions(namespace, ids, { fetch: stub });

    expect(requested.map((batch) => batch.length)).toEqual([50, 1]);
    expect(fields.map((field) => field.id)).toEqual(ids);
  });
});

describe("catalog request shape", () => {
  const page = { fields: [], totalFields: 0, hasMore: false, page: 1, limit: 50 };

  function capture() {
    const urls: string[] = [];
    const stub: typeof globalThis.fetch = async (input) => {
      urls.push(String(input));
      return new Response(JSON.stringify(page), { status: 200, headers: { "Content-Type": "application/json" } });
    };
    return { urls, stub };
  }

  it("browses with a search term and a bounded page", async () => {
    const { urls, stub } = capture();

    await fetchRuntimeFieldCatalog(namespace, { fetch: stub, search: "hazard", page: 2, limit: 200 });

    expect(urls[0]).toBe("/api/v2/instruments/custom-fields?search=hazard&page=2&limit=200");
  });

  it("hydrates specific IDs instead of searching, for a saved view", async () => {
    const { urls, stub } = capture();

    await fetchRuntimeFieldCatalog(namespace, { fetch: stub, ids: ["SF104", "SF108"], search: "ignored" });

    expect(urls[0]).toBe("/api/v2/instruments/custom-fields?ids=SF104%2CSF108");
  });
});
