import type { FieldName, ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import type { CollectionQueryState, FilterExpression } from "../../tableListState";
import type { ApiV2CollectionMetadata } from "./apiV2CollectionMetadata";
import { serializeRsql } from "./rsql/serializeRsql";

function searchExpression<TDocument>(
  config: ResolvedCollectionConfig<TDocument>,
  search: string,
): FilterExpression<TDocument> | null {
  const value = search.trim();
  const fields = config.listSearchableFields ?? [];
  if (value === "" || fields.length === 0) return null;
  return {
    kind: "or",
    children: fields.map((field) => ({ kind: "comparison", field, operator: "contains", value })),
  };
}

/**
 * The fields one request selects. The response carries these fields only, so the adapter validates
 * the response against these fields and no others.
 */
export function selectedFields<TDocument>(
  state: CollectionQueryState<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
  virtualFields: ReadonlySet<FieldName<TDocument>> = new Set(),
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
  return config.fields.map((field) => field.name).filter((name) => fields.has(name));
}

export function collectionQueryParams<TDocument>(
  state: CollectionQueryState<TDocument>,
  config: ResolvedCollectionConfig<TDocument>,
  metadata: ApiV2CollectionMetadata<TDocument>,
  virtualFields: ReadonlySet<FieldName<TDocument>> = new Set(),
): URLSearchParams {
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
    params.set("sort", state.sorting.map((rule) => `${rule.direction === "desc" ? "-" : ""}${rule.field}`).join(","));
  }

  const search = searchExpression(config, state.filters.search);
  const expression =
    search && state.filters.expression
      ? ({ kind: "and", children: [search, state.filters.expression] } satisfies FilterExpression<TDocument>)
      : (search ?? state.filters.expression);
  if (expression)
    params.set("where", serializeRsql(expression, metadata.filtering.selectors, metadata.filtering.limits));

  params.set(`fields[${metadata.resourceName}]`, selectedFields(state, config, virtualFields).join(","));
  return params;
}
