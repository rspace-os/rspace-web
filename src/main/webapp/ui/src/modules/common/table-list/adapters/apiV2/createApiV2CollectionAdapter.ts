import * as v from "valibot";
import type {
  CollectionConfig,
  FieldConfig,
  FieldName,
  FilterOperator,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/collectionConfig";
import {
  isFilterOperatorCompatible,
  resolveCollectionConfig,
} from "@/modules/common/collection/resolveCollectionConfig";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import type { CollectionPage, CollectionQueryState } from "../../tableListState";
import type { ApiV2CollectionMetadata, ApiV2FilterOperator } from "./apiV2CollectionMetadata";
import { collectionQueryParams, selectedFields } from "./collectionQueryParams";

const semanticOperators: Record<ApiV2FilterOperator, FilterOperator> = {
  "==": "equals",
  "!=": "notEquals",
  "=gt=": "greaterThan",
  "=ge=": "greaterThanOrEqual",
  "=lt=": "lessThan",
  "=le=": "lessThanOrEqual",
  "=in=": "in",
  "=out=": "notIn",
  "=contains=": "contains",
  "=like=": "matches",
  "=exists=": "exists",
};

/**
 * A document schema must be an object schema: the adapter narrows it to the selected fields with
 * `v.pick` before it validates a response.
 */
export type ApiV2DocumentSchema<TDocument> = v.ObjectSchema<v.ObjectEntries, undefined> &
  v.GenericSchema<unknown, TDocument>;

export type ApiV2CollectionAdapter<TDocument> = {
  config: ResolvedCollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  selectedFields: (state: CollectionQueryState<TDocument>) => readonly FieldName<TDocument>[];
  requiredDepth: (state: CollectionQueryState<TDocument>) => number;
  toSearchParams: (state: CollectionQueryState<TDocument>) => URLSearchParams;
  parseResponse: (input: unknown, selected: readonly FieldName<TDocument>[]) => CollectionPage<TDocument>;
};

export type ApiV2CollectionDefinition<TDocument> = {
  config: CollectionConfig<TDocument>;
  documentSchema: ApiV2DocumentSchema<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  translate?: (key: string) => string;
};

function validateSearchSelectors<TDocument>(
  config: CollectionConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
): void {
  const selectors = config.listSearchableFields ?? [];
  const limit = Math.min(
    metadata.filtering.limits.maximumComparisons,
    metadata.filtering.limits.maximumLikeComparisons,
  );
  if (selectors.length > limit) throw new Error(`Search field limit exceeded: maximum ${limit}`);
  for (const name of selectors) {
    const selector = String(name);
    const published = metadata.filtering.selectors[selector];
    if (!published) throw new Error(`Searchable field is not filterable: ${selector}`);
    const targetField = selector.slice(selector.indexOf(".") + 1);
    if (selector.includes(".") && (targetField === "value" || targetField === "relationTo")) {
      throw new Error(`Relationship wire field is not searchable: ${selector}`);
    }
    if (!published.operators.includes("=contains=")) {
      throw new Error(`Searchable field does not support contains: ${selector}`);
    }
    if (published.fieldType !== undefined && published.fieldType !== "text") {
      throw new Error(`Searchable field must be text: ${selector}`);
    }
  }
}

export function createApiV2CollectionAdapter<TDocument>({
  config: sourceConfig,
  documentSchema,
  metadata,
  translate = (key) => key,
}: ApiV2CollectionDefinition<TDocument>): ApiV2CollectionAdapter<TDocument> {
  const allowedFields = new Set(metadata.fields);
  for (const field of sourceConfig.fields) {
    if (!allowedFields.has(field.name)) throw new Error(`Collection config references unknown API field ${field.name}`);
    // The response projection picks these names out of the schema, so a name that the schema does
    // not carry must fail here rather than inside the parser.
    if (!(field.name in documentSchema.entries)) {
      throw new Error(`Collection config references field ${field.name}, which the document schema does not declare`);
    }
  }
  validateSearchSelectors(sourceConfig, metadata);
  // Derived fields go through the same narrowing, so their operators and wildcard rule come
  // from the published selector rather than from the defaults for a text field.
  const derived = derivedTargetFields(sourceConfig, metadata, translate);
  const virtualFields = new Set(derived.map(({ field }) => field.name));
  const fields = [...sourceConfig.fields, ...derived.map(({ field }) => field)].map((field) => {
    const selector = metadata.filtering.selectors[field.name];
    const filterOperators =
      selector?.operators
        .map((operator) => semanticOperators[operator])
        .filter((operator) => isFilterOperatorCompatible(field.type, operator)) ?? [];
    return {
      ...field,
      capabilities: {
        sortable: metadata.sorting.fields.includes(field.name),
        filterOperators,
        supportsWildcards: selector?.wildcards ?? false,
      },
    };
  });
  const configInput: CollectionConfig<TDocument> = {
    ...sourceConfig,
    fields,
    defaultSort: sourceConfig.defaultSort ?? metadata.sorting.default,
  };
  const config = resolveCollectionConfig(configInput);

  return {
    config,
    metadata,
    selectedFields: (state) => selectedFields(state, config, virtualFields),
    requiredDepth: (state) => (state.visibleFields.some((field) => virtualFields.has(field)) ? 1 : 0),
    toSearchParams: (state) => collectionQueryParams(state, config, metadata, virtualFields),
    // A request selects the visible fields only, so a response omits every other field. Validate the
    // same projection that the request asked for: an omitted selected field stays an error, and an
    // unselected field cannot fail. The row type still names every field, because column visibility
    // is run-time state that a static type cannot narrow.
    parseResponse: (input, selected) => {
      const projection = v.pick(documentSchema, selected as unknown as [string, ...string[]]);
      const result = parseOrThrow(v2ListEnvelope(projection), input);
      return { rows: result.docs as TDocument[], rowCount: result.totalDocs };
    },
  };
}

/**
 * Optional list fields for each target selector that no configured field covers.
 *
 * <p>A collection declares a relationship once. The server then publishes one selector for each
 * field of each target, so a new relationship becomes filterable with no change here and no change
 * to the collection config. This mirrors Payload, where declaring a relationship is enough for its
 * target's primitive fields to become queryable and available as hidden columns.
 *
 * <p>The name is a selector such as `target.name`, which is not a key of the document. That is why
 * these fields read their value through the relationship field. They stay out of forms because
 * they are not fields of the source document. The cast is confined to this function.
 */
function derivedTargetFields<TDocument>(
  sourceConfig: CollectionConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  translate: (key: string) => string,
): { field: FieldConfig<TDocument>; owner: FieldName<TDocument>; targetField: string }[] {
  const declared = new Set<string>(sourceConfig.fields.map((field) => String(field.name)));
  const relationships = new Map(
    sourceConfig.fields
      .filter((field) => field.type === "relationship")
      .map((field) => [String(field.name), field] as const),
  );
  return Object.entries(metadata.filtering.selectors).flatMap(([selector, published]) => {
    const dot = selector.indexOf(".");
    const relationship = relationships.get(selector.slice(0, dot));
    const targetField = selector.slice(dot + 1);
    // These are the relationship wire format, not fields of its target.
    if (declared.has(selector) || !relationship || targetField === "value" || targetField === "relationTo") {
      return [];
    }
    if (published?.fieldType === null) return [];
    const name = selector as FieldName<TDocument>;
    const owner = relationship.name;
    const common = {
      name,
      labelKey: `${translate(relationship.labelKey)}: ${published?.title ?? selector}`,
      list: {
        dependencies: [owner],
        renderCell: ({ row }: { row: TDocument }) => targetValue(row, owner, targetField),
      },
      form: false as const,
    };
    const field = (() => {
      switch (published?.fieldType) {
        case "number":
          return { ...common, type: "number" as const };
        case "boolean":
          return { ...common, type: "boolean" as const };
        case "dateTime":
          return { ...common, type: "dateTime" as const };
        default:
          return { ...common, type: "text" as const };
      }
    })() satisfies FieldConfig<TDocument>;
    return [{ field, owner, targetField }];
  });
}

function targetValue<TDocument>(row: TDocument, owner: FieldName<TDocument>, targetField: string): string {
  const relationship = row[owner];
  if (typeof relationship !== "object" || relationship === null || Array.isArray(relationship)) return "";
  const value = (relationship as Record<string, unknown>).value;
  if (typeof value !== "object" || value === null || Array.isArray(value)) return "";
  const fieldValue = (value as Record<string, unknown>)[targetField];
  return fieldValue === null || fieldValue === undefined ? "" : String(fieldValue);
}
