import * as v from "valibot";
import type { ApiV2CollectionParams } from "@/modules/common/api/v2/apiV2Resources";
import type { ApiV2FilterResource } from "@/modules/common/api/v2/apiV2Where";
import { apiV2ReadSchemas } from "@/modules/common/api/v2/generated/apiV2ReadSchemas";
import type { CollectionConfig, FieldName, ResolvedCollectionConfig } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { encodeInstants } from "@/modules/common/time/instant";
import type { CollectionPage, CollectionQueryState } from "../types";
import { createApiV2FilterFields, isRelationshipWireSelector } from "./filterFields";
import type { ApiV2CollectionMetadata } from "./metadata";
import { collectionQueryParams, selectedFields } from "./queryParams";
import type { ApiV2FilterResourceOf } from "./requestParams";
import type { RuntimeFieldCatalogForNamespace } from "./runtimeFieldCatalog";
import {
  type ApiV2DocumentMapping,
  type ApiV2DocumentSchema,
  createApiV2ResponseSchema,
  expandedValueKeys,
} from "./schema";
import { objectInput } from "./schemaPrimitives";

export type ApiV2CollectionAdapter<TDocument, R extends ApiV2FilterResource = never> = {
  config: ResolvedCollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  selectedFields: (state: CollectionQueryState<TDocument>) => readonly FieldName<TDocument>[];
  requiredDepth: (state: CollectionQueryState<TDocument>) => number;
  toRequestParams: (state: CollectionQueryState<TDocument>) => ApiV2CollectionParams<R>;
  parseResponse: (input: unknown, selected: readonly FieldName<TDocument>[]) => CollectionPage<TDocument>;
  isRuntimeSelector: (name: string) => boolean;
};

export type ApiV2CollectionDefinition<
  TDocument,
  TSchema extends ApiV2DocumentSchema<TDocument> = ApiV2DocumentSchema<TDocument>,
  TEntry extends { readonly name: string } = { readonly name: string },
> = {
  config: CollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
} & ApiV2DocumentMapping<TDocument, TSchema, TEntry>;

/**
 * A definition after its mapping arm was checked: the schema that validates a document, when not the resource's
 * read schema, and the mapper of that schema's output.
 */
export type ApiV2ResolvedCollectionDefinition<TDocument> = {
  config: CollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
  documentSchema?: v.ObjectSchema<v.ObjectEntries, undefined>;
  mapDocument?: (document: never) => TDocument;
};

function readSchemaFor(resourceName: string): v.ObjectSchema<v.ObjectEntries, undefined> {
  const schema = Object.entries(apiV2ReadSchemas).find(([name]) => name === resourceName)?.[1];
  if (schema === undefined) throw new Error(`API v2 resource ${resourceName} has no read schema; pass documentSchema`);
  return schema;
}

/** Each declared field is a published response field the document schema carries, or a filter-only selector. */
function validateDeclaredFields<TDocument>(
  config: CollectionConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  documentSchema: v.ObjectSchema<v.ObjectEntries, undefined>,
): void {
  const allowedFields = new Set(metadata.fields);
  for (const field of config.fields) {
    // Filter-only members are published selectors without a response projection.
    if (!allowedFields.has(field.name) && metadata.filtering.selectors[field.name] && field.form === false) continue;
    if (!allowedFields.has(field.name)) throw new Error(`Collection config references unknown API field ${field.name}`);
    // The response projection picks these names out of the schema, so a name that the schema does
    // not carry must fail here rather than inside the parser.
    if (!(field.name in documentSchema.entries)) {
      throw new Error(`Collection config references field ${field.name}, which the document schema does not declare`);
    }
  }
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
  TEntry extends { readonly name: string } = { readonly name: string },
>(
  definition: ApiV2CollectionDefinition<TDocument, TSchema, TEntry>,
): ApiV2CollectionAdapter<TDocument, ApiV2FilterResourceOf<TEntry>> {
  return buildApiV2CollectionAdapter(definition);
}

/** Builds an adapter from a definition whose mapping arm its caller already type-checked. */
export function buildApiV2CollectionAdapter<TDocument, R extends ApiV2FilterResource = never>({
  config: sourceConfig,
  documentSchema: suppliedSchema,
  mapDocument,
  metadata,
  runtimeFields = [],
}: ApiV2ResolvedCollectionDefinition<TDocument>): ApiV2CollectionAdapter<TDocument, R> {
  const documentSchema: v.ObjectSchema<v.ObjectEntries, undefined> =
    suppliedSchema ?? readSchemaFor(metadata.resourceName);
  validateDeclaredFields(sourceConfig, metadata, documentSchema);
  validateSearchSelectors(sourceConfig, metadata);
  const runtimeNamespaces = metadata.runtimeFields ?? [];
  const projectableFields = new Set(Object.keys(documentSchema.entries) as FieldName<TDocument>[]);
  const filterFields = createApiV2FilterFields({
    config: sourceConfig,
    metadata,
    runtimeFields,
    projectableFields,
  });
  const fields = filterFields.fields;
  for (const field of fields) {
    const source = field.list === false ? undefined : field.list?.valueSource;
    if (source?.kind !== "relationshipTarget") continue;
    // The page schema validates every value a row carries, and Valibot drops undeclared keys, so a listed target
    // field must be declared on its owner's expanded document.
    if (!expandedValueKeys(documentSchema.entries[String(source.owner)]).has(source.targetField)) {
      throw new Error(
        `Target field ${String(field.name)} is not declared on the expanded value of relationship \`${String(source.owner)}\``,
      );
    }
  }
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
    state.visibleFields.filter((name) => filterFields.runtimeSelectorNames.has(name));

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
    requiredDepth: (state) =>
      state.visibleFields.some((field) => filterFields.expandedTargetNames.has(field)) ? 1 : 0,
    toRequestParams: (state) =>
      collectionQueryParams<TDocument, R>(state, config, metadata, filterFields.virtualFields, projectableFields, {
        projection: runtimeProjection(state),
        selectors: filterFields.selectors,
      }),
    // A request selects the visible fields only, so a response omits every other field. Validate the
    // same projection that the request asked for: an omitted selected field stays an error, and an
    // unselected field cannot fail. The row type still names every field, because column visibility
    // is run-time state that a static type cannot narrow.
    parseResponse: (input, selected) => {
      const [first, ...rest] = selected;
      if (first === undefined) throw new Error("A collection projection must select its identifier");
      const projection = v.required(v.pick(responseSchema, [String(first), ...rest.map(String)]));
      // The transport has already decoded date-times into instants, which the wire schemas below reject; hand them
      // back as text so the projection parses exactly as the server's JSON would.
      const envelope = parseOrThrow(objectInput, encodeInstants(input));
      const result = parseOrThrow(v.object({ docs: v.array(projection) }), envelope);
      // Column visibility is runtime state: only selected fields can be present. Validation above
      // proves their wire types. This single assertion lets the mapper consume the schema output: the
      // mapping arms tie each mapper to the schema that validated its input, and permit the identity
      // mapper only when that output fits TDocument.
      const toDocument = (mapDocument ?? ((document: Record<string, unknown>) => document)) as (
        document: Record<string, unknown>,
      ) => TDocument;
      const rows = result.docs.map(toDocument);
      return {
        rows,
        rowCount: typeof envelope.totalDocs === "number" ? envelope.totalDocs : undefined,
        hasNextPage: typeof envelope.hasNextPage === "boolean" ? envelope.hasNextPage : undefined,
      };
    },
  };
}
