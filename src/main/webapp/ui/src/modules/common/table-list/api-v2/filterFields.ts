import type {
  CollectionConfig,
  CollectionTranslationKey,
  FieldConfig,
  FieldName,
  LabelText,
  RelationshipLabel,
  ResolvedCollectionConfig,
  RuntimeNamespaceSummary,
} from "@/modules/common/collection/config";
import { isFilterOperatorCompatible, resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { filterOperatorsByToken as semanticOperators } from "@/modules/common/collection/schema";
import type { ApiV2CollectionMetadata, ApiV2FilterOperator, ApiV2RuntimeFieldNamespace } from "./metadata";
import type { RuntimeFieldCatalogForNamespace, RuntimeFieldDefinition } from "./runtimeFieldCatalog";

export type ApiV2FilterFieldsDefinition<TDocument> = {
  config: CollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
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
  wireOperators: readonly ApiV2FilterOperator[];
};

export type ApiV2FilterFields<TDocument> = {
  /** Source fields with metadata capabilities applied where the API publishes them. */
  sourceFields: readonly FieldConfig<TDocument>[];
  /** Relationship target fields remain filter-only until an endpoint adapter adds a value source. */
  relationshipFields: readonly ApiV2RelationshipFilterField<TDocument>[];
  /** Runtime fields remain filter-only until an endpoint adapter adds a value source. */
  runtimeFields: readonly ApiV2RuntimeFilterField<TDocument>[];
  /** All fields suitable for a filter configuration. Derived fields have `list: false`. */
  fields: readonly FieldConfig<TDocument>[];
  virtualFields: ReadonlySet<FieldName<TDocument>>;
  runtimeSelectors: ReadonlySet<FieldName<TDocument>>;
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
    filterOperators: filterOperatorsFor(field.type, selector?.operators ?? []),
    supportsWildcards: selector?.wildcards ?? false,
  };
}

/** The published wire operators as filter operators that suit the field type. */
function filterOperatorsFor(type: FieldConfig<unknown>["type"], wire: readonly ApiV2FilterOperator[]) {
  return wire
    .map((operator) => semanticOperators[operator])
    .filter((operator) => isFilterOperatorCompatible(type, operator));
}

/** `<relationship>.value` and `<relationship>.relationTo` address the reference itself, not a target field. */
export function isRelationshipWireSelector(selector: string): boolean {
  return /^[^.]+\.(?:value|relationTo)$/.test(selector);
}

/** Unknown metadata names stay display data rather than catalog paths. */
function ownerLabel(ownerName: string, viaTitle: string | undefined): LabelText {
  const labels = {
    createdBy: "common:tableList.fields.createdBy",
    updatedBy: "common:tableList.fields.updatedBy",
    location: "common:tableList.fields.location",
  };
  if (Object.hasOwn(labels, ownerName)) return { key: labels[ownerName as keyof typeof labels] };
  return { text: viaTitle?.trim() ? viaTitle : ownerName };
}

function targetFieldLabel(targetField: string, title: string | undefined): LabelText {
  const labels = {
    createdAt: "common:tableList.targetFields.createdAt",
    deleted: "common:tableList.targetFields.deleted",
    email: "common:tableList.targetFields.email",
    firstName: "common:tableList.targetFields.firstName",
    globalId: "common:tableList.targetFields.globalId",
    id: "common:tableList.targetFields.id",
    lastName: "common:tableList.targetFields.lastName",
    name: "common:tableList.targetFields.name",
    username: "common:tableList.targetFields.username",
    updatedAt: "common:tableList.targetFields.updatedAt",
  };
  if (Object.hasOwn(labels, targetField)) return { key: labels[targetField as keyof typeof labels] };
  return { text: title ?? targetField };
}

type RelationshipField<TDocument> = Extract<FieldConfig<TDocument>, { type: "relationship" }>;

function configuredRelationshipLabel<TDocument>(field: RelationshipField<TDocument>): RelationshipLabel {
  const label = field.label !== undefined ? { text: field.label } : (field.derivedLabel ?? { key: field.labelKey });
  return { name: String(field.name), label };
}

function relationshipIndex<TDocument>(
  fields: readonly FieldConfig<TDocument>[],
): ReadonlyMap<string, { field: RelationshipField<TDocument>; via: RelationshipLabel }> {
  return new Map(
    fields
      .filter((field) => field.type === "relationship")
      .map((field) => [String(field.name), { field, via: configuredRelationshipLabel(field) }] as const),
  );
}

function runtimeVia<TDocument>(relationships: ReturnType<typeof relationshipIndex<TDocument>>, via: string) {
  if (via === "") return undefined;
  return relationships.get(via)?.via ?? { name: via, label: { text: via } };
}

function derivedIdentityFields<TDocument>(
  declared: ReadonlySet<string>,
  metadata: ApiV2CollectionMetadata<TDocument>,
): FieldConfig<TDocument>[] {
  return Object.entries(metadata.filtering.selectors).flatMap(([selector, published]) => {
    const picker = published?.picker;
    if (!picker || selector.includes(".") || declared.has(selector)) return [];

    const name = selector as FieldName<TDocument>;
    const base: FieldConfig<TDocument> = {
      name,
      labelKey: selector,
      derivedLabel: ownerLabel(selector, published.title),
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
  declared: ReadonlySet<string>,
  relationships: ReturnType<typeof relationshipIndex<TDocument>>,
  metadata: ApiV2CollectionMetadata<TDocument>,
): ApiV2RelationshipFilterField<TDocument>[] {
  return Object.entries(metadata.filtering.selectors).flatMap(([selector, published]) => {
    const dot = selector.indexOf(".");
    const ownerName = selector.slice(0, dot);
    const relationship = relationships.get(ownerName);
    const targetField = selector.slice(dot + 1);
    if (dot < 0 || isRelationshipWireSelector(selector)) return [];
    if (declared.has(selector) || published?.fieldType == null) return [];

    const name = selector as FieldName<TDocument>;
    const owner = (relationship?.field.name ?? ownerName) as FieldName<TDocument>;
    const via = relationship?.via ?? { name: ownerName, label: ownerLabel(ownerName, published.viaTitle) };
    const common = {
      name,
      labelKey: selector,
      derivedLabel: targetFieldLabel(targetField, published.title),
      origin: {
        kind: "relationshipTarget" as const,
        groupLabelKey: "tableList.fieldGroups.relationshipFields" satisfies CollectionTranslationKey,
        via,
      },
      list: false as const,
      form: false as const,
    };
    const base: FieldConfig<TDocument> = { ...common, type: published.fieldType };
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
  return catalog.flatMap((entry) => {
    const namespace = namespaces.get(entry.namespace);
    if (!namespace) return [];
    return entry.definitions.flatMap((definition) => {
      const responseField = namespace.responseField as FieldName<TDocument>;
      const name = (
        namespace.via === "" ? definition.selector : `${namespace.via}.${definition.selector}`
      ) as FieldName<TDocument>;
      const via = runtimeVia(relationships, namespace.via);
      const common = {
        name,
        labelKey: definition.selector,
        derivedLabel: { text: definition.label },
        origin: {
          kind: "runtimeField" as const,
          groupLabelKey: "tableList.fieldGroups.customFields" satisfies CollectionTranslationKey,
          sourceLabel: definition.source.label,
          namespace: namespace.namespace,
          ...(via === undefined ? {} : { via }),
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
      const operators = filterOperatorsFor(base.type, wireOperators);
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
          wireOperators,
        },
      ];
    });
  });
}

export function createApiV2FilterFields<TDocument>({
  config: sourceConfig,
  metadata,
  runtimeFields = [],
  localFields = [],
}: ApiV2FilterFieldsDefinition<TDocument>): ApiV2FilterFields<TDocument> {
  const declared = new Set(sourceConfig.fields.map((field) => String(field.name)));
  const configuredSourceFields = sourceConfig.fields.map((field) => {
    if (localFields.includes(field.name)) return field;
    const selector = metadata.filtering.selectors[String(field.name)];
    let sourceField = field;
    const structured = metadata.structuredFields?.[field.name];
    if ((field.type === "array" || field.type === "object") && metadata.structuredFields !== undefined) {
      if (!structured) throw new Error(`OpenAPI field ${field.name} must declare x-rspace-fields`);
      if (structured.type !== field.type)
        throw new Error(`OpenAPI field ${field.name} has a different structured type`);
      sourceField = {
        ...field,
        ...structured,
        readOnly: field.readOnly || structured.readOnly,
        fields: structured.fields.map((child) => {
          const configured = field.fields.find((candidate) => candidate.name === child.name);
          return configured &&
            (configured.label !== undefined ||
              configured.derivedLabel !== undefined ||
              configured.labelKey.includes(":"))
            ? {
                ...child,
                labelKey: configured.labelKey,
                label: configured.label,
                derivedLabel: configured.derivedLabel,
              }
            : child;
        }),
      };
    } else if (structured && field.type !== "relationship") {
      throw new Error(`Collection config field ${field.name} must use type ${structured.type}`);
    }
    const picker = selector?.picker;
    return {
      ...sourceField,
      ...(picker === undefined ? {} : { filterPicker: picker }),
      capabilities: capabilitiesForSelector(sourceField, metadata, selector),
    };
  });
  const identityFields = derivedIdentityFields(declared, metadata);
  const sourceFields = [...configuredSourceFields, ...identityFields];
  const relationships = relationshipIndex([...sourceConfig.fields, ...identityFields]);
  const relationshipFields = derivedTargetFields(declared, relationships, metadata);
  const runtimeDerivedFields = derivedRuntimeFields(metadata, runtimeFields, relationships);
  const occupiedNames = new Set([...declared, ...Object.keys(metadata.filtering.selectors)]);
  for (const { field } of runtimeDerivedFields) {
    const name = String(field.name);
    if (occupiedNames.has(name)) {
      throw new Error(`Runtime field selector shadows an existing selector: ${name}`);
    }
    occupiedNames.add(name);
  }
  const runtimeSelectors = new Set(runtimeDerivedFields.map(({ field }) => field.name));
  const virtualFields = new Set<FieldName<TDocument>>([
    ...sourceConfig.fields
      .filter((field) => !metadata.fields.includes(field.name) && metadata.filtering.selectors[field.name])
      .map((field) => field.name),
    ...identityFields.map((field) => field.name),
    ...relationshipFields.map(({ field }) => field.name),
    ...runtimeSelectors.keys(),
  ]);
  const catalogSelectors = Object.fromEntries(
    runtimeDerivedFields.map(({ field, wireOperators, definition }) => [
      String(field.name),
      { operators: wireOperators, wildcards: definition.supportsWildcards },
    ]),
  ) as ApiV2FilterFields<TDocument>["catalogSelectors"];
  const runtimeSources = (metadata.runtimeFields ?? [])
    .filter((namespace) => namespace.filterable || namespace.columnSelectable)
    .map((namespace) => {
      const via = runtimeVia(relationships, namespace.via);
      return {
        namespace: namespace.namespace,
        ...(via === undefined ? {} : { via }),
        catalog: namespace.catalog,
        maximumLimit: namespace.catalogMaximumLimit,
        filterable: namespace.filterable,
        columnSelectable: namespace.columnSelectable,
      };
    });

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
