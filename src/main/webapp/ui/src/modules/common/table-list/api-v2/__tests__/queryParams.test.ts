import { describe, expect, it } from "vitest";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import type { CollectionConfig } from "@/modules/common/collection/config";
import { testRecordSchema as documentSchema } from "../../__tests__/fixtures/schema";
import type { TestRecord } from "../../__tests__/fixtures/tableList";
import type { CollectionQueryState, FilterExpression } from "../../types";
import { createApiV2CollectionAdapter } from "../adapter";
import type { ApiV2CollectionMetadata } from "../metadata";

const metadata: ApiV2CollectionMetadata<TestRecord> = {
  resourceName: "records",
  fields: ["id", "title", "owner", "score", "enabled", "modifiedAt", "capability"],
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
  labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
  useAsTitle: "title",
  defaultColumns: ["title", "owner"],
  listSearchableFields: ["title", "owner"],
  fields: [
    { name: "id", type: "text", labelKey: "tableListExamples:fields.id" },
    { name: "title", type: "text", labelKey: "tableListExamples:fields.title" },
    { name: "owner", type: "text", labelKey: "tableListExamples:fields.owner", list: { dependencies: ["enabled"] } },
    { name: "score", type: "number", labelKey: "tableListExamples:fields.score" },
    { name: "enabled", type: "boolean", labelKey: "tableListExamples:fields.enabled" },
    { name: "modifiedAt", type: "dateTime", labelKey: "tableListExamples:fields.modified" },
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
    const params = adapter.toRequestParams(state);
    expect(params.page).toBe(3);
    expect(params.limit).toBe(10);
    expect(params.sort).toBe("-score,title");
    expect(params.where).toBe("(title=contains=Ada,owner=contains=Ada);score=ge=8");
    expect(params.fields?.records?.split(",")).toEqual(["id", "title", "owner", "enabled"]);
  });

  it("keeps the selected API fields in schema order", () => {
    const first = adapter.toRequestParams({ ...state, visibleFields: ["owner", "modifiedAt"] });
    const reordered = adapter.toRequestParams({ ...state, visibleFields: ["modifiedAt", "owner"] });

    expect(first.fields?.records).toBe("id,title,owner,enabled,modifiedAt");
    expect(reordered.fields?.records).toBe(first.fields?.records);
  });

  it("validates the selected fields only", () => {
    const selected = adapter.selectedFields(state);
    const sparse = [{ id: "1", title: "A", owner: "Ada", enabled: true }];
    const envelope = { ...v2ListEnvelope(sparse), totalDocs: 1, limit: 10 };

    expect(adapter.parseResponse({ ...envelope, docs: sparse }, selected)).toEqual({
      rows: sparse,
      rowCount: 1,
      hasNextPage: false,
    });
    expect(adapter.parseResponse({ docs: [], totalDocs: 12, limit: 2, page: 1, hasNextPage: true }, selected)).toEqual({
      rows: [],
      rowCount: 12,
      hasNextPage: true,
    });

    expect(() =>
      adapter.parseResponse({ ...envelope, docs: [{ id: "1", title: "A", enabled: true }] }, selected),
    ).toThrow("owner");
  });

  it("rejects the backend sort and page-size limits", () => {
    expect(() => adapter.toRequestParams({ ...state, page: { pageIndex: 0, pageSize: 101 } })).toThrow("Page size");
    expect(() =>
      adapter.toRequestParams({
        ...state,
        sorting: Array.from({ length: 6 }, () => ({ field: "title", direction: "asc" as const })),
      }),
    ).toThrow("Sort field limit");
  });

  it("allows the published number of nested filter groups", () => {
    const nested = (groups: number) => {
      let expression: FilterExpression<TestRecord> = {
        kind: "comparison",
        field: "title",
        operator: "equals",
        value: "a",
      };
      for (let group = 0; group < groups; group += 1) {
        expression = {
          kind: group % 2 === 0 ? "or" : "and",
          children: [expression, { kind: "comparison", field: "score", operator: "equals", value: group }],
        };
      }
      return { ...state, filters: { search: "", expression } };
    };
    expect(() => adapter.toRequestParams(nested(10))).not.toThrow();
    expect(() => adapter.toRequestParams(nested(11))).toThrow("nesting limit");
  });
});
