import type * as v from "valibot";
import type { ApiV2ReadOperation, ApiV2ResourceEntry } from "@/modules/common/api/v2/apiV2Resources";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
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
  signal?: AbortSignal;
};

/** Published runtime field catalog routes. */
export function validateRuntimeFieldCatalogUrl(catalog: string): void {
  if (catalog !== "/api/v2/instruments/fields/customFields" && catalog !== "/api/v2/instruments/fields/extraFields") {
    throw new Error("Unsupported runtime field catalog route");
  }
}

export async function fetchRuntimeFieldCatalog<
  TEntry extends ApiV2ResourceEntry<string, { runtimeFields?: ApiV2ReadOperation }>,
>(
  resource: TEntry,
  namespace: Pick<ApiV2RuntimeFieldNamespace, "catalog">,
  request: RuntimeFieldCatalogRequest = {},
): Promise<RuntimeFieldCatalogPage> {
  validateRuntimeFieldCatalogUrl(namespace.catalog);
  if (!resource.runtimeFields) throw new Error(`Resource ${resource.name} has no runtime field catalog`);
  const fieldNamespace = namespace.catalog.endsWith("/customFields") ? "customFields" : "extraFields";
  const params = {
    ...(request.ids?.length ? { ids: request.ids.join(",") } : request.search ? { q: request.search } : {}),
    ...(request.page === undefined ? {} : { page: request.page }),
    ...(request.limit === undefined ? {} : { limit: request.limit }),
  };
  const body = await resource.runtimeFields.request(
    ...([fieldNamespace, params, { signal: request.signal, redirect: "error" }] as never[]),
  );
  return mapRuntimeFieldCatalog(body);
}

/** UI catalog policy requires complete definitions and supported filter operators. */
export function mapRuntimeFieldCatalog(body: unknown): RuntimeFieldCatalogPage {
  return parseOrThrow(catalogSchema, body);
}

export async function fetchRuntimeFieldDefinitions<
  TEntry extends ApiV2ResourceEntry<string, { runtimeFields?: ApiV2ReadOperation }>,
>(
  resource: TEntry,
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
      fetchRuntimeFieldCatalog(resource, namespace, {
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

export type RuntimeFieldCatalogForNamespace = { namespace: string; definitions: readonly RuntimeFieldDefinition[] };
