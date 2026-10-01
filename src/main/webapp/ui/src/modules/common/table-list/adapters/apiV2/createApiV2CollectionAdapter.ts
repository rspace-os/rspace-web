import * as v from "valibot";
import type {
  CollectionConfig,
  FieldConfig,
  FieldName,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import type { CollectionPage, CollectionQueryState } from "../../tableListState";
import type { ApiV2CollectionMetadata } from "./apiV2CollectionMetadata";
import { createApiV2FilterFields, type RuntimeFieldCatalogForNamespace } from "./apiV2FilterFields";
import { collectionQueryParams, selectedFields } from "./collectionQueryParams";
import { runtimeFieldValuesSchema } from "./runtimeFieldCatalog";

export type { RuntimeFieldCatalogForNamespace } from "./apiV2FilterFields";

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
  isRuntimeSelector: (name: string) => boolean;
};

export type ApiV2CollectionDefinition<TDocument> = {
  config: CollectionConfig<TDocument>;
  documentSchema: ApiV2DocumentSchema<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
  translate?: (key: string, values?: Record<string, unknown>) => string;
};

export function staleRuntimeFields<TDocument>(
  names: readonly string[],
  adapter: ApiV2CollectionAdapter<TDocument>,
): readonly string[] {
  const known = new Set(adapter.config.fields.map((field) => String(field.name)));
  return names.filter((name) => adapter.isRuntimeSelector(name) && !known.has(name));
}

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
    const published = metadata.filtering.selectors[selector] ?? metadata.relationshipFields?.[selector];
    if (!published) throw new Error(`Searchable field is not filterable: ${selector}`);
    const targetField = selector.slice(selector.indexOf(".") + 1);
    if (selector.includes(".") && (targetField === "value" || targetField === "relationTo")) {
      throw new Error(`Relationship wire field is not searchable: ${selector}`);
    }
    if (targetField === "globalId") {
      if (published.fieldType !== undefined && published.fieldType !== "text") {
        throw new Error(`Searchable field must be text: ${selector}`);
      }
      continue;
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
  runtimeFields = [],
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
  const relationships = new Map(
    sourceConfig.fields.filter((field) => field.type === "relationship").map((field) => [String(field.name), field]),
  );
  const filterFields = createApiV2FilterFields({
    config: sourceConfig,
    metadata,
    runtimeFields,
    translate,
  });
  const relationshipFields = new Set(filterFields.relationshipFields.map(({ field }) => field.name));
  const runtimeSelectors = filterFields.runtimeSelectors;
  const projectableFields = new Set(Object.keys(documentSchema.entries) as FieldName<TDocument>[]);
  const fields: FieldConfig<TDocument>[] = [
    ...filterFields.sourceFields,
    ...filterFields.relationshipFields.map(({ field, owner, targetField }) => ({
      ...field,
      list: {
        dependencies: [owner],
        renderCell: ({ row }: { row: TDocument }) => targetValue(row, owner, targetField),
      },
    })),
    ...filterFields.runtimeFields.map(({ field, namespace, responseField, definition }) => ({
      ...field,
      list: namespace.columnSelectable
        ? {
            description: definition.source.label,
            renderCell: ({ row }: { row: TDocument }) => runtimeValue(row, responseField, definition.id),
          }
        : (false as const),
    })),
  ];
  const configInput: CollectionConfig<TDocument> = {
    ...sourceConfig,
    fields,
    defaultSort: sourceConfig.defaultSort ?? metadata.sorting.default,
    runtimeNamespaces: (metadata.runtimeFields ?? []).map((namespace) => namespace.namespace),
  };
  const config = {
    ...resolveCollectionConfig(configInput),
    runtimeSources: filterFields.runtimeSources,
  };
  const responseSchema = (
    (metadata.runtimeFields ?? []).length === 0
      ? documentSchema
      : v.object({
          ...documentSchema.entries,
          ...Object.fromEntries(
            (metadata.runtimeFields ?? [])
              .filter((namespace) => namespace.responseField !== "")
              .map((namespace) => [namespace.responseField, v.optional(runtimeFieldValuesSchema)]),
          ),
        })
  ) as ApiV2DocumentSchema<TDocument>;
  const isRuntimeSelector = (name: string) =>
    (metadata.runtimeFields ?? []).some((namespace) => name.startsWith(`${namespace.namespace}.`));
  const runtimeProjection = (state: CollectionQueryState<TDocument>) =>
    state.visibleFields.filter((name) => runtimeSelectors.has(name)).map(String);
  const catalogSelectors = filterFields.catalogSelectors;

  return {
    config,
    metadata,
    isRuntimeSelector,
    selectedFields: (state) => {
      const selected = selectedFields(state, config, filterFields.virtualFields, projectableFields);
      const namespaces = (metadata.runtimeFields ?? [])
        .filter((namespace) =>
          state.visibleFields.some(
            (field) => runtimeSelectors.has(field) && String(field).startsWith(`${namespace.namespace}.`),
          ),
        )
        .map((namespace) => namespace.responseField as FieldName<TDocument>);
      return namespaces.length === 0 ? selected : [...selected, ...namespaces];
    },
    requiredDepth: (state) => (state.visibleFields.some((field) => relationshipFields.has(field)) ? 1 : 0),
    toSearchParams: (state) =>
      collectionQueryParams(state, config, metadata, filterFields.virtualFields, projectableFields, {
        projection: runtimeProjection(state),
        selectors: catalogSelectors,
        projectionLimitMessage: (limit) => translate("tableList.error.customFieldColumnLimit", { limit }),
      }),
    // A request selects the visible fields only, so a response omits every other field. Validate the
    // same projection that the request asked for: an omitted selected field stays an error, and an
    // unselected field cannot fail. The row type still names every field, because column visibility
    // is run-time state that a static type cannot narrow.
    parseResponse: (input, selected) => {
      const projection = v.pick(responseSchema, selected as unknown as [string, ...string[]]);
      const result = parseOrThrow(v2ListEnvelope(projection), input);
      const rows = withExpandedTargetFields(result.docs as TDocument[], input, [...relationships.keys()]);
      return { rows, rowCount: result.totalDocs };
    },
  };
}

function runtimeValue<TDocument>(row: TDocument, responseField: FieldName<TDocument>, id: string): string {
  const values = row[responseField];
  if (typeof values !== "object" || values === null || Array.isArray(values)) return "";
  const value = (values as Record<string, unknown>)[id];
  if (value === null || value === undefined) return "";
  return Array.isArray(value) ? value.join(", ") : String(value);
}

function withExpandedTargetFields<TDocument>(
  parsed: TDocument[],
  input: unknown,
  relationshipNames: readonly string[],
): TDocument[] {
  if (relationshipNames.length === 0) return parsed;
  const rawDocs = (input as { docs?: unknown })?.docs;
  if (!Array.isArray(rawDocs)) return parsed;
  return parsed.map((document, index) => {
    const raw = rawDocs[index];
    if (typeof raw !== "object" || raw === null) return document;
    let restored = document;
    for (const name of relationshipNames) {
      const rawValue = expandedTarget((raw as Record<string, unknown>)[name]);
      const parsedRelationship = (restored as Record<string, unknown>)[name];
      const parsedValue = expandedTarget(parsedRelationship);
      if (!rawValue || !parsedValue) continue;
      restored = {
        ...restored,
        [name]: { ...(parsedRelationship as Record<string, unknown>), value: { ...rawValue, ...parsedValue } },
      };
    }
    return restored;
  });
}

function expandedTarget(relationship: unknown): Record<string, unknown> | null {
  if (typeof relationship !== "object" || relationship === null || Array.isArray(relationship)) return null;
  const value = (relationship as Record<string, unknown>).value;
  if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function targetValue<TDocument>(row: TDocument, owner: FieldName<TDocument>, targetField: string): string {
  const relationship = row[owner];
  if (typeof relationship !== "object" || relationship === null || Array.isArray(relationship)) return "";
  const value = (relationship as Record<string, unknown>).value;
  if (typeof value !== "object" || value === null || Array.isArray(value)) return "";
  const fieldValue = (value as Record<string, unknown>)[targetField];
  return fieldValue === null || fieldValue === undefined ? "" : String(fieldValue);
}
