import type { ApiV2ReadOperation, ApiV2ResourceEntry } from "@/modules/common/api/v2/apiV2Resources";
import type { FieldName } from "@/modules/common/collection/config";
import type { CollectionFetcher, CollectionQueryState, FilterExpression } from "../types";
import type { ApiV2CollectionAdapter } from "./adapter";

export type ApiV2CollectionProjection<TDocument> = "visible" | { fixed: readonly FieldName<TDocument>[] };

export type ApiV2CollectionFetchOptions<TDocument> = {
  /** Request a total alongside this page. Normal page requests omit totals. */
  totals?: boolean;
  depth?: number;
  /** Identity used for selection resets, never query keys. */
  authScope?: string | number;
  projection?: ApiV2CollectionProjection<TDocument>;
  baseFilter?: FilterExpression<TDocument>;
  validateRows?: (rows: readonly TDocument[]) => void;
};

export function projectedState<TDocument>(
  adapter: ApiV2CollectionAdapter<TDocument>,
  state: CollectionQueryState<TDocument>,
  projection: ApiV2CollectionProjection<TDocument>,
): CollectionQueryState<TDocument> {
  if (projection === "visible") return state;
  const runtime = state.visibleFields.filter((field) => adapter.isRuntimeSelector(String(field)));
  return { ...state, visibleFields: [...projection.fixed, ...runtime] };
}

export function apiV2CollectionRequestParams<TDocument>(
  adapter: ApiV2CollectionAdapter<TDocument>,
  state: CollectionQueryState<TDocument>,
  depth?: number,
  projection: ApiV2CollectionProjection<TDocument> = "visible",
  baseFilter?: FilterExpression<TDocument>,
): URLSearchParams {
  const expression =
    baseFilter && state.filters.expression
      ? ({ kind: "and", children: [baseFilter, state.filters.expression] } satisfies FilterExpression<TDocument>)
      : (baseFilter ?? state.filters.expression);
  const filteredState = { ...state, filters: { ...state.filters, expression } };
  const requestState = projectedState(adapter, filteredState, projection);
  const parameters = adapter.toSearchParams(requestState);
  const requiredDepth = adapter.requiredDepth(requestState);
  if (depth !== undefined || requiredDepth > 0) parameters.set("depth", String(Math.max(depth ?? 0, requiredDepth)));
  return parameters;
}

/** Convert the table's serialized state into the generated collection parameter object. */
export function apiV2CollectionParameters(parameters: URLSearchParams) {
  const result: Record<string, unknown> = {};
  for (const [key, value] of parameters) {
    if (value === "") continue;
    const fieldset = /^(fields|exclude)\[(.+)\]$/.exec(key);
    if (fieldset) {
      const selection = result[fieldset[1]] ?? {};
      result[fieldset[1]] = { ...Object(selection), [fieldset[2]]: value };
    } else
      result[key] = ["page", "limit", "depth", "max"].includes(key)
        ? Number(value)
        : key === "totals"
          ? value === "true"
          : value;
  }
  return result;
}

export function createApiV2CollectionFetcher<
  TDocument,
  TEntry extends ApiV2ResourceEntry<string, { list: ApiV2ReadOperation }>,
>(
  adapter: ApiV2CollectionAdapter<TDocument>,
  resource: TEntry,
  options: ApiV2CollectionFetchOptions<TDocument> = {},
): CollectionFetcher<TDocument> {
  return async (state, { signal }) => {
    const requestState = projectedState(adapter, state, options.projection ?? "visible");
    const selected = adapter.selectedFields(requestState);
    const parameters = apiV2CollectionRequestParams(
      adapter,
      requestState,
      options.depth,
      "visible",
      options.baseFilter,
    );
    if (options.totals) parameters.set("totals", "true");
    const args = [apiV2CollectionParameters(parameters), { signal }];
    const body = await resource.list.request(...(args as never[]));
    const page = adapter.parseResponse(body, selected);
    options.validateRows?.(page.rows);
    return page;
  };
}
