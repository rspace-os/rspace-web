import * as v from "valibot";
import type { FieldType } from "../../collection/fieldTypes.ts";
import { relationshipFilterPickerSchema } from "../../collection/relationshipFilterPicker.ts";
import { sortRuleFromToken } from "../utils/sortRuleFromToken.ts";
import { filterOperatorSchema, objectInput } from "./schemaPrimitives.ts";

// The schemas `scripts/api-v2-valibot/tableMetadata.ts` validates each collection's OpenAPI metadata with. They import
// only valibot and node-safe relative modules, so the generator can load them under plain `node`; the app imports
// them only as types (`metadata.ts`).

const positiveInteger = v.pipe(v.number(), v.integer(), v.minValue(1));
const nonNegativeInteger = v.pipe(v.number(), v.integer(), v.minValue(0));
const strings = v.array(v.string());
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

/** A declared JSON type, with an optional `null` alternative removed. */
const declaredType = v.pipe(declaredTypes, v.transform(nonNullType));

/** The table-list field type of each value kind `scripts/api-v2-valibot/filters.ts` classifies a selector as. */
const fieldTypes = {
  string: "text",
  number: "number",
  boolean: "boolean",
  dateTime: "dateTime",
  enum: "text",
} as const satisfies Record<string, FieldType>;
export type ApiV2PrimitiveFieldType = (typeof fieldTypes)[keyof typeof fieldTypes];

// A selector of the generator's filter catalog, as the table list reads it.
const selectorSchema = v.pipe(
  v.object({
    kind: v.picklist(Object.keys(fieldTypes) as (keyof typeof fieldTypes)[]),
    values: v.optional(v.array(v.union([v.string(), v.number()]))),
    operators: v.array(filterOperatorSchema),
    wildcards: v.boolean(),
    picker: v.optional(relationshipFilterPickerSchema),
  }),
  v.transform(({ kind, values, picker, ...selector }) => ({
    // An enum of numbers still compares as a number.
    fieldType: kind === "enum" && values?.every((value) => typeof value === "number") ? "number" : fieldTypes[kind],
    ...selector,
    ...(picker ? { picker } : {}),
    ...(values ? { values } : {}),
  })),
);
export const metadataSelectorsSchema = v.pipe(
  v.record(v.string(), selectorSchema),
  v.rawCheck(({ dataset, addIssue }) => {
    if (!dataset.typed) return;
    for (const [name, selector] of Object.entries(dataset.value)) {
      if (selector.picker && !/^[A-Za-z][A-Za-z0-9_]*$/.test(name)) {
        addIssue({ message: `Filter selector ${name} has an invalid picker` });
      }
    }
  }),
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

const rowBoundEntries = { minItems: v.optional(nonNegativeInteger), maxItems: v.optional(nonNegativeInteger) };
export const metadataRowBoundsSchema = v.pipe(
  v.object(rowBoundEntries),
  v.forward(
    v.check(
      ({ minItems, maxItems }) => minItems === undefined || maxItems === undefined || minItems <= maxItems,
      "Invalid row bounds",
    ),
    ["maxItems"],
  ),
  v.transform(({ minItems, maxItems }) => ({
    ...(minItems === undefined ? {} : { minRows: minItems }),
    ...(maxItems === undefined ? {} : { maxRows: maxItems }),
  })),
);
const enumValues = v.pipe(strings, v.nonEmpty());
const writeConstraints = v.pipe(
  objectInput,
  v.looseObject({
    ...rowBoundEntries,
    maxLength: v.optional(nonNegativeInteger),
    enum: v.optional(enumValues),
  }),
);
const childEntries = {
  name: v.pipe(v.string(), v.regex(/\S/, "Child name must not be empty")),
  type: v.object({
    jsonType,
    format: v.nullish(v.string()),
    maxLength: v.nullish(nonNegativeInteger),
    structure: v.pipe(objectInput, v.looseObject({ enum: v.optional(enumValues) })),
  }),
  writeConstraints,
  requiredOnCreate: v.boolean(),
  nullable: v.boolean(),
  readOnly: v.boolean(),
  defaultValue: v.optional(v.unknown()),
  openApi: v.pipe(
    objectInput,
    v.object({
      title: v.nullish(v.string()),
      format: v.nullish(v.string()),
    }),
  ),
};
export type ApiV2MetadataChild = v.InferOutput<v.ObjectSchema<typeof childEntries, undefined>> & {
  fields: ApiV2MetadataChild[];
};
const metadataChildSchema: v.GenericSchema<unknown, ApiV2MetadataChild> = v.object({
  ...childEntries,
  fields: v.lazy(() => metadataChildListSchema),
});
// Names identify children, so a list may not repeat one.
const metadataChildListSchema = v.pipe(
  v.array(metadataChildSchema),
  v.checkItems(
    (item, index, items) => items.findIndex((other) => other.name === item.name) === index,
    (issue) => `duplicate child field ${issue.input.name}`,
  ),
);
export const metadataStructuredPropertySchema = v.object({
  type: declaredType,
  readOnly: v.optional(v.boolean(), false),
  "x-rspace-fields": v.pipe(metadataChildListSchema, v.nonEmpty()),
});
