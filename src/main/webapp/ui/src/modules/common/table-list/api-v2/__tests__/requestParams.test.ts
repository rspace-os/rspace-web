import { hashKey } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import type { apiV2Resources } from "@/modules/common/api/v2/generated/apiV2Resources";
import {
  getListInstrumentsQueryKey,
  getListInstrumentsUrl,
} from "@/modules/common/api/v2/generated/instruments/instruments";
import { expandedTargetDocumentSchema, runtimeDocumentSchema, testRecordSchema } from "../../__tests__/fixtures/schema";
import { hazardClassDefinition, config as recordConfig, type TestRecord } from "../../__tests__/fixtures/tableList";
import type { CollectionQueryState } from "../../types";
import { createApiV2CollectionAdapter } from "../adapter";
import type { ApiV2CollectionMetadata } from "../metadata";
import { apiV2CollectionRequestParams } from "../requestParams";
import type { ApiV2DocumentSchema } from "../schema";

type Instrument = {
  id: number;
  name: string;
  customFields?: Record<string, string | number | readonly string[] | null>;
};

const metadata: ApiV2CollectionMetadata<Instrument> = {
  resourceName: "instruments",
  fields: ["id", "name"],
  sorting: { fields: ["name"], default: [{ field: "name", direction: "asc" }], maximumFields: 5 },
  filtering: {
    selectors: { name: { operators: ["==", "=contains="], wildcards: true, fieldType: "text" } },
    limits: {
      maximumComparisons: 50,
      maximumLikeComparisons: 10,
      maximumNesting: 10,
      maximumArguments: 100,
      maximumWhereLength: 4096,
    },
  },
  runtimeFields: [
    {
      namespace: "customFields",
      catalog: "/api/v2/instruments/fields/customFields",
      responseField: "customFields",
      filterable: true,
      columnSelectable: true,
      sortable: false,
      maximumProjections: 2,
      catalogDefaultLimit: 50,
      catalogMaximumLimit: 200,
      catalogMaximumIds: 50,
      via: "",
    },
  ],
  pagination: { defaultLimit: 20, maximumLimit: 100 },
};

const config = {
  slug: "instruments",
  idField: "id" as const,
  labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" } as const,
  useAsTitle: "name" as const,
  defaultColumns: ["name" as const],
  fields: [
    {
      name: "id" as const,
      labelKey: "tableListExamples:fields.id" as const,
      type: "number" as const,
      list: false as const,
    },
    { name: "name" as const, labelKey: "tableListExamples:fields.title" as const, type: "text" as const },
  ],
};

// Recorded from the URLSearchParams path this object replaced, so cached pages and saved views keep their keys.
const recordedUrl =
  "/api/v2/instruments?page=2&limit=20&sort=-name&where=name%3Dcontains%3D%22centri+fuge%22%3BcustomFields.SF104%3D%3DBSL-2&depth=2&totals=true&fields[instruments]=id%2Cname%2CcustomFields.SF104";
const recordedHashKey =
  '["/api/v2/instruments",{"depth":2,"fields":{"instruments":"id,name,customFields.SF104"},"limit":20,"page":2,"sort":"-name","totals":true,"where":"name=contains=\\"centri fuge\\";customFields.SF104==BSL-2"}]';

const recordAdapter = createApiV2CollectionAdapter({
  config: recordConfig,
  documentSchema: testRecordSchema,
  metadata: {
    resourceName: "records",
    fields: ["id", "title", "owner", "score", "enabled", "modifiedAt", "capability"],
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

describe("apiV2CollectionRequestParams", () => {
  it("preserves a configured depth above the minimum for a visible target field", () => {
    type Booking = {
      id: string;
      target: { relationTo: "instruments"; value: { id: number; name: string } };
    };
    const bookingAdapter = createApiV2CollectionAdapter<Booking>({
      config: {
        slug: "bookings",
        idField: "id",
        labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
        useAsTitle: "target",
        defaultColumns: ["target"],
        fields: [
          { name: "id", labelKey: "tableListExamples:fields.id", type: "text", list: false },
          {
            name: "target",
            labelKey: "tableListExamples:fields.owner",
            type: "relationship",
            relationTo: "instruments",
            hasMany: false,
          },
        ],
      },
      documentSchema: expandedTargetDocumentSchema,
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
    const params = apiV2CollectionRequestParams(
      bookingAdapter,
      {
        filters: { search: "", expression: null },
        sorting: [],
        page: { pageIndex: 0, pageSize: 20 },
        visibleFields: ["target.name" as never],
      },
      { depth: 2 },
    );

    expect(params.depth).toBe(2);
    expect(params.fields).toEqual({ bookings: "id,target" });
  });

  it("uses a fixed projection independently of the visible columns", () => {
    const projection = { fixed: ["title", "score", "capability"] } as const;
    const state: CollectionQueryState<TestRecord> = {
      filters: { search: "", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 2 },
      visibleFields: ["title"],
    };
    const params = apiV2CollectionRequestParams(recordAdapter, state, { projection });

    expect(params.fields).toEqual({ records: "id,title,score,capability" });
    expect(params).toEqual(
      apiV2CollectionRequestParams(recordAdapter, { ...state, visibleFields: ["owner"] }, { projection }),
    );
  });

  it("always ANDs the non-removable base filter with user filters", () => {
    const baseFilter = {
      kind: "comparison",
      field: "owner",
      operator: "contains",
      value: "ada",
    } as const;
    const state: CollectionQueryState<TestRecord> = {
      filters: {
        search: "",
        expression: { kind: "comparison", field: "title", operator: "contains", value: "scope" },
      },
      sorting: [],
      page: { pageIndex: 0, pageSize: 2 },
      visibleFields: ["title"],
    };

    const where = apiV2CollectionRequestParams(recordAdapter, state, { baseFilter }).where;
    expect(where).toContain("owner=contains=ada");
    expect(where).toContain("title=contains=scope");
    expect(
      apiV2CollectionRequestParams(
        recordAdapter,
        { ...state, filters: { search: "", expression: null } },
        { baseFilter },
      ).where,
    ).toContain("owner=contains=ada");
  });

  it("requests the same URL and query key as the search-parameter path it replaced", () => {
    const adapter = createApiV2CollectionAdapter<
      Instrument,
      ApiV2DocumentSchema<Instrument>,
      typeof apiV2Resources.instruments
    >({
      config,
      documentSchema: runtimeDocumentSchema,
      metadata,
      runtimeFields: [{ namespace: "customFields", definitions: [hazardClassDefinition] }],
    });
    const state: CollectionQueryState<Instrument> = {
      filters: {
        search: "",
        expression: {
          kind: "and",
          children: [
            { kind: "comparison", field: "name", operator: "contains", value: "centri fuge" },
            { kind: "comparison", field: "customFields.SF104", operator: "equals", value: "BSL-2" },
          ],
        },
      },
      sorting: [{ field: "name", direction: "desc" }],
      page: { pageIndex: 1, pageSize: 20 },
      visibleFields: ["name", "customFields.SF104" as "name"],
    };
    const params = { ...apiV2CollectionRequestParams(adapter, state, { depth: 2 }), totals: true };

    expect(getListInstrumentsUrl(params)).toBe(recordedUrl);
    expect(hashKey(getListInstrumentsQueryKey(params))).toBe(recordedHashKey);
  });
});
