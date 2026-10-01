import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import type { ApiV2FilterOperator, ApiV2RuntimeFieldNamespace } from "./apiV2CollectionMetadata";

const filterOperator = v.picklist([
  "==",
  "!=",
  "=gt=",
  "=ge=",
  "=lt=",
  "=le=",
  "=in=",
  "=out=",
  "=contains=",
  "=like=",
  "=exists=",
] as const satisfies readonly ApiV2FilterOperator[]);

const runtimeFieldSchema = v.object({
  id: v.pipe(v.string(), v.minLength(1)),
  selector: v.pipe(v.string(), v.minLength(1)),
  label: v.string(),
  type: v.picklist(["text", "number", "date", "time", "radio", "choice"] as const),
  jsonType: v.picklist(["string", "number", "array"] as const),
  operators: v.array(filterOperator),
  supportsWildcards: v.boolean(),
  columnSelectable: v.boolean(),
  sortable: v.boolean(),
  source: v.object({ id: v.string(), label: v.string() }),
  options: v.array(v.string()),
});

const catalogSchema = v.object({
  fields: v.array(runtimeFieldSchema),
  totalFields: v.optional(v.number()),
  hasMore: v.boolean(),
  page: v.number(),
  limit: v.number(),
});

export type RuntimeFieldDefinition = v.InferOutput<typeof runtimeFieldSchema>;

export const runtimeFieldValuesSchema = v.record(
  v.string(),
  v.nullable(v.union([v.string(), v.number(), v.array(v.string())])),
);

export type RuntimeFieldValues = v.InferOutput<typeof runtimeFieldValuesSchema>;

export type RuntimeFieldCatalogPage = {
  fields: readonly RuntimeFieldDefinition[];
  totalFields?: number;
  hasMore: boolean;
  page: number;
  limit: number;
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

export async function fetchRuntimeFieldCatalog(
  namespace: Pick<ApiV2RuntimeFieldNamespace, "catalog">,
  request: RuntimeFieldCatalogRequest = {},
): Promise<RuntimeFieldCatalogPage> {
  const headers = new Headers(request.headers);
  headers.set("X-Requested-With", "XMLHttpRequest");
  const parameters = new URLSearchParams();
  if (request.ids && request.ids.length > 0) parameters.set("ids", request.ids.join(","));
  else if (request.search) parameters.set("search", request.search);
  if (request.page !== undefined) parameters.set("page", String(request.page));
  if (request.limit !== undefined) parameters.set("limit", String(request.limit));
  const query = parameters.toString();
  const response = await (request.fetch ?? globalThis.fetch)(
    query === "" ? namespace.catalog : `${namespace.catalog}?${query}`,
    { headers, signal: request.signal },
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
