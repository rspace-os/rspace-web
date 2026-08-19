import type { FieldName } from "@/modules/common/collection/collectionConfig";
import type { CollectionFetcher, CollectionQueryState } from "../../tableListState";
import type { ApiV2CollectionAdapter } from "./createApiV2CollectionAdapter";

export type ApiV2CollectionProjection<TDocument> = "visible" | { fixed: readonly FieldName<TDocument>[] };

export type ApiV2CollectionFetchOptions<TDocument> = {
  endpoint?: string;
  depth?: number;
  token?: string | (() => string | Promise<string>);
  headers?: HeadersInit | (() => HeadersInit | Promise<HeadersInit>);
  fetch?: typeof globalThis.fetch;
  projection?: ApiV2CollectionProjection<TDocument>;
};

async function resolveValue<T>(value: T | (() => T | Promise<T>) | undefined): Promise<T | undefined> {
  return typeof value === "function" ? (value as () => T | Promise<T>)() : value;
}

export function apiV2CollectionRequestParams<TDocument>(
  adapter: ApiV2CollectionAdapter<TDocument>,
  state: CollectionQueryState<TDocument>,
  depth?: number,
  projection: ApiV2CollectionProjection<TDocument> = "visible",
): URLSearchParams {
  const requestState = projection === "visible" ? state : { ...state, visibleFields: projection.fixed };
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
    const requestState =
      options.projection && options.projection !== "visible"
        ? { ...state, visibleFields: options.projection.fixed }
        : state;
    const selected = adapter.selectedFields(requestState);
    const parameters = apiV2CollectionRequestParams(adapter, requestState, options.depth);
    const response = await request(`${endpoint}?${parameters}`, {
      method: "GET",
      headers,
      signal,
    });
    if (!response.ok) throw new Error(`API V2 collection request failed with status ${response.status}`);
    const body: unknown = await response.json();
    return adapter.parseResponse(body, selected);
  };
}
