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
import { isFilterOperatorCompatible } from "@/modules/common/collection/fieldTypes";
import type { FilterOperatorToken } from "@/modules/common/collection/filterOperators";
import { filterOperatorsByToken as semanticOperators } from "@/modules/common/collection/filterOperators";
import type { RelationshipFilterPicker } from "@/modules/common/collection/relationshipFilterPicker";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import type { ApiV2CollectionMetadata, ApiV2PrimitiveFieldType } from "./metadata";
import type { RuntimeFieldCatalogForNamespace, RuntimeFieldDefinition } from "./runtimeFieldCatalog";

export type ApiV2FilterFieldsDefinition<TDocument> = {
  config: CollectionConfig<TDocument>;
  metadata: ApiV2CollectionMetadata<TDocument>;
  runtimeFields?: readonly RuntimeFieldCatalogForNamespace[];
  /** Fields interpreted locally by a custom endpoint adapter, outside the API metadata. */
  localFields?: readonly FieldName<TDocument>[];
  /**
   * Response fields the endpoint can project. When given, a relationship-target field whose owner is one of them,
   * and a runtime field in a column-selectable namespace, become columns; otherwise every derived field is filter-only.
   */
  projectableFields?: ReadonlySet<FieldName<TDocument>>;
};

export type ApiV2FilterFields<TDocument> = {
  /**
   * Declared fields with metadata capabilities applied, then the derived identity, relationship-target and runtime
   * fields. A derived field is a column only when `projectableFields` lets its value be projected.
   */
  fields: readonly FieldConfig<TDocument>[];
  virtualFields: ReadonlySet<FieldName<TDocument>>;
  /** The published selectors merged with the runtime-field selectors, for serialising a filter. */
  selectors: ApiV2CollectionMetadata<TDocument>["filtering"]["selectors"];
  runtimeSources: readonly RuntimeNamespaceSummary[];
};

function capabilitiesForSelector<TDocument>(
  field: FieldConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  selector:
    | {
        operators: readonly FilterOperatorToken[];
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

const relationshipFieldsGroup: CollectionTranslationKey = "tableList.fieldGroups.relationshipFields";
const customFieldsGroup: CollectionTranslationKey = "tableList.fieldGroups.customFields";

/**
 * Catalog labels for every relationship an API v2 resource publishes a filter through. The OpenAPI
 * document's English titles are documentation only; a test keeps these maps in step with the document.
 */
export const relationshipLabelKeys: Readonly<Record<string, CollectionTranslationKey>> = {
  createdBy: "common:tableList.fields.createdBy",
  updatedBy: "common:tableList.fields.updatedBy",
  location: "common:tableList.fields.location",
  parentContainer: "common:tableList.fields.parentContainer",
};

/** Catalog labels for every relationship target field an API v2 resource publishes. */
export const targetFieldLabelKeys: Readonly<Record<string, CollectionTranslationKey>> = {
  containerType: "common:tableList.targetFields.containerType",
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

/** A name missing from the catalog falls back to the name itself. */
function catalogLabel(keys: Readonly<Record<string, CollectionTranslationKey>>, name: string): LabelText {
  const key = Object.hasOwn(keys, name) ? keys[name] : undefined;
  return key === undefined ? { text: name } : { key };
}

function ownerLabel(ownerName: string): LabelText {
  return catalogLabel(relationshipLabelKeys, ownerName);
}

function targetFieldLabel(targetField: string): LabelText {
  return catalogLabel(targetFieldLabelKeys, targetField);
}

type RelationshipField<TDocument> = Extract<FieldConfig<TDocument>, { type: "relationship" }>;

function configuredRelationshipLabel<TDocument>(field: RelationshipField<TDocument>): RelationshipLabel {
  const label: LabelText =
    field.label !== undefined
      ? { text: field.label }
      : (field.derivedLabel ?? (field.labelKey === undefined ? { text: String(field.name) } : { key: field.labelKey }));
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

function primitiveField<TDocument>(
  common: Pick<FieldConfig<TDocument>, "name" | "labelKey" | "label" | "derivedLabel" | "origin"> & {
    list: false;
    form: false;
  },
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

function runtimeVia<TDocument>(relationships: ReturnType<typeof relationshipIndex<TDocument>>, via: string) {
  if (via === "") return undefined;
  return relationships.get(via)?.via ?? { name: via, label: ownerLabel(via) };
}

function identityField<TDocument>(
  selector: string,
  picker: RelationshipFilterPicker,
  metadata: ApiV2CollectionMetadata<TDocument>,
): FieldConfig<TDocument> {
  const base: FieldConfig<TDocument> = {
    name: selector as FieldName<TDocument>,
    derivedLabel: ownerLabel(selector),
    type: "relationship",
    relationTo: picker.resource,
    hasMany: false,
    filterPicker: picker,
    list: false,
    form: false,
  };
  return { ...base, capabilities: capabilitiesForSelector(base, metadata, metadata.filtering.selectors[selector]) };
}

type TargetSelector = {
  selector: string;
  ownerName: string;
  targetField: string;
  fieldType: ApiV2PrimitiveFieldType;
};

function targetField<TDocument>(
  { selector, ownerName, targetField, fieldType }: TargetSelector,
  relationships: ReturnType<typeof relationshipIndex<TDocument>>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  projectableFields: ReadonlySet<FieldName<TDocument>> | undefined,
): FieldConfig<TDocument> {
  const relationship = relationships.get(ownerName);
  const owner = (relationship?.field.name ?? ownerName) as FieldName<TDocument>;
  const via = relationship?.via ?? { name: ownerName, label: ownerLabel(ownerName) };
  const common = {
    name: selector as FieldName<TDocument>,
    derivedLabel: targetFieldLabel(targetField),
    origin: {
      kind: "relationshipTarget" as const,
      groupLabelKey: relationshipFieldsGroup,
      via,
    },
    list: false as const,
    form: false as const,
  };
  const base = primitiveField(common, fieldType);
  const field = {
    ...base,
    capabilities: capabilitiesForSelector(base, metadata, metadata.filtering.selectors[selector]),
  };
  if (relationship === undefined || !projectableFields?.has(owner)) return field;
  return {
    ...field,
    list: { dependencies: [owner], valueSource: { kind: "relationshipTarget", owner, targetField } },
  };
}

function runtimeFieldType(
  definition: RuntimeFieldDefinition,
): { type: "select"; options: string[] } | { type: "number" } | { type: "text" } {
  if (definition.options.length > 0) return { type: "select", options: [...definition.options] };
  if (definition.type === "number") return { type: "number" };
  return { type: "text" };
}

type RuntimeDerivedField<TDocument> = {
  field: FieldConfig<TDocument>;
  wireOperators: readonly FilterOperatorToken[];
  supportsWildcards: boolean;
};

function derivedRuntimeFields<TDocument>(
  metadata: ApiV2CollectionMetadata<TDocument>,
  catalog: readonly RuntimeFieldCatalogForNamespace[],
  relationships: ReturnType<typeof relationshipIndex<TDocument>>,
  projectableFields: ReadonlySet<FieldName<TDocument>> | undefined,
): RuntimeDerivedField<TDocument>[] {
  const namespaces = new Map((metadata.runtimeFields ?? []).map((namespace) => [namespace.namespace, namespace]));
  return catalog.flatMap((entry) =>
    entry.definitions.flatMap((definition) => {
      const namespace = namespaces.get(entry.namespace);
      if (!namespace) return [];
      const responseField = namespace.responseField as FieldName<TDocument>;
      const name = (
        namespace.via === "" ? definition.selector : `${namespace.via}.${definition.selector}`
      ) as FieldName<TDocument>;
      const via = runtimeVia(relationships, namespace.via);
      const common = {
        name,
        derivedLabel: { text: definition.label },
        origin: {
          kind: "runtimeField" as const,
          groupLabelKey: customFieldsGroup,
          sourceLabel: definition.source.label,
          namespace: namespace.namespace,
          ...(via === undefined ? {} : { via }),
          runtimeValueType: definition.type,
        },
        list: false as const,
        form: false as const,
      };
      const base = { ...common, ...runtimeFieldType(definition) } satisfies FieldConfig<TDocument>;
      const wireOperators = namespace.filterable ? definition.operators : [];
      const field = {
        ...base,
        capabilities: capabilitiesForSelector(base, metadata, {
          operators: wireOperators,
          wildcards: definition.supportsWildcards,
        }),
        ...(projectableFields !== undefined && namespace.columnSelectable
          ? {
              list: {
                description: definition.source.label,
                valueSource: { kind: "runtimeField" as const, responseField, id: definition.id },
              },
            }
          : {}),
      } satisfies FieldConfig<TDocument>;
      return [{ field, wireOperators, supportsWildcards: definition.supportsWildcards }];
    }),
  );
}

export function createApiV2FilterFields<TDocument>({
  config: sourceConfig,
  metadata,
  runtimeFields = [],
  localFields = [],
  projectableFields,
}: ApiV2FilterFieldsDefinition<TDocument>): ApiV2FilterFields<TDocument> {
  const selectors = metadata.filtering.selectors;
  const declared = new Set(sourceConfig.fields.map((field) => String(field.name)));
  const virtualFields = new Set<FieldName<TDocument>>();
  const configuredSourceFields = sourceConfig.fields.map((field) => {
    const selector = selectors[String(field.name)];
    if (!metadata.fields.includes(field.name) && selector) virtualFields.add(field.name);
    if (localFields.includes(field.name)) return field;
    const picker = selector?.picker;
    return {
      ...field,
      ...(picker === undefined ? {} : { filterPicker: picker }),
      capabilities: capabilitiesForSelector(field, metadata, selector),
    };
  });

  // A target selector can precede its owner's selector, so identity and target fields fill separate buckets.
  const identityFields: FieldConfig<TDocument>[] = [];
  const targetSelectors: TargetSelector[] = [];
  for (const [selector, published] of Object.entries(selectors)) {
    if (declared.has(selector)) continue;
    const dot = selector.indexOf(".");
    if (dot < 0) {
      if (published?.picker) identityFields.push(identityField(selector, published.picker, metadata));
      continue;
    }
    if (isRelationshipWireSelector(selector) || published?.fieldType == null) continue;
    targetSelectors.push({
      selector,
      ownerName: selector.slice(0, dot),
      targetField: selector.slice(dot + 1),
      fieldType: published.fieldType,
    });
  }
  const relationships = relationshipIndex([...sourceConfig.fields, ...identityFields]);
  const targetFields = targetSelectors.map((target) => targetField(target, relationships, metadata, projectableFields));
  const runtimeDerivedFields = derivedRuntimeFields(metadata, runtimeFields, relationships, projectableFields);

  const occupiedNames = new Set([...declared, ...Object.keys(selectors)]);
  for (const { field } of runtimeDerivedFields) {
    const name = String(field.name);
    if (occupiedNames.has(name)) {
      throw new Error(`Runtime field selector shadows an existing selector: ${name}`);
    }
    occupiedNames.add(name);
  }
  const derivedFields = [...identityFields, ...targetFields, ...runtimeDerivedFields.map(({ field }) => field)];
  for (const field of derivedFields) virtualFields.add(field.name);

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
    fields: [...configuredSourceFields, ...derivedFields],
    virtualFields,
    selectors: {
      ...selectors,
      ...Object.fromEntries(
        runtimeDerivedFields.map(({ field, wireOperators, supportsWildcards }) => [
          String(field.name),
          { operators: wireOperators, wildcards: supportsWildcards },
        ]),
      ),
    },
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
