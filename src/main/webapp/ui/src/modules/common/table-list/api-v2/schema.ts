import * as v from "valibot";
import type { FieldConfig } from "@/modules/common/collection/config";
import {
  type FilterOperatorToken,
  filterOperatorTokens,
  relationshipFilterPickerSchema,
  runtimeFieldValueTypes,
} from "@/modules/common/collection/schema";

export type ApiV2FilterOperator = FilterOperatorToken;

const filterOperator = v.picklist(Object.values(filterOperatorTokens));

const positiveInteger = v.pipe(v.number(), v.finite(), v.integer(), v.minValue(1));
const nonNegativeInteger = v.pipe(v.number(), v.finite(), v.integer(), v.minValue(0));
const strings = v.array(v.string());
export const objectInput = v.custom<Record<string, unknown>>(
  (input) => typeof input === "object" && input !== null && !Array.isArray(input),
  "Expected an object",
);
const scalarType = v.picklist(["string", "integer", "number", "boolean"]);
const jsonType = v.picklist(["string", "integer", "number", "boolean", "object", "array"]);
const declaredType = v.pipe(
  v.union([
    jsonType,
    v.strictTuple([jsonType]),
    v.strictTuple([jsonType, v.literal("null")]),
    v.strictTuple([v.literal("null"), jsonType]),
  ]),
  v.transform((type) => (typeof type === "string" ? type : type[0] === "null" ? type[1] : type[0])),
);
const noComposition = {
  anyOf: v.optional(v.never()),
  allOf: v.optional(v.never()),
  $ref: v.optional(v.never()),
};
const typedEntries = { ...noComposition, format: v.optional(v.string()), oneOf: v.optional(v.never()) };
const scalarSchema = v.object({ ...typedEntries, type: scalarType });

type PrimitiveFieldType = Extract<FieldConfig<unknown>["type"], "text" | "number" | "boolean" | "dateTime">;

function primitiveType(type: v.InferOutput<typeof jsonType>, format?: string): PrimitiveFieldType | null {
  switch (type) {
    case "string":
      return format === "date-time" ? "dateTime" : "text";
    case "integer":
    case "number":
      return "number";
    case "boolean":
      return "boolean";
    case "object":
    case "array":
      return null;
  }
}

const fieldTypeSchema = v.pipe(
  v.union([
    v.object({ ...typedEntries, type: declaredType }),
    v.object({ ...noComposition, type: v.optional(v.never()), oneOf: v.pipe(v.array(scalarSchema), v.minLength(1)) }),
  ]),
  v.transform((schema) => {
    if (schema.oneOf !== undefined) {
      const types = schema.oneOf.map((variant) => primitiveType(variant.type, variant.format));
      return types.reduce((type, next) => (type === next ? type : "text"));
    }
    return primitiveType(schema.type, schema.format);
  }),
);

export type ApiV2PrimitiveFieldType = Exclude<v.InferOutput<typeof fieldTypeSchema>, null>;

const selectorName = v.pipe(v.string(), v.regex(/^[A-Za-z][A-Za-z0-9_-]*(?:\.[A-Za-z][A-Za-z0-9_-]*)?$/));
const selectorSchema = v.pipe(
  v.object({
    schema: fieldTypeSchema,
    operators: v.array(filterOperator),
    wildcards: v.boolean(),
    title: v.optional(v.string()),
    viaTitle: v.optional(v.string()),
    picker: v.optional(relationshipFilterPickerSchema),
  }),
  v.transform(({ schema, ...selector }) => ({ ...selector, fieldType: schema })),
);

export const metadataSortSchema = v.object({ fields: strings, default: strings, maximumFields: positiveInteger });
export const metadataFilterSchema = v.object({
  maximumComparisons: positiveInteger,
  maximumLikeComparisons: positiveInteger,
  maximumNesting: positiveInteger,
  maximumArguments: positiveInteger,
  selectors: v.pipe(objectInput, v.record(selectorName, selectorSchema)),
});
export type ApiV2ParsedFilterMetadata = v.InferOutput<typeof metadataFilterSchema>;
export type ApiV2ParsedSelector = v.InferOutput<typeof selectorSchema>;

export const metadataWhereSchema = v.object({ maxLength: positiveInteger });
export const metadataLimitSchema = v.object({ default: positiveInteger, maximum: positiveInteger });
export const metadataAllowedFieldsSchema = v.pipe(objectInput, v.record(v.string(), strings));

const runtimeNamespaceSchema = v.object({
  namespace: v.pipe(v.string(), v.regex(/^(?:[A-Za-z][A-Za-z0-9_-]*\.)?(?:customFields|extraFields)$/)),
  catalog: v.pipe(v.string(), v.nonEmpty()),
  responseField: v.string(),
  filterable: v.boolean(),
  columnSelectable: v.boolean(),
  sortable: v.boolean(),
  maximumProjections: nonNegativeInteger,
  catalogDefaultLimit: positiveInteger,
  catalogMaximumLimit: positiveInteger,
  catalogMaximumIds: positiveInteger,
  via: v.string(),
});
export type ApiV2RuntimeFieldNamespace = v.InferOutput<typeof runtimeNamespaceSchema>;
export const metadataRuntimeFieldsSchema = v.optional(v.array(runtimeNamespaceSchema), () => []);

export const metadataDocumentSchema = v.object({
  paths: objectInput,
  components: v.object({ schemas: objectInput }),
});
export const metadataOperationSchema = v.object({
  get: v.object({
    parameters: v.array(v.looseObject({ name: v.string() })),
    responses: v.object({
      "200": v.object({
        content: v.object({
          "application/json": v.object({
            schema: v.object({
              properties: v.object({
                docs: v.object({
                  items: v.object({ $ref: v.pipe(v.string(), v.regex(/^#\/components\/schemas\/[^/]+$/)) }),
                }),
              }),
            }),
          }),
        }),
      }),
    }),
  }),
});
export const metadataReadSchema = v.object({
  properties: v.pipe(objectInput, v.record(v.string(), objectInput)),
});

/**
 * A document schema must be an object schema: the adapter narrows it to the selected fields with
 * `v.pick` before it validates a response.
 */
export type ApiV2DocumentSchema<TDocument> = v.ObjectSchema<v.ObjectEntries, undefined> &
  v.GenericSchema<unknown, TDocument>;

export const runtimeFieldSchema = v.object({
  id: v.pipe(v.string(), v.minLength(1)),
  selector: v.pipe(v.string(), v.minLength(1)),
  label: v.string(),
  type: v.picklist(runtimeFieldValueTypes),
  jsonType: v.picklist(["string", "number", "array"]),
  operators: v.array(filterOperator),
  supportsWildcards: v.boolean(),
  columnSelectable: v.boolean(),
  sortable: v.boolean(),
  source: v.object({ id: v.string(), label: v.string() }),
  options: v.array(v.string()),
});

export const catalogSchema = v.object({
  fields: v.array(runtimeFieldSchema),
  totalFields: v.optional(v.number()),
  hasMore: v.boolean(),
  page: v.number(),
  limit: v.number(),
});

export const runtimeFieldValuesSchema = v.record(
  v.string(),
  v.nullable(v.union([v.string(), v.number(), v.array(v.string())])),
);

export function createApiV2ResponseSchema<TDocument>(
  documentSchema: ApiV2DocumentSchema<TDocument>,
  runtimeFields: readonly ApiV2RuntimeFieldNamespace[],
): ApiV2DocumentSchema<TDocument> {
  return (
    runtimeFields.length === 0
      ? documentSchema
      : v.object({
          ...documentSchema.entries,
          ...Object.fromEntries(
            runtimeFields
              .filter((namespace) => namespace.responseField !== "")
              .map((namespace) => [namespace.responseField, v.optional(runtimeFieldValuesSchema)]),
          ),
        })
  ) as ApiV2DocumentSchema<TDocument>;
}
