import type {
  CollectionConfig,
  CollectionTranslationKey,
  FieldConfig,
  FieldName,
  FilterOperator,
  ResolvedCollectionConfig,
  RuntimeNamespaceSummary,
} from "@/modules/common/collection/config";
import { fieldLabel, hierarchicalFieldLabel } from "@/modules/common/collection/config";
import { filterOperatorsByToken as semanticOperators } from "@/modules/common/collection/filterOperators";
import { isFilterOperatorCompatible, resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import type {
  ApiV2CollectionMetadata,
  ApiV2FilterOperator,
  ApiV2PrimitiveFieldType,
  ApiV2RuntimeFieldNamespace,
} from "./metadata";
import type { RuntimeFieldDefinition } from "./runtimeFieldCatalog";

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

/** `<relationship>.value` and `<relationship>.relationTo` address the reference itself, not a target field. */
export function isRelationshipWireSelector(selector: string): boolean {
  return /^[^.]+\.(?:value|relationTo)$/.test(selector);
}

/** Unknown metadata names stay display data rather than catalog paths. */
function ownerLabel(
  ownerName: string,
  viaTitle: string | undefined,
  t: (key: CollectionTranslationKey) => string,
): string {
  const labels = {
    createdBy: t("common:tableList.fields.createdBy"),
    updatedBy: t("common:tableList.fields.updatedBy"),
    location: t("common:tableList.fields.location"),
  };
  return Object.hasOwn(labels, ownerName)
    ? labels[ownerName as keyof typeof labels]
    : viaTitle?.trim()
      ? viaTitle
      : ownerName;
}

function targetFieldLabel(
  targetField: string,
  title: string | undefined,
  t: (key: CollectionTranslationKey) => string,
): string {
  const labels = {
    createdAt: t("common:tableList.targetFields.createdAt"),
    deleted: t("common:tableList.targetFields.deleted"),
    email: t("common:tableList.targetFields.email"),
    firstName: t("common:tableList.targetFields.firstName"),
    globalId: t("common:tableList.targetFields.globalId"),
    id: t("common:tableList.targetFields.id"),
    lastName: t("common:tableList.targetFields.lastName"),
    name: t("common:tableList.targetFields.name"),
    username: t("common:tableList.targetFields.username"),
    updatedAt: t("common:tableList.targetFields.updatedAt"),
  };
  return Object.hasOwn(labels, targetField) ? labels[targetField as keyof typeof labels] : (title ?? targetField);
}

type RelationshipField<TDocument> = Extract<FieldConfig<TDocument>, { type: "relationship" }>;

function relationshipIndex<TDocument>(
  fields: readonly FieldConfig<TDocument>[],
  translate: (key: string) => string,
): ReadonlyMap<string, { field: RelationshipField<TDocument>; label: string }> {
  return new Map(
    fields
      .filter((field) => field.type === "relationship")
      .map((field) => [String(field.name), { field, label: fieldLabel(field, translate) }] as const),
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
      label: ownerLabel(selector, published.title, translate),
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
  relationships: ReturnType<typeof relationshipIndex<TDocument>>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  translate: (key: string) => string,
): ApiV2RelationshipFilterField<TDocument>[] {
  const declared = new Set<string>(sourceConfig.fields.map((field) => String(field.name)));
  return Object.entries(metadata.filtering.selectors).flatMap(([selector, published]) => {
    const dot = selector.indexOf(".");
    const ownerName = selector.slice(0, dot);
    const relationship = relationships.get(ownerName);
    const targetField = selector.slice(dot + 1);
    if (dot < 0 || isRelationshipWireSelector(selector)) return [];
    if (declared.has(selector) || published?.fieldType == null) return [];

    const name = selector as FieldName<TDocument>;
    const owner = (relationship?.field.name ?? ownerName) as FieldName<TDocument>;
    const viaLabel = relationship ? relationship.label : ownerLabel(ownerName, published.viaTitle, translate);
    const common = {
      name,
      labelKey: selector,
      label: hierarchicalFieldLabel(viaLabel, targetFieldLabel(targetField, published.title, translate)),
      origin: {
        kind: "relationshipTarget" as const,
        groupLabelKey: "tableList.fieldGroups.relationshipFields" satisfies CollectionTranslationKey,
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
  relationships: ReturnType<typeof relationshipIndex<TDocument>>,
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
      const viaLabel = namespace.via === "" ? "" : (relationships.get(namespace.via)?.label ?? namespace.via);
      const common = {
        name,
        labelKey: definition.selector,
        label: hierarchicalFieldLabel(viaLabel, definition.label),
        origin: {
          kind: "runtimeField" as const,
          groupLabelKey: "tableList.fieldGroups.customFields" satisfies CollectionTranslationKey,
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
    const selector = metadata.filtering.selectors[String(field.name)];
    if (localFields.includes(field.name)) return field;
    const sourceField = field;
    const picker = metadata.filtering.selectors[String(field.name)]?.picker;
    return {
      ...sourceField,
      ...(picker === undefined ? {} : { filterPicker: picker }),
      capabilities: capabilitiesForSelector(sourceField, metadata, selector),
    };
  });
  const identityFields = derivedIdentityFields(sourceConfig, metadata, translate);
  const sourceFields = [...configuredSourceFields, ...identityFields];
  const identityConfig: CollectionConfig<TDocument> = {
    ...sourceConfig,
    fields: [...sourceConfig.fields, ...identityFields],
  };
  const relationships = relationshipIndex(identityConfig.fields, translate);
  const relationshipFields = derivedTargetFields(identityConfig, relationships, metadata, translate);
  const runtimeDerivedFields = derivedRuntimeFields(metadata, runtimeFields, relationships);
  const occupiedNames = new Set([
    ...sourceConfig.fields.map((field) => String(field.name)),
    ...Object.keys(metadata.filtering.selectors),
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
      viaLabel: namespace.via === "" ? "" : (relationships.get(namespace.via)?.label ?? namespace.via),
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
