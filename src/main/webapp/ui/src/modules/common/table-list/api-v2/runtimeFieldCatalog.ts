import { type QueryKey, queryOptions, skipToken } from "@tanstack/react-query";
import { chunk, uniq } from "es-toolkit";
import type * as v from "valibot";
import type {
  ApiV2RequestOptions,
  ApiV2RuntimeFieldParams,
  ApiV2RuntimeFieldSearchOperation,
  ApiV2RuntimeFieldsOperation,
} from "@/modules/common/api/v2/apiV2Resources";
import { apiV2Resources } from "@/modules/common/api/v2/generated/apiV2Resources";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import type { ApiV2RuntimeFieldNamespace } from "./metadata";
import { catalogSchema, type runtimeFieldSchema, runtimeFieldSearchSchema } from "./schema";
import { runtimeFieldCatalogRoute } from "./schemaPrimitives";

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

/** A resource that publishes runtime-field catalogs for the namespaces it lists. */
export type RuntimeFieldCatalogResource<N extends string> = {
  readonly name: string;
  readonly runtimeFieldNamespaces?: readonly N[];
  readonly runtimeFields: ApiV2RuntimeFieldsOperation<N>;
};

/** One namespace's catalog, bound to a resource that publishes that namespace. */
export type RuntimeFieldCatalog = {
  readonly resourceName: string;
  readonly queryKey: (params?: ApiV2RuntimeFieldParams) => QueryKey;
  readonly request: (params?: ApiV2RuntimeFieldParams, options?: ApiV2RequestOptions) => Promise<unknown>;
};

/** A resource that searches several of its runtime-field catalogs in one request. */
export type RuntimeFieldSearchResource<N extends string> = {
  readonly name: string;
  readonly runtimeFieldNamespaces?: readonly N[];
  readonly runtimeFieldSearch: ApiV2RuntimeFieldSearchOperation<N>;
};

export type RuntimeFieldSearchParams = { q: string; limit?: number };

/** Several catalogs of one resource, searched in one request. */
export type RuntimeFieldSearch = {
  readonly resourceName: string;
  /** The distinct catalog namespaces requested, each published by the resource. */
  readonly namespaces: readonly string[];
  readonly queryKey: (params: RuntimeFieldSearchParams) => QueryKey;
  readonly request: (params: RuntimeFieldSearchParams, options?: ApiV2RequestOptions) => Promise<unknown>;
};

/** The filter and column selector of a runtime field: its selector namespace, a dot, then its catalog ID. */
export function runtimeFieldSelector(namespace: string, id: string): string {
  return `${namespace}.${id}`;
}

/** The catalog namespace named by a selector namespace's metadata entry, such as `target.customFields`. */
export function runtimeFieldCatalogNamespace(namespace: string): string {
  return namespace.slice(namespace.lastIndexOf(".") + 1);
}

/** The resource whose runtime-field route, `/api/v2/<resource>/fields/<namespace>`, is this catalog. */
function catalogResourceName({ namespace, catalog }: RuntimeFieldCatalogNamespace): string | undefined {
  const route = runtimeFieldCatalogRoute.exec(catalog);
  return route && route[2] === runtimeFieldCatalogNamespace(namespace) ? route[1] : undefined;
}

/** The resource's catalog for a selector namespace, or `undefined` when the resource does not publish it. */
export function bindRuntimeFieldCatalog<N extends string>(
  resource: RuntimeFieldCatalogResource<N>,
  namespace: string,
): RuntimeFieldCatalog | undefined {
  const catalogNamespace = runtimeFieldCatalogNamespace(namespace);
  const published = resource.runtimeFieldNamespaces?.find((candidate) => candidate === catalogNamespace);
  if (published === undefined) return undefined;
  const operation = resource.runtimeFields;
  return {
    resourceName: resource.name,
    queryKey: (params) => operation.queryKey(published, params),
    request: (params, options) => operation.request(published, params, options),
  };
}

/**
 * The resource's search over the catalogs of these selector namespaces, keeping each distinct catalog namespace the
 * resource publishes and dropping the rest; `undefined` when none remains.
 */
export function bindRuntimeFieldSearch<N extends string>(
  resource: RuntimeFieldSearchResource<N>,
  namespaces: readonly string[],
): RuntimeFieldSearch | undefined {
  const published = uniq(namespaces.map(runtimeFieldCatalogNamespace)).flatMap(
    (namespace) => resource.runtimeFieldNamespaces?.filter((candidate) => candidate === namespace) ?? [],
  );
  if (published.length === 0) return undefined;
  const operation = resource.runtimeFieldSearch;
  return {
    resourceName: resource.name,
    namespaces: published,
    queryKey: (params) => operation.queryKey({ ...params, namespace: published }),
    request: (params, options) => operation.request({ ...params, namespace: published }, options),
  };
}

/** The registered resource with this name, if any. */
function registeredResource(name: string | undefined) {
  return Object.values(apiV2Resources).find((resource) => resource.name === name);
}

/** The registered resource's catalog for a namespace, if that resource publishes the namespace's route. */
export function registeredRuntimeFieldCatalog(
  namespace: RuntimeFieldCatalogNamespace,
): RuntimeFieldCatalog | undefined {
  const resource = registeredResource(catalogResourceName(namespace));
  return resource !== undefined && "runtimeFields" in resource
    ? bindRuntimeFieldCatalog(resource, namespace.namespace)
    : undefined;
}

/**
 * One search per registered resource that the sources' catalog routes resolve to, with the sources it answers.
 * Sources whose route names no registered resource are skipped.
 */
export function registeredRuntimeFieldSearches<S extends RuntimeFieldCatalogNamespace>(
  sources: readonly S[],
): readonly { search: RuntimeFieldSearch; sources: readonly S[] }[] {
  return [...Map.groupBy(sources, catalogResourceName)].flatMap(([name, group]) => {
    const resource = registeredResource(name);
    if (resource === undefined || !("runtimeFieldSearch" in resource)) return [];
    const search = bindRuntimeFieldSearch(
      resource,
      group.map((source) => source.namespace),
    );
    if (!search) return [];
    const answered = group.filter((source) =>
      search.namespaces.includes(runtimeFieldCatalogNamespace(source.namespace)),
    );
    return [{ search, sources: answered }];
  });
}

/**
 * The only search among these, if any. A picker's sources share one relationship path, which one resource serves, so
 * a second search means the sources were grouped wrongly.
 */
export function soleRuntimeFieldSearch<S>(
  searches: readonly { search: RuntimeFieldSearch; sources: readonly S[] }[],
): { search: RuntimeFieldSearch; sources: readonly S[] } | undefined {
  if (searches.length > 1) {
    const names = searches.map(({ search }) => search.resourceName).join(", ");
    throw new Error(`Runtime field sources span more than one resource: ${names}`);
  }
  return searches.at(0);
}

/** Each requested namespace's catalog page; a namespace missing from the response fails decoding. */
export function mapRuntimeFieldSearch(
  body: unknown,
  namespaces: readonly string[],
): ReadonlyMap<string, RuntimeFieldCatalogPage> {
  const { catalogs } = parseOrThrow(runtimeFieldSearchSchema, body);
  return new Map(
    namespaces.map((namespace) => {
      const catalog = catalogs[namespace];
      if (catalog === undefined) throw new Error(`The runtime field search response has no ${namespace} catalog`);
      return [namespace, catalog] as const;
    }),
  );
}

/** A runtime field offered by a picker, with the selector namespace it is chosen under. */
export type RuntimeFieldOption = {
  namespace: string;
  definition: RuntimeFieldDefinition;
};

/**
 * The search for runtime fields matching `params` across a picker's sources, each result under every source whose
 * catalog holds it. It runs only when `enabled` and a search is available.
 */
export function runtimeFieldSearchQueryOptions(
  runtimeSearch: { search: RuntimeFieldSearch; sources: readonly { namespace: string }[] } | undefined,
  params: RuntimeFieldSearchParams,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: runtimeSearch?.search.queryKey(params) ?? ["runtimeFieldSearch", "unavailable"],
    queryFn:
      runtimeSearch && enabled
        ? ({ signal }) => runtimeSearch.search.request(params, { signal, redirect: "error" })
        : skipToken,
    select: (body): readonly RuntimeFieldOption[] => {
      if (!runtimeSearch) return [];
      const catalogs = mapRuntimeFieldSearch(body, runtimeSearch.search.namespaces);
      return runtimeSearch.sources.flatMap((source) =>
        (catalogs.get(runtimeFieldCatalogNamespace(source.namespace))?.fields ?? []).map((definition) => ({
          namespace: source.namespace,
          definition,
        })),
      );
    },
    staleTime: 60_000,
  });
}

function selectionParams(request: RuntimeFieldCatalogRequest) {
  if (request.ids?.length) return { ids: request.ids.join(",") };
  if (request.search) return { q: request.search };
  return {};
}

export async function fetchRuntimeFieldCatalog(
  catalog: RuntimeFieldCatalog,
  namespace: RuntimeFieldCatalogNamespace,
  request: RuntimeFieldCatalogRequest = {},
): Promise<RuntimeFieldCatalogPage> {
  if (catalogResourceName(namespace) !== catalog.resourceName)
    throw new Error("Unsupported runtime field catalog route");
  const params = {
    ...selectionParams(request),
    ...(request.page === undefined ? {} : { page: request.page }),
    ...(request.limit === undefined ? {} : { limit: request.limit }),
  };
  const body = await catalog.request(params, { signal: request.signal, redirect: "error" });
  // UI catalog policy requires complete definitions and supported filter operators.
  return parseOrThrow(catalogSchema, body);
}

export async function fetchRuntimeFieldDefinitions(
  catalog: RuntimeFieldCatalog,
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
    chunk(ids, maximum).map((batch) => fetchRuntimeFieldCatalog(catalog, namespace, { ...request, ids: batch })),
  );
  const byId = new Map(pages.flatMap((page) => page.fields).map((field) => [field.id, field] as const));
  return ids.flatMap((id) => {
    const field = byId.get(id);
    return field === undefined ? [] : [field];
  });
}

export type RuntimeFieldCatalogForNamespace = { namespace: string; definitions: readonly RuntimeFieldDefinition[] };
