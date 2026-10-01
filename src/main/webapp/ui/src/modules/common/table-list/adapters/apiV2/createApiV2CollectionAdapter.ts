import * as v from "valibot";
import type {
  CollectionConfig,
  FieldConfig,
  FieldName,
  FilterOperator,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/collectionConfig";
import { fieldLabel, hierarchicalFieldLabel } from "@/modules/common/collection/collectionConfig";
import {
  isFilterOperatorCompatible,
  resolveCollectionConfig,
} from "@/modules/common/collection/resolveCollectionConfig";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import type { CollectionPage, CollectionQueryState } from "../../tableListState";
import type { ApiV2CollectionMetadata, ApiV2FilterOperator } from "./apiV2CollectionMetadata";
import { collectionQueryParams, selectedFields } from "./collectionQueryParams";
import { type RuntimeFieldDefinition, runtimeFieldValuesSchema } from "./runtimeFieldCatalog";

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
  isRuntimeSelector: (name: string) => boolean;
};

export type ApiV2CollectionDefinition<TDocument> = {
  config: CollectionConfig<TDocument>;
  documentSchema: ApiV2DocumentSchema<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
  translate?: (key: string, values?: Record<string, unknown>) => string;
};

export type RuntimeFieldCatalogForNamespace = {
  namespace: string;
  definitions: readonly RuntimeFieldDefinition[];
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
  // Derived fields go through the same narrowing, so their operators and wildcard rule come
  // from the published selector rather than from the defaults for a text field.
  const relationships = new Map(
    sourceConfig.fields.filter((field) => field.type === "relationship").map((field) => [String(field.name), field]),
  );
  const derived = derivedTargetFields(sourceConfig, metadata, translate);
  const runtime = derivedRuntimeFields(metadata, runtimeFields, relationshipLabels(sourceConfig, translate));
  const relationshipFields = new Set(derived.map(({ field }) => field.name));
  const runtimeSelectors = new Map(
    runtime.map(({ field, operators, supportsWildcards }) => [field.name, { operators, supportsWildcards }] as const),
  );
  const virtualFields = new Set([...relationshipFields, ...runtimeSelectors.keys()]);
  const projectableFields = new Set(Object.keys(documentSchema.entries) as FieldName<TDocument>[]);
  const fields = [
    ...sourceConfig.fields,
    ...derived.map(({ field }) => field),
    ...runtime.map(({ field }) => field),
  ].map((field) => {
    const runtimeOperators = runtimeSelectors.get(field.name);
    if (runtimeOperators) {
      return {
        ...field,
        capabilities: {
          sortable: false,
          filterOperators: runtimeOperators.operators.filter((operator) =>
            isFilterOperatorCompatible(field.type, operator),
          ),
          supportsWildcards: runtimeOperators.supportsWildcards,
        },
      };
    }
    const selector = metadata.filtering.selectors[field.name] ?? metadata.relationshipFields?.[String(field.name)];
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
    runtimeNamespaces: (metadata.runtimeFields ?? []).map((namespace) => namespace.namespace),
  };
  const config = {
    ...resolveCollectionConfig(configInput),
    runtimeSources: (metadata.runtimeFields ?? [])
      .filter((namespace) => namespace.filterable || namespace.columnSelectable)
      .map((namespace) => ({
        namespace: namespace.namespace,
        viaLabel:
          namespace.via === "" ? "" : (relationshipLabels(sourceConfig, translate).get(namespace.via) ?? namespace.via),
        catalog: namespace.catalog,
        maximumLimit: namespace.catalogMaximumLimit,
        filterable: namespace.filterable,
        columnSelectable: namespace.columnSelectable,
      })),
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
  const catalogSelectors = Object.fromEntries(
    runtime.map(({ field, wireOperators, supportsWildcards }) => [
      String(field.name),
      { operators: wireOperators, wildcards: supportsWildcards },
    ]),
  ) as ApiV2CollectionMetadata<TDocument>["filtering"]["selectors"];

  return {
    config,
    metadata,
    isRuntimeSelector,
    selectedFields: (state) => {
      const selected = selectedFields(state, config, virtualFields, projectableFields);
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
      collectionQueryParams(state, config, metadata, virtualFields, projectableFields, {
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

/**
 * Optional list fields for each target selector that no configured field covers.
 *
 *
 * <p>The name is a selector such as `target.name`, which is not a key of the document. That is why
 * these fields read their value through the relationship field. They stay out of forms because
 * they are not fields of the source document. The cast is confined to this function.
 */
function relationshipLabels<TDocument>(
  sourceConfig: CollectionConfig<TDocument>,
  translate: (key: string) => string,
): ReadonlyMap<string, string> {
  return new Map(
    sourceConfig.fields
      .filter((field) => field.type === "relationship")
      .map((field) => [String(field.name), fieldLabel(field, translate)] as const),
  );
}

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
  return Object.entries(metadata.relationshipFields ?? {}).flatMap(([selector, published]) => {
    const dot = selector.indexOf(".");
    const relationship = relationships.get(selector.slice(0, dot));
    const targetField = selector.slice(dot + 1);
    if (declared.has(selector) || !relationship) {
      return [];
    }
    if (published.fieldType === null) return [];
    const name = selector as FieldName<TDocument>;
    const owner = relationship.name;
    const viaLabel = fieldLabel(relationship, translate);
    const common = {
      name,
      labelKey: selector,
      label: hierarchicalFieldLabel(viaLabel, published.title ?? targetField),
      origin: {
        kind: "relationshipTarget" as const,
        groupLabelKey: "tableList.fieldGroups.relationshipFields",
        viaLabel,
      },
      list: {
        dependencies: [owner],
        renderCell: ({ row }: { row: TDocument }) => targetValue(row, owner, targetField),
      },
      form: false as const,
    };
    const field = (() => {
      switch (published.fieldType) {
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

function derivedRuntimeFields<TDocument>(
  metadata: ApiV2CollectionMetadata<TDocument>,
  catalog: readonly RuntimeFieldCatalogForNamespace[],
  viaLabels: ReadonlyMap<string, string>,
): {
  field: FieldConfig<TDocument>;
  operators: readonly FilterOperator[];
  wireOperators: readonly ApiV2FilterOperator[];
  supportsWildcards: boolean;
}[] {
  const namespaces = new Map((metadata.runtimeFields ?? []).map((namespace) => [namespace.namespace, namespace]));
  return catalog.flatMap((entry) =>
    entry.definitions.flatMap((definition) => {
      const namespace = namespaces.get(entry.namespace);
      if (!namespace) return [];
      const responseField = namespace.responseField as FieldName<TDocument>;
      const name = (
        namespace.via === "" ? definition.selector : `${namespace.via}.${definition.selector}`
      ) as FieldName<TDocument>;
      const viaLabel = namespace.via === "" ? "" : (viaLabels.get(namespace.via) ?? namespace.via);
      const common = {
        name,
        labelKey: definition.selector,
        label: hierarchicalFieldLabel(viaLabel, definition.label),
        origin: {
          kind: "runtimeField" as const,
          groupLabelKey: "tableList.fieldGroups.customFields",
          sourceLabel: definition.source.label,
          stableId: definition.id,
          namespace: namespace.namespace,
          viaLabel,
        },
        list: namespace.columnSelectable
          ? {
              description: definition.source.label,
              renderCell: ({ row }: { row: TDocument }) => runtimeValue(row, responseField, definition.id),
            }
          : (false as const),
        form: false as const,
      };
      const field = (
        definition.options.length > 0
          ? { ...common, type: "select" as const, options: [...definition.options] }
          : definition.type === "number"
            ? { ...common, type: "number" as const }
            : { ...common, type: "text" as const }
      ) satisfies FieldConfig<TDocument>;
      const wireOperators = namespace.filterable ? definition.operators : [];
      return [
        {
          field,
          operators: wireOperators.map((operator) => semanticOperators[operator]),
          wireOperators,
          supportsWildcards: definition.supportsWildcards,
        },
      ];
    }),
  );
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
