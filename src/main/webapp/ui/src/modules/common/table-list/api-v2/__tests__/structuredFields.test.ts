import * as v from "valibot";
import { describe, expect, it } from "vitest";
import type { CollectionConfig } from "@/modules/common/collection/config";
import { createApiV2CollectionAdapter } from "../adapter";
import type { ApiV2CollectionMetadata, ApiV2StructuredField } from "../metadata";

type RecordDocument = {
  id: string;
  title: string;
  rows: Record<string, unknown>[];
  owners: { relationTo: string; value: number }[];
};

const config = {
  slug: "records",
  idField: "id",
  useAsTitle: "title",
  defaultColumns: ["title", "rows", "owners"],
  labels: { singularKey: "tableListExamples:record", pluralKey: "tableListExamples:records" },
  fields: [
    { name: "id", type: "text", labelKey: "tableListExamples:fields.id" },
    { name: "title", type: "text", labelKey: "tableListExamples:fields.title" },
    {
      name: "rows",
      type: "array",
      labelKey: "tableListExamples:fields.score",
      fields: [
        {
          name: "dayOfWeek",
          type: "number",
          labelKey: "tableListExamples:fields.score",
          form: { descriptionKey: "tableListExamples:fields.titleDescription" },
        },
        {
          name: "details",
          type: "object",
          labelKey: "tableListExamples:fields.notes",
          fields: [
            {
              name: "roles",
              type: "array",
              labelKey: "tableListExamples:fields.collaborators",
              fields: [
                {
                  name: "role",
                  type: "select",
                  labelKey: "tableListExamples:fields.status",
                  options: [
                    { value: "OWNER", label: "Owner" },
                    { value: "BOOKER", label: "Booker" },
                  ],
                },
              ],
            },
          ],
        },
      ],
      form: { component: () => null },
    },
    {
      name: "owners",
      type: "relationship",
      labelKey: "tableListExamples:fields.collaborators",
      relationTo: "users",
      hasMany: true,
    },
  ],
} satisfies CollectionConfig<RecordDocument>;

const documentSchema = v.object({
  id: v.string(),
  title: v.string(),
  rows: v.array(v.record(v.string(), v.unknown())),
  owners: v.array(v.object({ relationTo: v.string(), value: v.number() })),
});

const rows: ApiV2StructuredField = {
  type: "array",
  nullable: true,
  readOnly: true,
  minRows: 1,
  maxRows: 7,
  fields: [
    {
      name: "dayOfWeek",
      type: "number",
      required: true,
      nullable: false,
      readOnly: false,
      defaultValue: 1,
      writeConstraints: { minimum: 1, maximum: 7 },
    },
    {
      name: "details",
      type: "object",
      required: false,
      nullable: true,
      readOnly: false,
      writeConstraints: {},
      fields: [
        {
          name: "label",
          type: "text",
          maximumLength: 12,
          readOnly: true,
          defaultValue: "Stored",
          writeConstraints: { pattern: "^[A-Z]", maxLength: 12 },
        },
        {
          name: "roles",
          type: "array",
          minRows: 0,
          maxRows: 3,
          writeConstraints: { minItems: 0, maxItems: 3 },
          fields: [
            { name: "enabled", type: "boolean", defaultValue: false },
            { name: "role", type: "select", options: ["OWNER", "BOOKER"] },
            { name: "expiresAt", type: "dateTime" },
          ],
        },
      ],
    },
  ],
};

/** The generated entry `tableMetadata.ts` writes for a Read component whose `rows` declares x-rspace-fields. */
function metadata(
  structuredFields: ApiV2CollectionMetadata<RecordDocument>["structuredFields"] = { rows },
): ApiV2CollectionMetadata<RecordDocument> {
  return {
    resourceName: "records",
    fields: ["id", "title", "rows", "owners"],
    structuredFields,
    sorting: { fields: ["title"], default: [], maximumFields: 5 },
    filtering: {
      selectors: { title: { fieldType: "text", operators: ["=contains="], wildcards: false } },
      limits: {
        maximumComparisons: 50,
        maximumLikeComparisons: 10,
        maximumNesting: 10,
        maximumArguments: 100,
        maximumWhereLength: 4096,
      },
    },
    runtimeFields: [],
    pagination: { defaultLimit: 20, maximumLimit: 100 },
  };
}

function adapter(supplied = metadata()) {
  return createApiV2CollectionAdapter({ config, documentSchema, metadata: supplied });
}

describe("declared structured fields", () => {
  it("resolves recursive Read metadata beside a named item reference for the custom editor", () => {
    const resolved = adapter();
    const rows = resolved.config.fields.find((entry) => entry.name === "rows");

    expect(rows).toMatchObject({
      type: "array",
      form: config.fields[2].form,
      nullable: true,
      readOnly: true,
      minRows: 1,
      maxRows: 7,
      capabilities: { sortable: false, filterOperators: [], supportsWildcards: false },
      fields: [
        {
          name: "dayOfWeek",
          type: "number",
          labelKey: "tableListExamples:fields.score",
          form: { descriptionKey: "tableListExamples:fields.titleDescription" },
          required: true,
          defaultValue: 1,
          writeConstraints: { minimum: 1, maximum: 7 },
        },
        {
          name: "details",
          type: "object",
          labelKey: "tableListExamples:fields.notes",
          nullable: true,
          fields: [
            {
              name: "label",
              type: "text",
              maximumLength: 12,
              readOnly: true,
              defaultValue: "Stored",
              writeConstraints: { pattern: "^[A-Z]", maxLength: 12 },
            },
            {
              name: "roles",
              type: "array",
              labelKey: "tableListExamples:fields.collaborators",
              minRows: 0,
              maxRows: 3,
              fields: [
                { name: "enabled", type: "boolean", defaultValue: false },
                {
                  name: "role",
                  type: "select",
                  labelKey: "tableListExamples:fields.status",
                  options: [
                    { value: "OWNER", label: "Owner" },
                    { value: "BOOKER", label: "Booker" },
                  ],
                },
                { name: "expiresAt", type: "dateTime" },
              ],
            },
          ],
        },
      ],
    });
    expect(resolved.config.fields.find((entry) => entry.name === "owners")).toMatchObject({
      type: "relationship",
      hasMany: true,
    });
    const state = {
      filters: { search: "", expression: null },
      sorting: [],
      page: { pageIndex: 0, pageSize: 20 },
      visibleFields: ["rows", "owners"] as const,
    };
    expect(resolved.toRequestParams(state).fields?.records).toBe("id,title,rows,owners");
    expect(() => resolved.toRequestParams({ ...state, sorting: [{ field: "rows", direction: "asc" }] })).toThrow();
    expect(() =>
      resolved.toRequestParams({
        ...state,
        filters: { search: "", expression: { kind: "comparison", field: "rows", operator: "equals", value: "one" } },
      }),
    ).toThrow();
  });

  it("rejects configured structured fields with a missing extension or incompatible type", () => {
    expect(() => adapter(metadata({}))).toThrow("must declare x-rspace-fields");
    expect(() => adapter(metadata({ rows: { ...rows, type: "object" } }))).toThrow("has a different structured type");
  });
});
