import type { FieldName } from "@/modules/common/collection/collectionConfig";
import type { CollectionFetcher, CollectionQueryState, FilterExpression } from "../../tableListState";
import type { ApiV2CollectionAdapter } from "./createApiV2CollectionAdapter";

export type ApiV2CollectionProjection<TDocument> = "visible" | { fixed: readonly FieldName<TDocument>[] };

export type ApiV2CollectionFetchOptions<TDocument> = {
  endpoint?: string;
  depth?: number;
  token?: string | (() => string | Promise<string>);
  headers?: HeadersInit | (() => HeadersInit | Promise<HeadersInit>);
  fetch?: typeof globalThis.fetch;
  projection?: ApiV2CollectionProjection<TDocument>;
  baseFilter?: FilterExpression<TDocument>;
  validateRows?: (rows: readonly TDocument[]) => void;
};

async function resolveValue<T>(value: T | (() => T | Promise<T>) | undefined): Promise<T | undefined> {
  return typeof value === "function" ? (value as () => T | Promise<T>)() : value;
}

function projectedState<TDocument>(
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

export function createApiV2CollectionFetcher<TDocument>(
  adapter: ApiV2CollectionAdapter<TDocument>,
  options: ApiV2CollectionFetchOptions<TDocument> = {},
): CollectionFetcher<TDocument> {
  const endpoint = options.endpoint ?? `/api/v2/${adapter.metadata.resourceName}`;
  const request = options.fetch ?? globalThis.fetch;
  return async (state, { signal }) => {
    const headers = new Headers(await resolveValue(options.headers));
    headers.set("X-Requested-With", "XMLHttpRequest");
    const token = await resolveValue(options.token);
    if (token) headers.set("Authorization", `Bearer ${token}`);
    const requestState = projectedState(adapter, state, options.projection ?? "visible");
    const selected = adapter.selectedFields(requestState);
    const parameters = apiV2CollectionRequestParams(
      adapter,
      requestState,
      options.depth,
      "visible",
      options.baseFilter,
    );
    const response = await request(`${endpoint}?${parameters}`, {
      method: "GET",
      headers,
      signal,
    });
    if (!response.ok) throw new Error(`API V2 collection request failed with status ${response.status}`);
    const body: unknown = await response.json();
    const page = adapter.parseResponse(body, selected);
    options.validateRows?.(page.rows);
    return page;
  };
}
