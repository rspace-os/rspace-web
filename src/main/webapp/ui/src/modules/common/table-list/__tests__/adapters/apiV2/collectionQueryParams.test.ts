import * as v from "valibot";
import { describe, expect, it } from "vitest";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import type { ApiV2CollectionMetadata } from "../../../adapters/apiV2/apiV2CollectionMetadata";
import { createApiV2CollectionAdapter } from "../../../adapters/apiV2/createApiV2CollectionAdapter";
import type { CollectionQueryState } from "../../../tableListState";
import type { TestRecord } from "../../fixtures/tableListFixtures";

const documentSchema = v.object({
  id: v.string(),
  title: v.string(),
  owner: v.string(),
  score: v.number(),
  enabled: v.boolean(),
  modifiedAt: v.string(),
});

const metadata: ApiV2CollectionMetadata<TestRecord> = {
  resourceName: "records",
  fields: ["id", "title", "owner", "score", "enabled", "modifiedAt"],
  sorting: { fields: ["title", "score", "modifiedAt"], default: [], maximumFields: 5 },
  filtering: {
    selectors: {
      title: { operators: ["==", "=contains="], wildcards: true },
      owner: { operators: ["==", "=contains="], wildcards: true },
      score: { operators: ["==", "=ge="], wildcards: false },
    },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  pagination: { defaultLimit: 10, maximumLimit: 100 },
};

const collectionConfig: CollectionConfig<TestRecord> = {
  slug: "records",
  idField: "id",
  labels: { singularKey: "record", pluralKey: "records" },
  useAsTitle: "title",
  defaultColumns: ["title", "owner"],
  listSearchableFields: ["title", "owner"],
  fields: [
    { name: "id", type: "text", labelKey: "id" },
    { name: "title", type: "text", labelKey: "title" },
    { name: "owner", type: "text", labelKey: "owner", list: { dependencies: ["enabled"] } },
    { name: "score", type: "number", labelKey: "score" },
    { name: "enabled", type: "boolean", labelKey: "enabled" },
    { name: "modifiedAt", type: "dateTime", labelKey: "modifiedAt" },
  ],
};

const adapter = createApiV2CollectionAdapter({ config: collectionConfig, documentSchema, metadata });

const state: CollectionQueryState<TestRecord> = {
  filters: {
    search: "Ada",
    expression: { kind: "comparison", field: "score", operator: "greaterThanOrEqual", value: 8 },
  },
  sorting: [
    { field: "score", direction: "desc" },
    { field: "title", direction: "asc" },
  ],
  page: { pageIndex: 2, pageSize: 10 },
  visibleFields: ["owner"],
};

describe("createApiV2CollectionAdapter", () => {
  it("maps controlled state to v2 collection parameters", () => {
    const params = adapter.toSearchParams(state);
    expect(params.get("page")).toBe("3");
    expect(params.get("limit")).toBe("10");
    expect(params.get("sort")).toBe("-score,title");
    expect(params.get("where")).toBe("(title=contains=Ada,owner=contains=Ada);score=ge=8");
    expect(params.get("fields[records]")?.split(",")).toEqual(["id", "title", "owner", "enabled"]);
  });

  it("keeps the selected API fields in schema order", () => {
    const first = adapter.toSearchParams({ ...state, visibleFields: ["owner", "modifiedAt"] });
    const reordered = adapter.toSearchParams({ ...state, visibleFields: ["modifiedAt", "owner"] });

    expect(first.get("fields[records]")).toBe("id,title,owner,enabled,modifiedAt");
    expect(reordered.get("fields[records]")).toBe(first.get("fields[records]"));
  });

  it("parses the shared v2 list envelope", () => {
    const docs: TestRecord[] = [{ id: "1", title: "A", owner: "Ada", score: 1, enabled: true, modifiedAt: "now" }];
    expect(
      adapter.parseResponse(
        {
          docs,
          totalDocs: 42,
          limit: 10,
          page: 1,
          pagingCounter: 1,
          totalPages: 5,
          hasPrevPage: false,
          hasNextPage: true,
          prevPage: null,
          nextPage: 2,
        },
        adapter.selectedFields(state),
      ),
      // The parsed row carries the selected fields only, like the request asked for.
    ).toEqual({ rows: [{ id: "1", title: "A", owner: "Ada", enabled: true }], rowCount: 42 });
  });

  it("validates the selected fields only", () => {
    const selected = adapter.selectedFields(state);
    const sparse = [{ id: "1", title: "A", owner: "Ada", enabled: true }];
    const envelope = {
      totalDocs: 1,
      limit: 10,
      page: 1,
      pagingCounter: 1,
      totalPages: 1,
      hasPrevPage: false,
      hasNextPage: false,
      prevPage: null,
      nextPage: null,
    };

    // `score` and `modifiedAt` are not selected, so their absence is not an error.
    expect(adapter.parseResponse({ ...envelope, docs: sparse }, selected)).toEqual({ rows: sparse, rowCount: 1 });

    // `owner` is selected, so its absence is a contract failure.
    expect(() =>
      adapter.parseResponse({ ...envelope, docs: [{ id: "1", title: "A", enabled: true }] }, selected),
    ).toThrow(/owner/);
  });

  it("rejects the backend sort and page-size limits", () => {
    expect(() => adapter.toSearchParams({ ...state, page: { pageIndex: 0, pageSize: 101 } })).toThrow(/Page size/);
    expect(() =>
      adapter.toSearchParams({
        ...state,
        sorting: Array.from({ length: 6 }, () => ({ field: "title", direction: "asc" as const })),
      }),
    ).toThrow(/Sort field limit/);
  });
});
