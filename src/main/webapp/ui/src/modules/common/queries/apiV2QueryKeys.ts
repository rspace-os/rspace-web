import { queryOptions } from "@tanstack/react-query";

export const apiV2Root = <R extends string>(resource: R) => [`rspace.api.v2.${resource}`] as const;

export const apiV2ListKey = (resource: string, endpoint: string, scope: unknown, parameters: unknown) =>
  [...apiV2Root(resource), "list", scope, endpoint, parameters] as const;

export function apiV2ListOptions<T>(
  resource: string,
  endpoint: string,
  scope: unknown,
  parameters: unknown,
  fetcher: (context: { signal: AbortSignal }) => Promise<T>,
) {
  return queryOptions({ queryKey: apiV2ListKey(resource, endpoint, scope, parameters), queryFn: fetcher });
}

export const apiV2MetadataKey = (resource: string) => [...apiV2Root("openapi"), resource] as const;

export const apiV2RuntimeFieldKey = (operation: "ids" | "search", ...parameters: readonly unknown[]) =>
  [...apiV2Root("runtime-fields"), operation, ...parameters] as const;

export function apiV2RuntimeFieldOptions<T>(
  operation: "ids" | "search",
  parameters: readonly unknown[],
  fetcher: (context: { signal: AbortSignal }) => Promise<T>,
) {
  return queryOptions({
    queryKey: apiV2RuntimeFieldKey(operation, ...parameters),
    queryFn: fetcher,
    ...(operation === "ids" ? { retry: false } : {}),
    staleTime: 60_000,
  });
}
