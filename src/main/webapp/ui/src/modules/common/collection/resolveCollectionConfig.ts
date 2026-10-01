import type {
  CollectionConfig,
  FieldConfig,
  FieldName,
  FilterOperator,
  ResolvedCollectionConfig,
  ResolvedFieldCapabilities,
  ResolvedFieldConfig,
  SearchSelector,
} from "./collectionConfig";

const commonOperators = ["equals", "notEquals", "in", "notIn", "exists"] as const;

const defaultCapabilities: Record<FieldConfig<never>["type"], ResolvedFieldCapabilities> = {
  text: {
    sortable: true,
    filterOperators: [...commonOperators, "contains", "matches"],
    supportsWildcards: true,
  },
  number: {
    sortable: true,
    filterOperators: [...commonOperators, "greaterThan", "greaterThanOrEqual", "lessThan", "lessThanOrEqual"],
    supportsWildcards: false,
  },
  boolean: {
    sortable: true,
    filterOperators: ["equals", "notEquals", "exists"],
    supportsWildcards: false,
  },
  dateTime: {
    sortable: true,
    filterOperators: [
      "equals",
      "notEquals",
      "exists",
      "greaterThan",
      "greaterThanOrEqual",
      "lessThan",
      "lessThanOrEqual",
    ],
    supportsWildcards: false,
  },
  select: {
    sortable: true,
    filterOperators: [...commonOperators, "contains", "matches"],
    supportsWildcards: false,
  },
  relationship: {
    sortable: false,
    filterOperators: commonOperators,
    supportsWildcards: false,
  },
};

const allowedOperators: Record<FieldConfig<never>["type"], ReadonlySet<FilterOperator>> = {
  text: new Set(defaultCapabilities.text.filterOperators),
  number: new Set(defaultCapabilities.number.filterOperators),
  boolean: new Set(defaultCapabilities.boolean.filterOperators),
  dateTime: new Set(defaultCapabilities.dateTime.filterOperators),
  select: new Set(defaultCapabilities.select.filterOperators),
  relationship: new Set(defaultCapabilities.relationship.filterOperators),
};

export function isFilterOperatorCompatible(fieldType: FieldConfig<never>["type"], operator: FilterOperator): boolean {
  return allowedOperators[fieldType].has(operator);
}

function assertKnownField<TDocument>(
  name: FieldName<TDocument>,
  fields: ReadonlyMap<FieldName<TDocument>, FieldConfig<TDocument>>,
  role: string,
): FieldConfig<TDocument> {
  const field = fields.get(name);
  if (!field) throw new Error(`Unknown ${role} field: ${name}`);
  return field;
}

function directSearchField<TDocument>(
  name: SearchSelector<TDocument>,
  fields: ReadonlyMap<FieldName<TDocument>, FieldConfig<TDocument>>,
): FieldConfig<TDocument> | null {
  const selector = String(name);
  const dot = selector.indexOf(".");
  if (dot < 0) return assertKnownField(name as FieldName<TDocument>, fields, "searchable");
  if (dot === 0 || dot === selector.length - 1 || selector.indexOf(".", dot + 1) >= 0) {
    throw new Error(`Searchable relationship selector must have one hop: ${selector}`);
  }
  const relationship = fields.get(selector.slice(0, dot) as FieldName<TDocument>);
  if (relationship?.type !== "relationship") {
    throw new Error(`Searchable selector root must be a relationship: ${selector}`);
  }
  return null;
}

export function resolveCollectionConfig<
  TDocument,
  TId extends FieldName<TDocument> = FieldName<TDocument>,
  TTitle extends FieldName<TDocument> = FieldName<TDocument>,
>(config: CollectionConfig<TDocument, TId, TTitle>): ResolvedCollectionConfig<TDocument, TId, TTitle> {
  if (config.slug.trim() === "") throw new Error("Collection slug must not be empty");
  if (config.fields.length === 0) throw new Error("A collection must define at least one field");

  const fieldMap = new Map<FieldName<TDocument>, FieldConfig<TDocument>>();
  for (const field of config.fields) {
    if (fieldMap.has(field.name)) throw new Error(`Duplicate field: ${field.name}`);
    fieldMap.set(field.name, field);
  }

  assertKnownField(config.idField, fieldMap, "ID");
  const titleField = assertKnownField(config.useAsTitle, fieldMap, "title");
  if (titleField.list === false) throw new Error("The title field must be visible in lists");

  const defaultColumns = new Set<FieldName<TDocument>>();
  for (const name of config.defaultColumns) {
    const field = assertKnownField(name, fieldMap, "default column");
    if (field.list === false) throw new Error(`Default column is not listable: ${name}`);
    if (defaultColumns.has(name)) throw new Error(`Duplicate default column: ${name}`);
    defaultColumns.add(name);
  }

  const searchableFields = new Set<string>();
  for (const name of config.listSearchableFields ?? []) {
    if (searchableFields.has(name)) throw new Error(`Duplicate searchable field: ${name}`);
    searchableFields.add(name);
    const field = directSearchField(name, fieldMap);
    if (!field) continue;
    if (field.type !== "text" && field.type !== "select") {
      throw new Error(`Searchable field must be text or select: ${name}`);
    }
    if (field.list === false) throw new Error(`Searchable field is not listable: ${name}`);
  }

  const resolvedFields = config.fields.map((field): ResolvedFieldConfig<TDocument> => {
    const defaults = defaultCapabilities[field.type];
    const capabilities = {
      sortable: field.capabilities?.sortable ?? defaults.sortable,
      filterOperators: field.capabilities?.filterOperators ?? defaults.filterOperators,
      supportsWildcards: field.capabilities?.supportsWildcards ?? defaults.supportsWildcards,
    };
    for (const operator of capabilities.filterOperators) {
      if (!isFilterOperatorCompatible(field.type, operator)) {
        throw new Error(`Operator ${operator} is not compatible with ${field.type} field ${field.name}`);
      }
    }
    if (capabilities.supportsWildcards && field.type !== "text" && field.type !== "select") {
      throw new Error(`Wildcards are not compatible with ${field.type} field ${field.name}`);
    }
    return { ...field, capabilities } as ResolvedFieldConfig<TDocument>;
  });

  const resolvedByName = new Map(resolvedFields.map((field) => [field.name, field]));
  for (const name of config.listSearchableFields ?? []) {
    const field = resolvedByName.get(name as FieldName<TDocument>);
    // API adapters add a field for a published relationship selector. A form-only resolution has
    // no API metadata, so the adapter validates the target field when it creates its configuration.
    if (!field && String(name).includes(".")) continue;
    if (!field?.capabilities.filterOperators.includes("contains")) {
      throw new Error(`Searchable field does not support contains: ${name}`);
    }
  }
  for (const field of resolvedFields) {
    const list = field.list;
    if (!list) continue;
    for (const dependency of list.dependencies ?? []) {
      assertKnownField(dependency, fieldMap, `renderer dependency for ${field.name}`);
    }
  }
  for (const rule of config.defaultSort ?? []) {
    const field = resolvedByName.get(rule.field);
    if (!field) throw new Error(`Unknown default sort field: ${rule.field}`);
    if (!field.capabilities.sortable) throw new Error(`Default sort field is not sortable: ${rule.field}`);
  }

  const pagination = config.pagination;
  if (pagination) {
    if (pagination.defaultLimit <= 0 || pagination.limits.some((limit) => limit <= 0)) {
      throw new Error("Pagination limits must be positive");
    }
    if (!pagination.limits.includes(pagination.defaultLimit)) {
      throw new Error("Pagination limits must include the default limit");
    }
  }

  return { ...config, fields: resolvedFields };
}
