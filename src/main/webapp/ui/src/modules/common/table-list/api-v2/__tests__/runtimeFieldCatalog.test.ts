import { describe, expect, it, vi } from "vitest";
import { hazardClassDefinition } from "../../__tests__/fixtures/tableList";
import { fetchRuntimeFieldCatalog, fetchRuntimeFieldDefinitions } from "../runtimeFieldCatalog";

const namespace = {
  catalog: "/api/v2/instruments/fields/customFields",
  catalogMaximumIds: 50,
};
const page = { fields: [hazardClassDefinition], totalFields: 1, hasMore: false, page: 1, limit: 50 };
const emptyPage = { fields: [], totalFields: 0, hasMore: false, page: 1, limit: 50 };

function fakeResource(
  request: (namespace: string, params: Record<string, unknown>, options: RequestInit) => Promise<unknown>,
) {
  return { name: "instruments", runtimeFields: { queryKey: () => [], request } };
}

describe("fetchRuntimeFieldCatalog", () => {
  it("validates and returns the actor-scoped catalog", async () => {
    const request = vi.fn(async () => page);
    await expect(fetchRuntimeFieldCatalog(fakeResource(request), namespace)).resolves.toEqual(page);
    expect(request).toHaveBeenCalledWith("customFields", {}, { signal: undefined, redirect: "error" });
  });

  it("rejects a catalog entry with an unknown operator", async () => {
    const resource = fakeResource(async () => ({
      ...page,
      fields: [{ ...hazardClassDefinition, operators: ["=regex="] }],
    }));
    await expect(fetchRuntimeFieldCatalog(resource, namespace)).rejects.toThrow();
  });

  it("accepts a page that reports more results without an exact total", async () => {
    const resource = fakeResource(async () => ({ fields: [hazardClassDefinition], hasMore: true, page: 1, limit: 50 }));
    const result = await fetchRuntimeFieldCatalog(resource, namespace);
    expect(result.hasMore).toBe(true);
    expect(result.totalFields).toBeUndefined();
  });

  it.each([
    "https://example.invalid/api/v2/instruments/fields/customFields",
    "/api/v2/records/fields/customFields",
    "/api/v2/instruments/fields/customFields?next=/api/v2/records",
  ])("rejects an unapproved catalog URL before making a request: %s", async (catalog) => {
    const request = vi.fn(async () => emptyPage);
    await expect(fetchRuntimeFieldCatalog(fakeResource(request), { catalog })).rejects.toThrow(
      "Unsupported runtime field catalog route",
    );
    expect(request).not.toHaveBeenCalled();
  });

  it("hydrates saved definitions in batches within the published ID limit", async () => {
    const requested: string[][] = [];
    const resource = fakeResource(async (_namespace, params) => {
      const ids = String(params.ids).split(",");
      requested.push(ids);
      return {
        ...page,
        fields: ids.map((id) => ({ ...hazardClassDefinition, id, selector: `customFields.${id}` })),
        totalFields: ids.length,
      };
    });
    const ids = Array.from({ length: 51 }, (_, index) => `SF${index + 1}`);
    const fields = await fetchRuntimeFieldDefinitions(resource, namespace, ids);
    expect(requested.map((batch) => batch.length)).toEqual([50, 1]);
    expect(fields.map((field) => field.id)).toEqual(ids);
  });
});

describe("catalog request shape", () => {
  it("browses with a search term and a bounded page", async () => {
    const request = vi.fn(async () => emptyPage);
    await fetchRuntimeFieldCatalog(fakeResource(request), namespace, { search: "hazard", page: 2, limit: 200 });
    expect(request).toHaveBeenCalledWith(
      "customFields",
      { q: "hazard", page: 2, limit: 200 },
      { signal: undefined, redirect: "error" },
    );
  });

  it("hydrates specific IDs instead of searching, for a saved view", async () => {
    const request = vi.fn(async () => emptyPage);
    await fetchRuntimeFieldCatalog(fakeResource(request), namespace, { ids: ["SF104", "SF108"], search: "ignored" });
    expect(request).toHaveBeenCalledWith(
      "customFields",
      { ids: "SF104,SF108" },
      { signal: undefined, redirect: "error" },
    );
  });
});
