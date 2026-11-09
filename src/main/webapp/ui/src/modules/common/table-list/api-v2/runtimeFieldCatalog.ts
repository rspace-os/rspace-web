import type * as v from "valibot";
import {
  type ApiV2ReadOperation,
  type ApiV2ResourceEntry,
  apiV2Resources,
} from "@/modules/common/api/v2/apiV2Resources";
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

type RuntimeFieldCatalogNamespace = Pick<ApiV2RuntimeFieldNamespace, "namespace" | "catalog">;
type MaybeRuntimeFieldResource = ApiV2ResourceEntry<string, { runtimeFields?: ApiV2ReadOperation }>;
export type RuntimeFieldCatalogResource = ApiV2ResourceEntry<string, { runtimeFields: ApiV2ReadOperation }>;

/** The catalog namespace named by a selector namespace's metadata entry, such as `target.customFields`. */
export function runtimeFieldCatalogNamespace(namespace: string): string {
  return namespace.slice(namespace.lastIndexOf(".") + 1);
}

/** The resource whose runtime-field route, `/api/v2/<resource>/fields/<namespace>`, is this catalog. */
function catalogResourceName({ namespace, catalog }: RuntimeFieldCatalogNamespace): string | undefined {
  const route = /^\/api\/v2\/([a-z][a-z0-9-]*)\/fields\/([A-Za-z][A-Za-z0-9_-]*)$/.exec(catalog);
  return route && route[2] === runtimeFieldCatalogNamespace(namespace) ? route[1] : undefined;
}

/** Rejects a catalog that is not the runtime-field route of its namespace. */
export function validateRuntimeFieldCatalog(namespace: RuntimeFieldCatalogNamespace): void {
  if (catalogResourceName(namespace) === undefined) throw new Error("Unsupported runtime field catalog route");
}

/** The registered resource that publishes a namespace's catalog, if it publishes runtime fields at all. */
export function runtimeFieldCatalogResource(
  namespace: RuntimeFieldCatalogNamespace,
): RuntimeFieldCatalogResource | undefined {
  const name = catalogResourceName(namespace);
  const resource = name === undefined ? undefined : (apiV2Resources as Record<string, MaybeRuntimeFieldResource>)[name];
  return resource?.runtimeFields ? (resource as RuntimeFieldCatalogResource) : undefined;
}

export async function fetchRuntimeFieldCatalog<
  TEntry extends ApiV2ResourceEntry<string, { runtimeFields?: ApiV2ReadOperation }>,
>(
  resource: TEntry,
  namespace: RuntimeFieldCatalogNamespace,
  request: RuntimeFieldCatalogRequest = {},
): Promise<RuntimeFieldCatalogPage> {
  if (catalogResourceName(namespace) !== resource.name) throw new Error("Unsupported runtime field catalog route");
  if (!resource.runtimeFields) throw new Error(`Resource ${resource.name} has no runtime field catalog`);
  const fieldNamespace = runtimeFieldCatalogNamespace(namespace.namespace);
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
  namespace: Pick<ApiV2RuntimeFieldNamespace, "namespace" | "catalog" | "catalogMaximumIds">,
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
