import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";

function fakeResource(stub: typeof globalThis.fetch) {
  return {
    name: "instruments",
    runtimeFields: {
      queryKey: (namespace: string, params?: Record<string, unknown>) =>
        [`/api/v2/instruments/fields/${namespace}`, params] as const,
      request: async (namespace: string, params: Record<string, unknown> = {}, options?: RequestInit) => {
        const query = new URLSearchParams(
          Object.entries(params)
            .filter(([, value]) => value !== undefined)
            .map(([key, value]) => [key, String(value)]),
        );
        const response = await stub(`/api/v2/instruments/fields/${namespace}${query.size ? `?${query}` : ""}`, options);
        if (!response.ok)
          throw new ApiV2ProblemError(
            response.status,
            undefined,
            `Runtime field catalog request failed with status ${response.status}`,
          );
        return response.json() as Promise<unknown>;
      },
    },
  };
}

import { describe, expect, it } from "vitest";
import { definition } from "../../__tests__/fixtures/tableList";
import { fetchRuntimeFieldCatalog, fetchRuntimeFieldDefinitions } from "../runtimeFieldCatalog";

const namespace = {
  catalog: "/api/v2/instruments/fields/customFields",
  catalogMaximumIds: 50,
};

describe("fetchRuntimeFieldCatalog", () => {
  it("validates and returns the actor-scoped catalog", async () => {
    const stub: typeof globalThis.fetch = async (input) => {
      expect(String(input)).toBe(namespace.catalog);
      return new Response(
        JSON.stringify({ fields: [definition()], totalFields: 1, hasMore: false, page: 1, limit: 50 }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    };

    await expect(fetchRuntimeFieldCatalog(fakeResource(stub), namespace)).resolves.toEqual({
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

    await expect(fetchRuntimeFieldCatalog(fakeResource(stub), namespace)).rejects.toThrow();
  });

  it("accepts a page that reports more results without an exact total", async () => {
    const stub: typeof globalThis.fetch = async () =>
      new Response(JSON.stringify({ fields: [definition()], hasMore: true, page: 1, limit: 50 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const result = await fetchRuntimeFieldCatalog(fakeResource(stub), namespace);

    expect(result.hasMore).toBe(true);
    expect(result.totalFields).toBeUndefined();
  });

  it("reports a failed catalog request", async () => {
    const stub: typeof globalThis.fetch = async () => new Response("", { status: 403 });

    await expect(fetchRuntimeFieldCatalog(fakeResource(stub), namespace)).rejects.toThrow(
      "Runtime field catalog request failed with status 403",
    );
  });

  it.each([
    "https://example.invalid/api/v2/instruments/fields/customFields",
    "/api/v2/records/fields/customFields",
    "/api/v2/instruments/fields/customFields?next=/api/v2/records",
  ])("rejects an unapproved catalog URL before making a request: %s", async (catalog) => {
    let requests = 0;
    const stub: typeof globalThis.fetch = async () => {
      requests += 1;
      return new Response("{}", { status: 200 });
    };

    await expect(fetchRuntimeFieldCatalog(fakeResource(stub), { catalog })).rejects.toThrow(
      "Unsupported runtime field catalog route",
    );
    expect(requests).toBe(0);
  });

  it("rejects catalog redirects", async () => {
    let redirect: RequestRedirect | undefined;
    const stub: typeof globalThis.fetch = async (_input, init) => {
      redirect = init?.redirect;
      return new Response(JSON.stringify({ fields: [], hasMore: false, page: 1, limit: 50 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    await fetchRuntimeFieldCatalog(fakeResource(stub), namespace);

    expect(redirect).toBe("error");
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

    const fields = await fetchRuntimeFieldDefinitions(fakeResource(stub), namespace, ids);

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

    await fetchRuntimeFieldCatalog(fakeResource(stub), namespace, { search: "hazard", page: 2, limit: 200 });

    expect(urls[0]).toBe("/api/v2/instruments/fields/customFields?q=hazard&page=2&limit=200");
  });

  it("hydrates specific IDs instead of searching, for a saved view", async () => {
    const { urls, stub } = capture();

    await fetchRuntimeFieldCatalog(fakeResource(stub), namespace, { ids: ["SF104", "SF108"], search: "ignored" });

    expect(urls[0]).toBe("/api/v2/instruments/fields/customFields?ids=SF104%2CSF108");
  });
});
