import type {
  CollectionConfig,
  FieldConfig,
  FieldName,
  FilterOperator,
  ResolvedCollectionConfig,
  RuntimeNamespaceSummary,
} from "@/modules/common/collection/collectionConfig";
import { fieldLabel, hierarchicalFieldLabel } from "@/modules/common/collection/collectionConfig";
import {
  isFilterOperatorCompatible,
  resolveCollectionConfig,
} from "@/modules/common/collection/resolveCollectionConfig";
import type {
  ApiV2CollectionMetadata,
  ApiV2FilterOperator,
  ApiV2PrimitiveFieldType,
  ApiV2RuntimeFieldNamespace,
} from "./apiV2CollectionMetadata";
import type { RuntimeFieldDefinition } from "./runtimeFieldCatalog";

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

export type RuntimeFieldCatalogForNamespace = {
  namespace: string;
  definitions: readonly RuntimeFieldDefinition[];
};

export type ApiV2FilterFieldsDefinition<TDocument> = {
  config: CollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
  translate?: (key: string, values?: Record<string, unknown>) => string;
  /** Fields interpreted locally by a custom endpoint adapter, outside the API metadata. */
  localFields?: readonly FieldName<TDocument>[];
};

export type ApiV2RelationshipFilterField<TDocument> = {
  field: FieldConfig<TDocument>;
  owner: FieldName<TDocument>;
  targetField: string;
};

export type ApiV2RuntimeFilterField<TDocument> = {
  field: FieldConfig<TDocument>;
  namespace: ApiV2RuntimeFieldNamespace;
  responseField: FieldName<TDocument>;
  definition: RuntimeFieldDefinition;
  operators: readonly FilterOperator[];
  wireOperators: readonly ApiV2FilterOperator[];
  supportsWildcards: boolean;
};

export type ApiV2FilterFields<TDocument> = {
  /** Source fields with metadata capabilities applied where the API publishes them. */
  sourceFields: readonly FieldConfig<TDocument>[];
  /** Relationship target fields are filter-only until an endpoint adds a renderer. */
  relationshipFields: readonly ApiV2RelationshipFilterField<TDocument>[];
  /** Runtime fields are filter-only until an endpoint adds a projection/renderer. */
  runtimeFields: readonly ApiV2RuntimeFilterField<TDocument>[];
  /** All fields suitable for a filter configuration. Derived fields have `list: false`. */
  fields: readonly FieldConfig<TDocument>[];
  virtualFields: ReadonlySet<FieldName<TDocument>>;
  runtimeSelectors: ReadonlyMap<
    FieldName<TDocument>,
    { operators: readonly FilterOperator[]; supportsWildcards: boolean }
  >;
  catalogSelectors: ApiV2CollectionMetadata<TDocument>["filtering"]["selectors"];
  runtimeSources: readonly RuntimeNamespaceSummary[];
};

function capabilitiesForSelector<TDocument>(
  field: FieldConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  selector:
    | {
        operators: readonly ApiV2FilterOperator[];
        wildcards: boolean;
      }
    | undefined,
): NonNullable<FieldConfig<TDocument>["capabilities"]> {
  return {
    sortable: metadata.sorting.fields.includes(field.name),
    filterOperators:
      selector?.operators
        .map((operator) => semanticOperators[operator])
        .filter((operator) => isFilterOperatorCompatible(field.type, operator)) ?? [],
    supportsWildcards: selector?.wildcards ?? false,
  };
}

/**
 * Returns a catalog label, or undefined when the translator does not know the key.
 *
 * Keys carry their namespace so a page-scoped translator, such as `useTranslation("booking")`,
 * still resolves these shared table labels. A missing key comes back as the key itself, with or
 * without its namespace, depending on the translator.
 */
function catalogLabel(translate: (key: string) => string, key: string): string | undefined {
  const value = translate(key);
  const withoutNamespace = key.slice(key.indexOf(":") + 1);
  return value === key || value === withoutNamespace || value.trim() === "" ? undefined : value;
}

/** Relationship owners without a configured field, such as the shared audit relationships. */
function ownerLabel(ownerName: string, viaTitle: string | undefined, translate: (key: string) => string): string {
  return catalogLabel(translate, `common:tableList.fields.${ownerName}`) ?? (viaTitle?.trim() ? viaTitle : ownerName);
}

/** A server title wins; otherwise the shared catalog names common target fields such as `firstName`. */
function targetFieldLabel(targetField: string, title: string | undefined, translate: (key: string) => string): string {
  return title ?? catalogLabel(translate, `common:tableList.targetFields.${targetField}`) ?? targetField;
}

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

function primitiveField<TDocument>(
  common: Pick<FieldConfig<TDocument>, "name" | "labelKey" | "label" | "origin"> & { list: false; form: false },
  fieldType: ApiV2PrimitiveFieldType,
): FieldConfig<TDocument> {
  switch (fieldType) {
    case "number":
      return { ...common, type: "number" };
    case "boolean":
      return { ...common, type: "boolean" };
    case "dateTime":
      return { ...common, type: "dateTime" };
    default:
      return { ...common, type: "text" };
  }
}

function derivedIdentityFields<TDocument>(
  sourceConfig: CollectionConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  translate: (key: string) => string,
): FieldConfig<TDocument>[] {
  const declared = new Set(sourceConfig.fields.map((field) => String(field.name)));
  return Object.entries(metadata.filtering.selectors).flatMap(([selector, published]) => {
    const picker = published?.picker;
    if (!picker || selector.includes(".") || declared.has(selector)) return [];

    const name = selector as FieldName<TDocument>;
    const base: FieldConfig<TDocument> = {
      name,
      labelKey: selector,
      label: published.title ?? ownerLabel(selector, undefined, translate),
      type: "relationship",
      relationTo: picker.resource,
      hasMany: false,
      filterPicker: picker,
      list: false,
      form: false,
    };
    return [{ ...base, capabilities: capabilitiesForSelector(base, metadata, published) }];
  });
}

function derivedTargetFields<TDocument>(
  sourceConfig: CollectionConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  translate: (key: string) => string,
): ApiV2RelationshipFilterField<TDocument>[] {
  const declared = new Set<string>(sourceConfig.fields.map((field) => String(field.name)));
  const relationships = new Map(
    sourceConfig.fields
      .filter((field) => field.type === "relationship")
      .map((field) => [String(field.name), field] as const),
  );
  return Object.entries(metadata.relationshipFields ?? {}).flatMap(([selector, published]) => {
    const dot = selector.indexOf(".");
    const ownerName = selector.slice(0, dot);
    const relationship = relationships.get(ownerName);
    const targetField = selector.slice(dot + 1);
    if (declared.has(selector) || published.fieldType === null) return [];

    const name = selector as FieldName<TDocument>;
    const owner = (relationship?.name ?? ownerName) as FieldName<TDocument>;
    const viaLabel = relationship
      ? fieldLabel(relationship, translate)
      : ownerLabel(ownerName, published.viaTitle, translate);
    const common = {
      name,
      labelKey: selector,
      label: hierarchicalFieldLabel(viaLabel, targetFieldLabel(targetField, published.title, translate)),
      origin: {
        kind: "relationshipTarget" as const,
        groupLabelKey: "tableList.fieldGroups.relationshipFields",
        viaLabel,
      },
      list: false as const,
      form: false as const,
    };
    const base = primitiveField(common, published.fieldType);
    const field = { ...base, capabilities: capabilitiesForSelector(base, metadata, published) };
    return [{ field, owner, targetField }];
  });
}

function derivedRuntimeFields<TDocument>(
  metadata: ApiV2CollectionMetadata<TDocument>,
  catalog: readonly RuntimeFieldCatalogForNamespace[],
  viaLabels: ReadonlyMap<string, string>,
): ApiV2RuntimeFilterField<TDocument>[] {
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
          runtimeValueType: definition.type,
        },
        list: false as const,
        form: false as const,
      };
      const base = (
        definition.options.length > 0
          ? { ...common, type: "select" as const, options: [...definition.options] }
          : definition.type === "number"
            ? { ...common, type: "number" as const }
            : { ...common, type: "text" as const }
      ) satisfies FieldConfig<TDocument>;
      const wireOperators = namespace.filterable ? definition.operators : [];
      const operators = wireOperators
        .map((operator) => semanticOperators[operator])
        .filter((operator) => isFilterOperatorCompatible(base.type, operator));
      const field = {
        ...base,
        capabilities: {
          sortable: false,
          filterOperators: operators,
          supportsWildcards: definition.supportsWildcards,
        },
      } satisfies FieldConfig<TDocument>;
      return [
        {
          field,
          namespace,
          responseField,
          definition,
          operators,
          wireOperators,
          supportsWildcards: definition.supportsWildcards,
        },
      ];
    }),
  );
}

export function createApiV2FilterFields<TDocument>({
  config: sourceConfig,
  metadata,
  runtimeFields = [],
  translate = (key) => key,
  localFields = [],
}: ApiV2FilterFieldsDefinition<TDocument>): ApiV2FilterFields<TDocument> {
  const configuredSourceFields = sourceConfig.fields.map((field) => {
    const selector =
      metadata.filtering.selectors[String(field.name)] ?? metadata.relationshipFields?.[String(field.name)];
    if (localFields.includes(field.name)) return field;
    const picker = metadata.filtering.selectors[String(field.name)]?.picker;
    return {
      ...field,
      ...(picker === undefined ? {} : { filterPicker: picker }),
      capabilities: capabilitiesForSelector(field, metadata, selector),
    };
  });
  const identityFields = derivedIdentityFields(sourceConfig, metadata, translate);
  const sourceFields = [...configuredSourceFields, ...identityFields];
  const identityConfig: CollectionConfig<TDocument> = {
    ...sourceConfig,
    fields: [...sourceConfig.fields, ...identityFields],
  };
  const relationshipFields = derivedTargetFields(identityConfig, metadata, translate);
  const runtimeDerivedFields = derivedRuntimeFields(
    metadata,
    runtimeFields,
    relationshipLabels(identityConfig, translate),
  );
  const occupiedNames = new Set([
    ...sourceConfig.fields.map((field) => String(field.name)),
    ...Object.keys(metadata.filtering.selectors),
    ...Object.keys(metadata.relationshipFields ?? {}),
  ]);
  const runtimeNames = new Set<string>();
  for (const { field } of runtimeDerivedFields) {
    const name = String(field.name);
    if (occupiedNames.has(name) || runtimeNames.has(name)) {
      throw new Error(`Runtime field selector shadows an existing selector: ${name}`);
    }
    runtimeNames.add(name);
  }
  const runtimeSelectors = new Map(
    runtimeDerivedFields.map(
      ({ field, operators, supportsWildcards }) => [field.name, { operators, supportsWildcards }] as const,
    ),
  );
  const virtualFields = new Set<FieldName<TDocument>>([
    ...identityFields.map((field) => field.name),
    ...relationshipFields.map(({ field }) => field.name),
    ...runtimeSelectors.keys(),
  ]);
  const catalogSelectors = Object.fromEntries(
    runtimeDerivedFields.map(({ field, wireOperators, supportsWildcards }) => [
      String(field.name),
      { operators: wireOperators, wildcards: supportsWildcards },
    ]),
  ) as ApiV2FilterFields<TDocument>["catalogSelectors"];
  const runtimeSources = (metadata.runtimeFields ?? [])
    .filter((namespace) => namespace.filterable || namespace.columnSelectable)
    .map((namespace) => ({
      namespace: namespace.namespace,
      viaLabel:
        namespace.via === "" ? "" : (relationshipLabels(identityConfig, translate).get(namespace.via) ?? namespace.via),
      catalog: namespace.catalog,
      maximumLimit: namespace.catalogMaximumLimit,
      filterable: namespace.filterable,
      columnSelectable: namespace.columnSelectable,
    }));

  return {
    sourceFields,
    relationshipFields,
    runtimeFields: runtimeDerivedFields,
    fields: [
      ...sourceFields,
      ...relationshipFields.map(({ field }) => field),
      ...runtimeDerivedFields.map(({ field }) => field),
    ],
    virtualFields,
    runtimeSelectors,
    catalogSelectors,
    runtimeSources,
  };
}

/**
 * Adds API-supported target/runtime selectors to a caller-owned configuration as filter-only fields.
 * Existing fields, columns, and explicitly supplied capabilities remain caller-owned.
 */
export function enrichApiV2FilterConfig<TDocument>(
  definition: ApiV2FilterFieldsDefinition<TDocument>,
): ResolvedCollectionConfig<TDocument> {
  const derived = createApiV2FilterFields(definition);
  const runtimeNamespaces =
    definition.metadata.runtimeFields === undefined
      ? definition.config.runtimeNamespaces
      : definition.metadata.runtimeFields.map((namespace) => namespace.namespace);
  const configInput: CollectionConfig<TDocument> = {
    ...definition.config,
    fields: derived.fields,
    ...(runtimeNamespaces === undefined ? {} : { runtimeNamespaces: runtimeNamespaces }),
  };
  return {
    ...resolveCollectionConfig(configInput),
    runtimeSources: derived.runtimeSources.map((source) => ({ ...source, columnSelectable: false })),
  };
}
