import { describe, expect, it, vi } from "vitest";
import type { ApiV2RuntimeFieldParams } from "@/modules/common/api/v2/apiV2Resources";
import { hazardClassDefinition } from "../../__tests__/fixtures/tableList";
import {
  bindRuntimeFieldCatalog,
  bindRuntimeFieldSearch,
  fetchRuntimeFieldCatalog,
  fetchRuntimeFieldDefinitions,
  mapRuntimeFieldSearch,
  registeredRuntimeFieldCatalog,
  registeredRuntimeFieldSearches,
} from "../runtimeFieldCatalog";

const namespace = {
  namespace: "customFields",
  catalog: "/api/v2/instruments/fields/customFields",
  catalogMaximumIds: 50,
};
const page = { fields: [hazardClassDefinition], totalFields: 1, hasMore: false, page: 1, limit: 50 };
const emptyPage = { fields: [], totalFields: 0, hasMore: false, page: 1, limit: 50 };

function fakeResource(
  request: (namespace: string, params?: ApiV2RuntimeFieldParams, options?: RequestInit) => Promise<unknown>,
) {
  const catalog = bindRuntimeFieldCatalog(
    { name: "instruments", runtimeFieldNamespaces: ["customFields"], runtimeFields: { queryKey: () => [], request } },
    "customFields",
  );
  if (!catalog) throw new Error("The fake resource publishes customFields");
  return catalog;
}

describe("runtime field catalog binding", () => {
  it("binds only a namespace the resource publishes", () => {
    const resource = {
      name: "instruments",
      runtimeFieldNamespaces: ["customFields"],
      runtimeFields: { queryKey: () => [], request: async () => emptyPage },
    };
    expect(bindRuntimeFieldCatalog(resource, "target.customFields")?.resourceName).toBe("instruments");
    expect(bindRuntimeFieldCatalog(resource, "extraFields")).toBeUndefined();
  });

  it("finds the registered resource that publishes a catalog route", () => {
    const catalog = "/api/v2/instruments/fields/extraFields";
    expect(registeredRuntimeFieldCatalog({ namespace: "extraFields", catalog })?.queryKey({ ids: "SF1" })).toEqual([
      catalog,
      { ids: "SF1" },
    ]);
    expect(
      registeredRuntimeFieldCatalog({ namespace: "otherFields", catalog: "/api/v2/instruments/fields/otherFields" }),
    ).toBeUndefined();
    expect(
      registeredRuntimeFieldCatalog({ namespace: "customFields", catalog: "/api/v2/users/fields/customFields" }),
    ).toBeUndefined();
  });
});

describe("runtime field search binding", () => {
  it("requests each distinct published catalog namespace once and drops the rest", () => {
    const request = vi.fn(async () => ({}));
    const search = bindRuntimeFieldSearch(
      {
        name: "instruments",
        runtimeFieldNamespaces: ["customFields", "extraFields"],
        runtimeFieldSearch: { queryKey: () => [], request },
      },
      ["customFields", "target.customFields", "otherFields", "extraFields"],
    );
    expect(search?.namespaces).toEqual(["customFields", "extraFields"]);
    void search?.request({ q: "cab", limit: 20 });
    expect(request).toHaveBeenCalledWith(
      { q: "cab", limit: 20, namespace: ["customFields", "extraFields"] },
      undefined,
    );
  });

  it("groups registered sources by resource and skips unregistered routes", () => {
    const sources = [
      { namespace: "customFields", catalog: "/api/v2/instruments/fields/customFields" },
      { namespace: "target.customFields", catalog: "/api/v2/instruments/fields/customFields" },
      { namespace: "extraFields", catalog: "/api/v2/instruments/fields/extraFields" },
      { namespace: "customFields", catalog: "/api/v2/users/fields/customFields" },
    ];
    const searches = registeredRuntimeFieldSearches(sources);
    expect(searches).toHaveLength(1);
    expect(searches[0].sources).toEqual(sources.slice(0, 3));
    expect(searches[0].search.queryKey({ q: "cab" })).toEqual([
      "/api/v2/instruments/fields",
      { q: "cab", namespace: ["customFields", "extraFields"] },
    ]);
  });

  it("decodes each requested namespace, including a truncated catalog", () => {
    const catalogs = mapRuntimeFieldSearch(
      {
        catalogs: {
          customFields: { fields: [hazardClassDefinition], hasMore: true, page: 1, limit: 20 },
          extraFields: { ...emptyPage, limit: 20 },
        },
      },
      ["customFields", "extraFields"],
    );
    expect(catalogs.get("customFields")?.hasMore).toBe(true);
    expect(catalogs.get("customFields")?.totalFields).toBeUndefined();
    expect(catalogs.get("extraFields")).toMatchObject({ fields: [], totalFields: 0 });
  });

  it("rejects a response missing a requested namespace", () => {
    expect(() => mapRuntimeFieldSearch({ catalogs: { customFields: page } }, ["customFields", "extraFields"])).toThrow(
      "extraFields",
    );
  });
});

describe("fetchRuntimeFieldCatalog", () => {
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
    await expect(
      fetchRuntimeFieldCatalog(fakeResource(request), { namespace: "customFields", catalog }),
    ).rejects.toThrow("Unsupported runtime field catalog route");
    expect(request).not.toHaveBeenCalled();
  });

  it("hydrates saved definitions in batches within the published ID limit", async () => {
    const requested: string[][] = [];
    const resource = fakeResource(async (_namespace, params) => {
      const ids = String(params?.ids).split(",");
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
  it.each<[string, Parameters<typeof fetchRuntimeFieldCatalog>[2], ApiV2RuntimeFieldParams, typeof page]>([
    ["validates and returns the actor-scoped catalog", undefined, {}, page],
    [
      "browses with a search term and a bounded page",
      { search: "hazard", page: 2, limit: 200 },
      { q: "hazard", page: 2, limit: 200 },
      emptyPage,
    ],
    [
      "hydrates specific IDs instead of searching, for a saved view",
      { ids: ["SF104", "SF108"], search: "ignored" },
      { ids: "SF104,SF108" },
      emptyPage,
    ],
  ])("%s", async (_label, options, params, response) => {
    const request = vi.fn(async () => response);
    await expect(fetchRuntimeFieldCatalog(fakeResource(request), namespace, options)).resolves.toEqual(response);
    expect(request).toHaveBeenCalledWith("customFields", params, { signal: undefined, redirect: "error" });
  });
});
