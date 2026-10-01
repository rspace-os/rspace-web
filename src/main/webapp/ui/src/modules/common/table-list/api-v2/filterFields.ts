import type { i18n as I18n, Namespace } from "i18next";
import type {
  CollectionConfig,
  CollectionTranslationKey,
  FieldConfig,
  FieldName,
  ResolvedCollectionConfig,
  RuntimeNamespaceSummary,
} from "@/modules/common/collection/config";
import { fieldLabel, hierarchicalFieldLabel } from "@/modules/common/collection/labels";
import { isFilterOperatorCompatible, resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { filterOperatorsByToken as semanticOperators } from "@/modules/common/collection/schema";
import appI18n from "@/modules/common/i18n";
import type { ApiV2CollectionMetadata, ApiV2FilterOperator, ApiV2RuntimeFieldNamespace } from "./metadata";
import type { RuntimeFieldCatalogForNamespace, RuntimeFieldDefinition } from "./runtimeFieldCatalog";

export type { RuntimeFieldCatalogForNamespace } from "./runtimeFieldCatalog";

export type ApiV2FilterFieldsDefinition<TDocument> = {
  config: CollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
  translate?: (key: string, values?: Record<string, unknown>) => string;
  language?: string;
  i18n?: I18n;
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
  /** Relationship target fields are filter-only until an endpoint adds a renderer. */
  relationshipFields: readonly ApiV2RelationshipFilterField<TDocument>[];
  /** Runtime fields are filter-only until an endpoint adds a projection/renderer. */
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
  translate: (key: CollectionTranslationKey) => string,
): ReadonlyMap<string, { field: RelationshipField<TDocument>; label: string }> {
  return new Map(
    fields
      .filter((field) => field.type === "relationship")
      .map((field) => [String(field.name), { field, label: fieldLabel(field, translate) }] as const),
  );
}

function runtimeViaLabel<TDocument>(relationships: ReturnType<typeof relationshipIndex<TDocument>>, via: string) {
  return via === "" ? "" : (relationships.get(via)?.label ?? via);
}

function derivedIdentityFields<TDocument>(
  declared: ReadonlySet<string>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  translate: (key: CollectionTranslationKey) => string,
): FieldConfig<TDocument>[] {
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
  declared: ReadonlySet<string>,
  relationships: ReturnType<typeof relationshipIndex<TDocument>>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  translate: (key: CollectionTranslationKey) => string,
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
      const viaLabel = runtimeViaLabel(relationships, namespace.via);
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
  language,
  i18n = appI18n,
  translate,
  localFields = [],
}: ApiV2FilterFieldsDefinition<TDocument>): ApiV2FilterFields<TDocument> {
  const translateLabel = (key: CollectionTranslationKey) =>
    translate?.(key) ?? i18n.getFixedT<Namespace>(language ?? null, "common")(key);
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
          return configured && (configured.label !== undefined || configured.labelKey.includes(":"))
            ? { ...child, labelKey: configured.labelKey, label: configured.label }
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
  const identityFields = derivedIdentityFields(declared, metadata, translateLabel);
  const sourceFields = [...configuredSourceFields, ...identityFields];
  const relationships = relationshipIndex([...sourceConfig.fields, ...identityFields], translateLabel);
  const relationshipFields = derivedTargetFields(declared, relationships, metadata, translateLabel);
  const runtimeDerivedFields = derivedRuntimeFields(metadata, runtimeFields, relationships);
  const occupiedNames = new Set([...declared, ...Object.keys(metadata.filtering.selectors)]);
  const runtimeNames = new Set<string>();
  for (const { field } of runtimeDerivedFields) {
    const name = String(field.name);
    if (occupiedNames.has(name) || runtimeNames.has(name)) {
      throw new Error(`Runtime field selector shadows an existing selector: ${name}`);
    }
    runtimeNames.add(name);
  }
  const runtimeSelectors = new Set(runtimeDerivedFields.map(({ field }) => field.name));
  const virtualFields = new Set<FieldName<TDocument>>([
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
    .map((namespace) => ({
      namespace: namespace.namespace,
      viaLabel: runtimeViaLabel(relationships, namespace.via),
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
