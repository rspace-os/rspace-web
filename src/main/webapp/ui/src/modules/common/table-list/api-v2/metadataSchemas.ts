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
