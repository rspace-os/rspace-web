import type {
  FieldName,
  RelationshipFilterPicker,
  ResolvedCollectionConfig,
  SearchSelector,
} from "@/modules/common/collection/config";
import { isRuntimeFieldInNamespace } from "@/modules/common/collection/utils";
import type { RelationshipSource } from "@/modules/common/relationship-picker/sources";
import {
  relationshipSources as builtInRelationshipSources,
  relationshipSourceForPicker,
} from "@/modules/common/relationship-picker/sources";
import type { CollectionQueryState, FilterExpression } from "../types";
import { LocalizedTableListError } from "../utils/errors";
import { serializeSorting } from "../utils/queryString";
import type { ApiV2CollectionMetadata } from "./metadata";
import { serializeRsql } from "./serializeRsql";

// The only current global-ID search selector is the Booking instrument target.
const GLOBAL_ID_PATTERN = /^IN(\d+)$/i;
const MAX_GLOBAL_ID_SUFFIX = "9223372036854775807";

function supportedGlobalId(value: string): string | null {
  const match = GLOBAL_ID_PATTERN.exec(value);
  if (!match) return null;
  const suffix = match[1].replace(/^0+(?=\d)/, "");
  if (
    suffix.length > MAX_GLOBAL_ID_SUFFIX.length ||
    (suffix.length === MAX_GLOBAL_ID_SUFFIX.length && suffix > MAX_GLOBAL_ID_SUFFIX)
  ) {
    return null;
  }
  return value.toUpperCase();
}

function compatiblePickerSource<TDocument>(
  config: ResolvedCollectionConfig<TDocument>,
  picker: RelationshipFilterPicker,
): RelationshipSource | undefined {
  const sources = { ...builtInRelationshipSources, ...config.relationshipSources };
  return relationshipSourceForPicker(picker, sources);
}

function ownedPickerValue(value: string, source: RelationshipSource): string | null {
  const normalized = source.normalizeValue ? source.normalizeValue(value) : source.ownsValue(value) ? value : null;
  return normalized !== null && source.ownsValue(normalized) ? normalized : null;
}

class InvalidPickerValueError extends Error {}

function normalizePickerValue(value: unknown, source: RelationshipSource, field: string): string {
  if (typeof value !== "string") {
    throw new InvalidPickerValueError(`Relationship filter value must be a string: ${field}`);
  }
  const normalized = ownedPickerValue(value, source);
  if (normalized === null || normalized === "") {
    throw new InvalidPickerValueError(`Invalid relationship filter value: ${field}`);
  }
  return normalized;
}

function normalizePickerExpression<TDocument>(
  expression: FilterExpression<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
  metadata: Pick<ApiV2CollectionMetadata<TDocument>, "filtering">,
  depth = 1,
): FilterExpression<TDocument> {
  if (depth > metadata.filtering.limits.maximumNesting) throw new Error("Filter nesting limit exceeded");
  if (expression.kind !== "comparison") {
    return {
      kind: expression.kind,
      children: expression.children.map((child) => normalizePickerExpression(child, config, metadata, depth + 1)),
    };
  }
  if (expression.operator === "exists") return expression;
  const picker = metadata.filtering.selectors[String(expression.field)]?.picker;
  if (!picker) return expression;
  const source = compatiblePickerSource(config, picker);
  // An unavailable or mismatched source keeps the existing typed filter path usable.
  if (!source) return expression;
  if (expression.operator === "in" || expression.operator === "notIn") {
    return {
      ...expression,
      value: Array.isArray(expression.value)
        ? expression.value.map((value) => normalizePickerValue(value, source, String(expression.field)))
        : expression.value,
    };
  }
  if (expression.operator !== "equals" && expression.operator !== "notEquals") return expression;
  return {
    ...expression,
    value: normalizePickerValue(expression.value, source, String(expression.field)),
  };
}

export function isPickerExpressionValid<TDocument>(
  expression: FilterExpression<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
  metadata: Pick<ApiV2CollectionMetadata<TDocument>, "filtering">,
): boolean {
  try {
    normalizePickerExpression(expression, config, metadata);
    return true;
  } catch (error) {
    if (error instanceof InvalidPickerValueError) return false;
    throw error;
  }
}

function searchExpression<TDocument>(
  config: ResolvedCollectionConfig<TDocument>,
  search: string,
): FilterExpression<TDocument> | null {
  const value = search.trim();
  const fields = config.listSearchableFields ?? [];
  if (value === "" || fields.length === 0) return null;
  const children: FilterExpression<TDocument>[] = [];
  for (const field of fields) {
    const selector = String(field);
    if (!selector.endsWith(".globalId")) {
      children.push({ kind: "comparison", field, operator: "contains", value });
      continue;
    }
    const globalId = supportedGlobalId(value);
    if (globalId === null) continue;
    const relationship = selector.slice(0, selector.lastIndexOf(".")) as SearchSelector<TDocument>;
    children.push({ kind: "comparison", field: relationship, operator: "equals", value: globalId });
  }
  if (children.length === 0) return null;
  return children.length === 1 ? children[0] : { kind: "or", children };
}

/**
 * The fields one request selects. The response carries these fields only, so the adapter validates
 * the response against these fields and no others.
 */
export function selectedFields<TDocument>(
  state: CollectionQueryState<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
  virtualFields: ReadonlySet<FieldName<TDocument>>,
  projectableFields: ReadonlySet<FieldName<TDocument>>,
): readonly FieldName<TDocument>[] {
  const fields = new Set<FieldName<TDocument>>([config.idField, config.useAsTitle]);
  const byName = new Map(config.fields.map((field) => [field.name, field]));
  for (const name of state.visibleFields) {
    if (virtualFields.has(name)) {
      const list = byName.get(name)?.list;
      if (list) for (const dependency of list.dependencies ?? []) fields.add(dependency);
    } else {
      fields.add(name);
    }
  }
  for (const name of [...fields]) {
    const field = byName.get(name);
    const list = field?.list;
    if (list) for (const dependency of list.dependencies ?? []) fields.add(dependency);
  }
  const selected = config.fields.map((field) => field.name).filter((name) => fields.has(name));
  for (const name of state.visibleFields) {
    if (fields.has(name) && !byName.has(name) && !virtualFields.has(name) && projectableFields.has(name)) {
      selected.push(name);
    }
  }
  return selected;
}

export function collectionQueryParams<TDocument>(
  state: CollectionQueryState<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  virtualFields: ReadonlySet<FieldName<TDocument>>,
  projectableFields: ReadonlySet<FieldName<TDocument>>,
  runtime: {
    projection: readonly string[];
    selectors: ApiV2CollectionMetadata<TDocument>["filtering"]["selectors"];
    projectionLimitMessage: (limit: number) => string;
  },
): URLSearchParams {
  const runtimeProjection = runtime.projection;
  if (state.page.pageIndex < 0) throw new Error("Page index must not be negative");
  if (state.page.pageSize <= 0 || state.page.pageSize > metadata.pagination.maximumLimit) {
    throw new Error(`Page size must be between 1 and ${metadata.pagination.maximumLimit}`);
  }
  if (state.sorting.length > metadata.sorting.maximumFields) throw new Error("Sort field limit exceeded");

  const allowedSorts = new Set(metadata.sorting.fields);
  for (const rule of state.sorting) {
    if (!allowedSorts.has(rule.field)) throw new Error(`Field is not sortable: ${rule.field}`);
  }

  const params = new URLSearchParams({
    page: String(state.page.pageIndex + 1),
    limit: String(state.page.pageSize),
  });
  if (state.sorting.length > 0) {
    params.set("sort", serializeSorting(state.sorting));
  }

  const search = searchExpression(config, state.filters.search);
  const filters = state.filters.expression
    ? normalizePickerExpression(state.filters.expression, config, metadata)
    : null;
  const expression =
    search && filters
      ? ({ kind: "and", children: [search, filters] } satisfies FilterExpression<TDocument>)
      : (search ?? filters);
  if (expression)
    params.set(
      "where",
      serializeRsql(expression, { ...metadata.filtering.selectors, ...runtime.selectors }, metadata.filtering.limits),
    );

  for (const namespace of metadata.runtimeFields ?? []) {
    const selected = runtimeProjection.filter((name) => isRuntimeFieldInNamespace(name, namespace.namespace));
    if (selected.length > namespace.maximumProjections) {
      throw new LocalizedTableListError(runtime.projectionLimitMessage(namespace.maximumProjections));
    }
  }
  params.set(
    `fields[${metadata.resourceName}]`,
    [...selectedFields(state, config, virtualFields, projectableFields), ...runtimeProjection].join(","),
  );
  return params;
}
