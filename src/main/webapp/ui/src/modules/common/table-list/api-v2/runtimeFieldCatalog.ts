import type * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import type { ApiV2RuntimeFieldNamespace } from "./metadata";
import { catalogSchema, type runtimeFieldSchema } from "./schema";

export type RuntimeFieldDefinition = v.InferOutput<typeof runtimeFieldSchema>;

export type RuntimeFieldCatalogPage = Omit<v.InferOutput<typeof catalogSchema>, "fields"> & {
  fields: Readonly<v.InferOutput<typeof catalogSchema>["fields"]>;
};

export type RuntimeFieldCatalogRequest = {
  search?: string;
  ids?: readonly string[];
  page?: number;
  limit?: number;
  fetch?: typeof globalThis.fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
};

/** Published runtime field catalog routes. */
export function validateRuntimeFieldCatalogUrl(catalog: string): void {
  if (catalog !== "/api/v2/instruments/fields/customFields" && catalog !== "/api/v2/instruments/fields/extraFields") {
    throw new Error("Unsupported runtime field catalog route");
  }
}

export async function fetchRuntimeFieldCatalog(
  namespace: Pick<ApiV2RuntimeFieldNamespace, "catalog">,
  request: RuntimeFieldCatalogRequest = {},
): Promise<RuntimeFieldCatalogPage> {
  validateRuntimeFieldCatalogUrl(namespace.catalog);
  const headers = new Headers(request.headers);
  headers.set("X-Requested-With", "XMLHttpRequest");
  const parameters = new URLSearchParams();
  if (request.ids && request.ids.length > 0) parameters.set("ids", request.ids.join(","));
  else if (request.search) parameters.set("search", request.search);
  if (request.page !== undefined) parameters.set("page", String(request.page));
  if (request.limit !== undefined) parameters.set("limit", String(request.limit));
  const query = parameters.toString();
  const response = await (request.fetch ?? fetchWithUiToken)(
    query === "" ? namespace.catalog : `${namespace.catalog}?${query}`,
    { headers, signal: request.signal, redirect: "error" },
  );
  if (!response.ok) throw new Error(`Runtime field catalog request failed with status ${response.status}`);
  const body: unknown = await response.json();
  return parseOrThrow(catalogSchema, body);
}

export async function fetchRuntimeFieldDefinitions(
  namespace: Pick<ApiV2RuntimeFieldNamespace, "catalog" | "catalogMaximumIds">,
  ids: readonly string[],
  request: Omit<RuntimeFieldCatalogRequest, "ids" | "page" | "limit" | "search"> = {},
): Promise<readonly RuntimeFieldDefinition[]> {
  if (ids.length === 0) return [];
  const maximum = namespace.catalogMaximumIds;
  if (!Number.isInteger(maximum) || maximum < 1) {
    throw new Error("Runtime field catalog maximum IDs must be positive");
  }
  const pages = await Promise.all(
    Array.from({ length: Math.ceil(ids.length / maximum) }, (_, index) =>
      fetchRuntimeFieldCatalog(namespace, {
        ...request,
        ids: ids.slice(index * maximum, (index + 1) * maximum),
      }),
    ),
  );
  const byId = new Map(pages.flatMap((page) => page.fields).map((field) => [field.id, field] as const));
  return ids.flatMap((id) => {
    const field = byId.get(id);
    return field === undefined ? [] : [field];
  });
}
