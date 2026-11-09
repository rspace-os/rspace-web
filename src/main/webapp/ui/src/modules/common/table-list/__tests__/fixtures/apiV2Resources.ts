import type { ApiV2OperationSet, ApiV2ResourceEntry } from "@/modules/common/api/v2/apiV2Resources";

export function fakeCollectionResource(name: string) {
  return {
    name,
    runtimeFields: {
      queryKey: (namespace: string, params?: Record<string, unknown>) =>
        [`/api/v2/${name}/fields/${namespace}`, params] as const,
      request: async (namespace: string, params: Record<string, unknown> = {}, options?: RequestInit) => {
        const query = new URLSearchParams(
          Object.entries(params)
            .filter(([, value]) => value !== undefined)
            .map(([key, value]) => [key, String(value)]),
        );
        const response = await fetch(`/api/v2/${name}/fields/${namespace}?${query}`, options);
        if (!response.ok) throw new Error(`Catalog request failed with status ${response.status}`);
        return response.json() as Promise<unknown>;
      },
    },
    list: {
      queryKey: (params?: Record<string, unknown>) => [`/api/v2/${name}`, ...(params ? [params] : [])] as const,
      request: async (params: Record<string, unknown> = {}, options?: RequestInit) => {
        const search = new URLSearchParams();
        for (const [key, value] of Object.entries(params)) {
          if (typeof value === "object" && value !== null) {
            for (const [field, selection] of Object.entries(value)) search.set(`${key}[${field}]`, String(selection));
          } else if (value !== undefined) search.set(key, String(value));
        }
        const response = await fetch(`/api/v2/${name}?${search}`, options);
        if (!response.ok) throw new Error(`Collection request failed with status ${response.status}`);
        return response.json() as Promise<unknown>;
      },
    },
  } satisfies ApiV2ResourceEntry<string, ApiV2OperationSet>;
}
