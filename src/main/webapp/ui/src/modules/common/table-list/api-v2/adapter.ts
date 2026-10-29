import * as v from "valibot";
import type {
  CollectionConfig,
  FieldConfig,
  FieldName,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { isIdLikeField, isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import i18n from "@/modules/common/i18n";
import { formatList } from "@/modules/common/i18n/listFormat";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import type { CollectionPage, CollectionQueryState } from "../types";
import {
  createApiV2FilterFields,
  isRelationshipWireSelector,
  type RuntimeFieldCatalogForNamespace,
} from "./filterFields";
import type { ApiV2CollectionMetadata } from "./metadata";
import { collectionQueryParams, selectedFields } from "./queryParams";
import { type ApiV2DocumentSchema, createApiV2ResponseSchema, objectInput } from "./schema";

export type { ApiV2DocumentSchema } from "./schema";

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
  /** Formats multi-value cells as a list in this language. */
  language?: string;
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
    if (isRelationshipWireSelector(selector)) {
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
  translate = (key, values) =>
    key === "common:tableList.error.customFieldColumnLimit"
      ? i18n.t("common:tableList.error.customFieldColumnLimit", { limit: Number(values?.limit), lng: language })
      : key,
  language,
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
  const filterFields = createApiV2FilterFields({
    config: sourceConfig,
    metadata,
    runtimeFields,
    translate,
  });
  const relationshipNames = filterFields.sourceFields
    .filter((field) => field.type === "relationship")
    .map((field) => field.name);
  const projectableRelationships = new Set(relationshipNames.filter((name) => name in documentSchema.entries));
  const relationshipFields = new Set(
    filterFields.relationshipFields
      .filter(({ owner }) => projectableRelationships.has(owner))
      .map(({ field }) => field.name),
  );
  const runtimeSelectors = filterFields.runtimeSelectors;
  const runtimeNamespaces = metadata.runtimeFields ?? [];
  const projectableFields = new Set(Object.keys(documentSchema.entries) as FieldName<TDocument>[]);
  const fields: FieldConfig<TDocument>[] = [
    ...filterFields.sourceFields,
    ...filterFields.relationshipFields.map(({ field, owner, targetField }) =>
      projectableRelationships.has(owner)
        ? {
            ...field,
            list: {
              dependencies: [owner],
              renderCell: ({ row }: { row: TDocument }) => targetValue(row, owner, targetField, language),
            },
          }
        : { ...field, list: false as const },
    ),
    ...filterFields.runtimeFields.map(({ field, namespace, responseField, definition }) => ({
      ...field,
      list: namespace.columnSelectable
        ? {
            description: definition.source.label,
            renderCell: ({ row }: { row: TDocument }) =>
              runtimeValue(row, responseField, definition.id, definition.type === "number", language),
          }
        : (false as const),
    })),
  ];
  const configInput: CollectionConfig<TDocument> = {
    ...sourceConfig,
    fields,
    defaultSort: sourceConfig.defaultSort ?? metadata.sorting.default,
    runtimeNamespaces: runtimeNamespaces.map((namespace) => namespace.namespace),
  };
  const config = {
    ...resolveCollectionConfig(configInput),
    runtimeSources: filterFields.runtimeSources,
  };
  const responseSchema = createApiV2ResponseSchema(documentSchema, runtimeNamespaces);
  const isRuntimeSelector = (name: string) =>
    runtimeNamespaces.some((namespace) => isRuntimeFieldInNamespace(name, namespace.namespace));
  const runtimeProjection = (state: CollectionQueryState<TDocument>) =>
    state.visibleFields.filter((name) => runtimeSelectors.has(name)).map(String);
  const catalogSelectors = filterFields.catalogSelectors;

  return {
    config,
    metadata,
    isRuntimeSelector,
    selectedFields: (state) => {
      const selected = selectedFields(state, config, filterFields.virtualFields, projectableFields);
      const projection = runtimeProjection(state);
      const namespaces = runtimeNamespaces
        .filter((namespace) => projection.some((name) => isRuntimeFieldInNamespace(name, namespace.namespace)))
        .map((namespace) => namespace.responseField as FieldName<TDocument>);
      return namespaces.length === 0 ? selected : [...selected, ...namespaces];
    },
    requiredDepth: (state) => (state.visibleFields.some((field) => relationshipFields.has(field)) ? 1 : 0),
    toSearchParams: (state) =>
      collectionQueryParams(state, config, metadata, filterFields.virtualFields, projectableFields, {
        projection: runtimeProjection(state),
        selectors: catalogSelectors,
        projectionLimitMessage: (limit) => translate("common:tableList.error.customFieldColumnLimit", { limit }),
      }),
    // A request selects the visible fields only, so a response omits every other field. Validate the
    // same projection that the request asked for: an omitted selected field stays an error, and an
    // unselected field cannot fail. The row type still names every field, because column visibility
    // is run-time state that a static type cannot narrow.
    parseResponse: (input, selected) => {
      const projection = v.pick(responseSchema, selected as unknown as [string, ...string[]]);
      const result = parseOrThrow(v2ListEnvelope(projection), input);
      const rows = withExpandedTargetFields(result.docs as TDocument[], input, relationshipNames.map(String));
      return { rows, rowCount: result.totalDocs };
    },
  };
}

// Several values of one field, not a sentence: "a, b, c" rather than "a, b, and c".
const VALUE_LIST: Intl.ListFormatOptions = { type: "unit", style: "short" };

function runtimeValue<TDocument>(
  row: TDocument,
  responseField: FieldName<TDocument>,
  id: string,
  numeric: boolean,
  language: string | undefined,
): string {
  const values = row[responseField];
  if (!v.is(objectInput, values)) return "";
  const value = values[id];
  if (value === null || value === undefined) return "";
  const format = (item: unknown) => (numeric && typeof item === "number" ? formatNumber(item, language) : String(item));
  return Array.isArray(value) ? formatList(value.map(format), language, VALUE_LIST) : format(value);
}

// Restore expanded display keys stripped by the page schema.
// These extras are unvalidated, so parsed values must override raw equivalents.
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

const expandedTargetSchema = v.pipe(objectInput, v.object({ value: objectInput }));

function expandedTarget(relationship: unknown): Record<string, unknown> | null {
  const result = v.safeParse(expandedTargetSchema, relationship);
  return result.success ? result.output.value : null;
}

function targetValue<TDocument>(
  row: TDocument,
  owner: FieldName<TDocument>,
  targetField: string,
  language: string | undefined,
): string {
  const value = expandedTarget(row[owner]);
  if (!value) return "";
  const fieldValue = value[targetField];
  if (typeof fieldValue === "boolean")
    return fieldValue
      ? i18n.t("common:actions.yes", { lng: language })
      : i18n.t("common:actions.no", { lng: language });
  if (typeof fieldValue === "number" && targetField !== "id" && !isIdLikeField(targetField))
    return formatNumber(fieldValue, language);
  return fieldValue === null || fieldValue === undefined ? "" : String(fieldValue);
}
