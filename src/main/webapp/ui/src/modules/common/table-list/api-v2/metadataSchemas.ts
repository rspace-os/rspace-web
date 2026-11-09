import * as v from "valibot";
import { relationshipFilterPickerSchema } from "../../collection/relationshipFilterPicker.ts";
import { sortRuleFromToken } from "../utils/sortRuleFromToken.ts";
import { filterOperatorSchema, objectInput } from "./schemaPrimitives.ts";

// The schemas `scripts/api-v2-valibot/tableMetadata.ts` validates each collection's OpenAPI metadata with. They import
// only valibot and node-safe relative modules, so the generator can load them under plain `node`; the app imports
// them only as types (`metadata.ts`).

const positiveInteger = v.pipe(v.number(), v.integer(), v.minValue(1));
const nonNegativeInteger = v.pipe(v.number(), v.integer(), v.minValue(0));
const strings = v.array(v.string());
const scalarType = v.picklist(["string", "integer", "number", "boolean"]);
const jsonType = v.picklist(["string", "integer", "number", "boolean", "object", "array"]);
const declaredTypes = v.union([
  jsonType,
  v.strictTuple([jsonType]),
  v.strictTuple([jsonType, v.literal("null")]),
  v.strictTuple([v.literal("null"), jsonType]),
]);

function nonNullType(type: v.InferOutput<typeof declaredTypes>): v.InferOutput<typeof jsonType> {
  if (typeof type === "string") return type;
  if (type[0] === "null") return type[1];
  return type[0];
}

const declaredType = v.pipe(declaredTypes, v.transform(nonNullType));
const noComposition = {
  anyOf: v.optional(v.never()),
  allOf: v.optional(v.never()),
  $ref: v.optional(v.never()),
};
const typedEntries = { ...noComposition, format: v.optional(v.string()), oneOf: v.optional(v.never()) };
const scalarSchema = v.object({ ...typedEntries, type: scalarType });

function primitiveType(type: v.InferOutput<typeof jsonType>, format?: string) {
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

export const fieldTypeSchema = v.pipe(
  v.union([
    v.object({ ...typedEntries, type: declaredType }),
    v.object({ ...noComposition, type: v.optional(v.never()), oneOf: v.pipe(v.array(scalarSchema), v.nonEmpty()) }),
  ]),
  v.transform((schema) => {
    if (schema.oneOf !== undefined) {
      const types = schema.oneOf.map((variant) => primitiveType(variant.type, variant.format));
      return types.reduce((type, next) => (type === next ? type : "text"));
    }
    return primitiveType(schema.type, schema.format);
  }),
);

const selectorName = v.pipe(v.string(), v.regex(/^[A-Za-z][A-Za-z0-9_-]*(?:\.[A-Za-z][A-Za-z0-9_-]*)?$/));
const selectorSchema = v.pipe(
  v.object({
    schema: fieldTypeSchema,
    operators: v.array(filterOperatorSchema),
    wildcards: v.boolean(),
    title: v.optional(v.string()),
    viaTitle: v.optional(v.string()),
    picker: v.optional(relationshipFilterPickerSchema),
  }),
  v.transform(({ schema, ...selector }) => ({ ...selector, fieldType: schema })),
);

export const metadataSortSchema = v.object({
  fields: strings,
  default: v.array(
    v.pipe(
      v.string(),
      v.transform((token) => sortRuleFromToken<Record<string, unknown>>(token)),
    ),
  ),
  maximumFields: positiveInteger,
});
export const metadataFilterSchema = v.object({
  maximumComparisons: positiveInteger,
  maximumLikeComparisons: positiveInteger,
  maximumNesting: positiveInteger,
  maximumArguments: positiveInteger,
  selectors: v.pipe(
    objectInput,
    v.record(selectorName, selectorSchema),
    v.rawCheck(({ dataset, addIssue }) => {
      if (!dataset.typed) return;
      for (const [name, selector] of Object.entries(dataset.value)) {
        if (selector.picker && !/^[A-Za-z][A-Za-z0-9_]*$/.test(name)) {
          addIssue({ message: `Filter selector ${name} has an invalid picker` });
        }
      }
    }),
  ),
});

export const metadataWhereSchema = v.object({ maxLength: positiveInteger });
export const metadataLimitSchema = v.object({ default: positiveInteger, maximum: positiveInteger });
export const metadataAllowedFieldsSchema = v.pipe(objectInput, v.record(v.string(), strings));

export const runtimeNamespaceSchema = v.object({
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
  viaResource: v.optional(v.string()),
});
export const metadataRuntimeFieldsSchema = v.optional(
  v.pipe(
    v.array(runtimeNamespaceSchema),
    v.checkItems(
      (item, index, items) => items.findIndex((other) => other.namespace === item.namespace) === index,
      (issue) => `Duplicate runtime field namespace ${issue.input.namespace}`,
    ),
  ),
  () => [],
);

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
