import { HttpResponse, http } from "msw";
import * as v from "valibot";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { createApiV2CollectionAdapter } from "../../../adapters/apiV2/createApiV2CollectionAdapter";
import {
  apiV2CollectionRequestParams,
  createApiV2CollectionFetcher,
} from "../../../adapters/apiV2/createApiV2CollectionFetcher";
import { config, type TestRecord } from "../../fixtures/tableListFixtures";

const adapter = createApiV2CollectionAdapter({
  config,
  documentSchema: v.object({
    id: v.string(),
    title: v.string(),
    owner: v.string(),
    score: v.number(),
    enabled: v.boolean(),
    modifiedAt: v.string(),
  }),
  metadata: {
    resourceName: "records",
    fields: ["id", "title", "owner", "score", "enabled", "modifiedAt"],
    sorting: { fields: ["title", "modifiedAt"], default: [], maximumFields: 5 },
    filtering: {
      selectors: {
        title: { operators: ["=contains="], wildcards: false },
        owner: { operators: ["=contains="], wildcards: false },
      },
      limits: {
        maximumComparisons: 50,
        maximumLikeComparisons: 10,
        maximumNesting: 10,
        maximumArguments: 100,
        maximumWhereLength: 4096,
      },
    },
    pagination: { defaultLimit: 2, maximumLimit: 100 },
  },
});

describe("createApiV2CollectionFetcher", () => {
  it("sends the standard request and parses the response", async () => {
    server.use(
      http.get("/api/v2/records", ({ request }) => {
        const url = new URL(request.url);
        expect(request.headers.get("Authorization")).toBe("Bearer test-token");
        expect(url.searchParams.get("page")).toBe("1");
        return HttpResponse.json({
          docs: [],
          totalDocs: 12,
          limit: 2,
          page: 1,
          pagingCounter: 1,
          totalPages: 6,
          hasPrevPage: false,
          hasNextPage: true,
          prevPage: null,
          nextPage: 2,
        });
      }),
    );
    const fetchCollection = createApiV2CollectionFetcher<TestRecord>(adapter, { token: "test-token" });

    await expect(
      fetchCollection(
        {
          filters: { search: "", expression: null },
          sorting: [],
          page: { pageIndex: 0, pageSize: 2 },
          visibleFields: ["title"],
        },
        { signal: new AbortController().signal },
      ),
    ).resolves.toEqual({ rows: [], rowCount: 12 });
  });

  it("raises the request depth for a visible target field", async () => {
    type Booking = {
      id: string;
      target: { relationTo: "instruments"; value: { id: number; name: string } };
    };
    const bookingAdapter = createApiV2CollectionAdapter<Booking>({
      config: {
        slug: "bookings",
        idField: "id",
        labels: { singularKey: "booking", pluralKey: "bookings" },
        useAsTitle: "target",
        defaultColumns: ["target"],
        fields: [
          { name: "id", labelKey: "id", type: "text", list: false },
          {
            name: "target",
            labelKey: "target",
            type: "relationship",
            relationTo: "instruments",
            hasMany: false,
          },
        ],
      },
      documentSchema: v.object({
        id: v.string(),
        target: v.object({
          relationTo: v.literal("instruments"),
          value: v.object({ id: v.number(), name: v.string() }),
        }),
      }),
      metadata: {
        resourceName: "bookings",
        fields: ["id", "target"],
        sorting: { fields: [], default: [], maximumFields: 5 },
        filtering: {
          selectors: {
            "target.name": { operators: ["=contains="], wildcards: false, fieldType: "text" },
          },
          limits: {
            maximumComparisons: 50,
            maximumLikeComparisons: 10,
            maximumNesting: 10,
            maximumArguments: 100,
            maximumWhereLength: 4096,
          },
        },
        pagination: { defaultLimit: 20, maximumLimit: 100 },
      },
    });
    server.use(
      http.get("/api/v2/bookings", ({ request }) => {
        const url = new URL(request.url);
        expect(url.searchParams.get("depth")).toBe("2");
        expect(url.searchParams.get("fields[bookings]")).toBe("id,target");
        return HttpResponse.json({
          docs: [],
          totalDocs: 0,
          limit: 20,
          page: 1,
          pagingCounter: 1,
          totalPages: 0,
          hasPrevPage: false,
          hasNextPage: false,
          prevPage: null,
          nextPage: null,
        });
      }),
    );
    const fetchCollection = createApiV2CollectionFetcher(bookingAdapter, { depth: 2 });

    await fetchCollection(
      {
        filters: { search: "", expression: null },
        sorting: [],
        page: { pageIndex: 0, pageSize: 20 },
        visibleFields: ["target.name" as never],
      },
      { signal: new AbortController().signal },
    );
  });

  it("uses a fixed projection independently of the visible columns", async () => {
    const projection = { fixed: ["title", "score"] } as const;
    const state = {
      filters: { search: "", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 2 },
      visibleFields: ["title"],
    } satisfies Parameters<ReturnType<typeof createApiV2CollectionFetcher<TestRecord>>>[0];
    const changedColumns = { ...state, visibleFields: ["owner"] as const };

    expect(apiV2CollectionRequestParams(adapter, state, undefined, projection).toString()).toBe(
      apiV2CollectionRequestParams(adapter, changedColumns, undefined, projection).toString(),
    );

    server.use(
      http.get("/api/v2/records", ({ request }) => {
        expect(new URL(request.url).searchParams.get("fields[records]")).toBe("id,title,score");
        return HttpResponse.json({
          docs: [{ id: "one", title: "One", score: 1 }],
          totalDocs: 1,
          limit: 2,
          page: 1,
          pagingCounter: 1,
          totalPages: 1,
          hasPrevPage: false,
          hasNextPage: false,
          prevPage: null,
          nextPage: null,
        });
      }),
    );

    const fetchCollection = createApiV2CollectionFetcher(adapter, { projection });
    await expect(fetchCollection(state, { signal: new AbortController().signal })).resolves.toEqual({
      rows: [{ id: "one", title: "One", score: 1 }],
      rowCount: 1,
    });
  });
});
