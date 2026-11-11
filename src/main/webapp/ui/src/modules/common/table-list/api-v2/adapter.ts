import * as v from "valibot";
import type {
  CollectionConfig,
  FieldConfig,
  FieldName,
  ResolvedCollectionConfig,
} from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import type { CollectionPage, CollectionQueryState } from "../types";
import {
  createApiV2FilterFields,
  isRelationshipWireSelector,
  type RuntimeFieldCatalogForNamespace,
} from "./filterFields";
import type { ApiV2CollectionMetadata } from "./metadata";
import { collectionQueryParams, selectedFields } from "./queryParams";
import { type ApiV2DocumentMapping, type ApiV2DocumentSchema, createApiV2ResponseSchema, objectInput } from "./schema";

export type { ApiV2DocumentMapping, ApiV2DocumentSchema } from "./schema";

export type ApiV2CollectionAdapter<TDocument> = {
  config: ResolvedCollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  selectedFields: (state: CollectionQueryState<TDocument>) => readonly FieldName<TDocument>[];
  requiredDepth: (state: CollectionQueryState<TDocument>) => number;
  toSearchParams: (state: CollectionQueryState<TDocument>) => URLSearchParams;
  parseResponse: (input: unknown, selected: readonly FieldName<TDocument>[]) => CollectionPage<TDocument>;
  isRuntimeSelector: (name: string) => boolean;
};

export type ApiV2CollectionDefinition<
  TDocument,
  TSchema extends ApiV2DocumentSchema<TDocument> = ApiV2DocumentSchema<TDocument>,
> = {
  config: CollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
} & ApiV2DocumentMapping<TDocument, TSchema>;

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
    // A globalId search is an exact match, so it needs no contains operator.
    const isGlobalId = selector.slice(selector.indexOf(".") + 1) === "globalId";
    if (!isGlobalId && !published.operators.includes("=contains=")) {
      throw new Error(`Searchable field does not support contains: ${selector}`);
    }
    if (published.fieldType !== undefined && published.fieldType !== "text") {
      throw new Error(`Searchable field must be text: ${selector}`);
    }
  }
}

export function createApiV2CollectionAdapter<
  TDocument,
  TSchema extends ApiV2DocumentSchema<TDocument> = ApiV2DocumentSchema<TDocument>,
>({
  config: sourceConfig,
  documentSchema,
  mapDocument,
  metadata,
  runtimeFields = [],
}: ApiV2CollectionDefinition<TDocument, TSchema>): ApiV2CollectionAdapter<TDocument> {
  const allowedFields = new Set(metadata.fields);
  for (const field of sourceConfig.fields) {
    // Filter-only members are published selectors without a response projection.
    if (!allowedFields.has(field.name) && metadata.filtering.selectors[field.name] && field.form === false) continue;
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
    ...filterFields.relationshipFields.map<FieldConfig<TDocument>>(({ field, owner, targetField }) =>
      projectableRelationships.has(owner)
        ? {
            ...field,
            list: {
              dependencies: [owner],
              valueSource: { kind: "relationshipTarget", owner, targetField },
            },
          }
        : { ...field, list: false as const },
    ),
    ...filterFields.runtimeFields.map<FieldConfig<TDocument>>(({ field, namespace, responseField, definition }) => ({
      ...field,
      list: namespace.columnSelectable
        ? {
            description: definition.source.label,
            valueSource: {
              kind: "runtimeField",
              responseField,
              id: definition.id,
              numeric: definition.type === "number",
            },
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
    state.visibleFields.filter((name) => runtimeSelectors.has(name));
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
      }),
    // A request selects the visible fields only, so a response omits every other field. Validate the
    // same projection that the request asked for: an omitted selected field stays an error, and an
    // unselected field cannot fail. The row type still names every field, because column visibility
    // is run-time state that a static type cannot narrow.
    parseResponse: (input, selected) => {
      const [first, ...rest] = selected;
      if (first === undefined) throw new Error("A collection projection must select its identifier");
      const projection = v.required(v.pick(responseSchema, [String(first), ...rest.map(String)]));
      const envelope = parseOrThrow(objectInput, input);
      const result = parseOrThrow(v.object({ docs: v.array(projection) }), { docs: envelope.docs ?? envelope.items });
      // Column visibility is runtime state: only selected fields can be present. Validation above
      // proves their wire types. This single projection assertion lets the mapper consume the
      // schema output; the identity mapper is permitted only when that output fits TDocument.
      const documents = withExpandedTargetFields(
        result.docs,
        { docs: envelope.docs ?? envelope.items },
        relationshipNames,
      ) as (v.InferOutput<TSchema> & TDocument)[];
      const toDocument: (document: v.InferOutput<TSchema> & TDocument) => TDocument =
        mapDocument ?? ((document) => document);
      const rows = documents.map(toDocument);
      return {
        rows,
        rowCount:
          typeof envelope.totalDocs === "number"
            ? envelope.totalDocs
            : typeof envelope.total === "number"
              ? envelope.total
              : undefined,
        hasNextPage:
          typeof envelope.hasNextPage === "boolean"
            ? envelope.hasNextPage
            : typeof envelope.page === "number" &&
                typeof envelope.pageSize === "number" &&
                typeof envelope.total === "number"
              ? envelope.page * envelope.pageSize < envelope.total
              : undefined,
      };
    },
  };
}

// Restore expanded display keys stripped by the page schema.
// These extras are unvalidated, so parsed values must override raw equivalents.
function withExpandedTargetFields(
  parsed: Record<string, unknown>[],
  input: unknown,
  relationshipNames: readonly string[],
): Record<string, unknown>[] {
  if (relationshipNames.length === 0) return parsed;
  const envelope = v.safeParse(objectInput, input);
  const rawDocs = envelope.success ? envelope.output.docs : undefined;
  if (!Array.isArray(rawDocs)) return parsed;
  return parsed.map((document, index) => {
    const raw = v.safeParse(objectInput, rawDocs[index]);
    if (!raw.success) return document;
    let restored = document;
    for (const name of relationshipNames) {
      const rawValue = expandedTarget(raw.output[name]);
      const parsedRelationship = v.safeParse(objectInput, restored[name]);
      if (!parsedRelationship.success) continue;
      const parsedValue = expandedTarget(parsedRelationship.output);
      if (!rawValue || !parsedValue) continue;
      restored = { ...restored, [name]: { ...parsedRelationship.output, value: { ...rawValue, ...parsedValue } } };
    }
    return restored;
  });
}

const expandedTargetSchema = v.pipe(objectInput, v.object({ value: objectInput }));

function expandedTarget(relationship: unknown): Record<string, unknown> | null {
  const result = v.safeParse(expandedTargetSchema, relationship);
  return result.success ? result.output.value : null;
}
