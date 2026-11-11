import {
  type QueryClient,
  skipToken,
  type UseQueryResult,
  useQueries,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { chunk } from "es-toolkit";
import { useMemo } from "react";
import { useQueryIdentityScope } from "@/modules/common/queries/queryIdentityScope";
import type {
  RelationshipOption,
  RelationshipOptionAvailabilitySource,
} from "@/modules/common/relationship-picker/types";
import type { RelationshipOptionContext, RelationshipOptionWithSource, RelationshipSource } from "./sources";
import { databaseIdFromGlobalId } from "./sources";

/** The numeric ID inside a global ID, or null when the prefix belongs to another resource. */
export function databaseId(source: RelationshipSource, globalId: string): number | null {
  return source.globalIdPrefix === undefined ? null : databaseIdFromGlobalId(globalId, source.globalIdPrefix);
}

// Module-level and data-only, so TanStack's structural sharing keeps `data` stable between renders.
function combineSearches(results: UseQueryResult<readonly unknown[]>[]) {
  return {
    data: results.map((query) => query.data),
    failed: results.some((query) => query.isError),
    loading: results.some((query) => query.isFetching),
  };
}

export function useRelationshipOptions({
  sources,
  term,
  labels,
  authScope,
  enabled = true,
}: {
  sources: readonly RelationshipSource[];
  term: string;
  authScope?: string | number;
  labels: RelationshipOptionContext;
  enabled?: boolean;
}) {
  const scope = JSON.stringify([useQueryIdentityScope(authScope), authScope ?? null]);
  const queryClient = useQueryClient();
  const { data, failed, loading } = useQueries({
    queries: sources.map((candidate) => ({
      queryKey: ["relationship-options", candidate.id, scope, term.trim()],
      enabled,
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        const documents = await candidate.search(term.trim(), signal);
        for (const document of documents) {
          const option = candidate.toOption(document, labels);
          queryClient.setQueryData(
            optionCacheKey(candidate, canonicalValue(candidate, String(option.value)) ?? String(option.value), scope),
            document,
          );
        }
        return documents;
      },
    })),
    combine: combineSearches,
  });
  // Base UI's Combobox compares items by reference, so options keep their identity between renders.
  const options = useMemo<RelationshipOptionWithSource[]>(
    () =>
      sources.flatMap((candidate, index) =>
        (data[index] ?? []).map((document) => ({ ...candidate.toOption(document, labels), sourceId: candidate.id })),
      ),
    [sources, data, labels],
  );
  return { options, failed, loading };
}

export function useRelationshipOptionAvailability({
  sourceId,
  options,
  availabilitySource,
  authScope,
}: {
  sourceId: string;
  options: readonly RelationshipOption[];
  availabilitySource: RelationshipOptionAvailabilitySource | undefined;
  authScope?: string | number;
}) {
  const scope = JSON.stringify([useQueryIdentityScope(authScope), authScope ?? null]);
  const values = [...new Set(options.map((option) => String(option.value)))].sort();
  const query = useQuery({
    queryKey: [...(availabilitySource?.queryKey ?? []), sourceId, scope, ...values],
    enabled: values.length > 0,
    staleTime: 15_000,
    gcTime: 5 * 60_000,
    queryFn:
      availabilitySource === undefined ? skipToken : ({ signal }) => availabilitySource.loadUnavailable(values, signal),
  });

  return {
    unavailable: query.data ?? {},
    checking: availabilitySource !== undefined && values.length > 0 && query.isPending,
    failed: query.isError,
  };
}

export function optionCacheKey(source: RelationshipSource | undefined, value: string, scope: string | number | null) {
  return ["relationship-option", source?.id ?? "unknown", scope, value] as const;
}

export function canonicalValue(source: RelationshipSource, value: string): string | null {
  return source.normalizeValue === undefined ? value : source.normalizeValue(value);
}

export function optionBatchCacheKey(
  source: RelationshipSource,
  values: readonly string[],
  scope: string | number | null,
) {
  return ["relationship-option-batch", source.id, scope, ...values] as const;
}

const MAX_RESOLVE_BATCH_SIZE = 100;

function cacheEntryIsFresh(queryClient: QueryClient, key: readonly unknown[]) {
  const state = queryClient.getQueryState(key);
  return state?.data !== undefined && Date.now() - state.dataUpdatedAt < 15_000;
}

function failedRestoreLabel(labels: RelationshipOptionContext, value: string) {
  return labels.failedLabel?.(value) ?? value;
}

/**
 * Resolves selected values through the source that owns each value. Values that no source can
 * identify remain visible as raw values instead of causing a request to every source.
 */
export function useSelectedRelationshipOptions({
  sources,
  values,
  labels,
  authScope,
}: {
  sources: readonly RelationshipSource[];
  values: readonly string[];
  authScope?: string | number;
  labels: RelationshipOptionContext;
}): RelationshipOptionWithSource[] {
  const scope = JSON.stringify([useQueryIdentityScope(authScope), authScope ?? null]);
  const queryClient = useQueryClient();
  const selections = values.map((value) => {
    const owner = sources.find((candidate) => candidate.ownsValue(value));
    const canonical = owner === undefined ? null : canonicalValue(owner, value);
    const malformed =
      owner === undefined &&
      sources.some(
        (candidate) =>
          candidate.globalIdPrefix !== undefined &&
          candidate.normalizeValue?.(value) === null &&
          value.trim().toUpperCase().startsWith(candidate.globalIdPrefix.toUpperCase()),
      );
    return { value, owner, canonical, malformed };
  });

  const canonicalValuesBySource = new Map<RelationshipSource, Set<string>>();
  for (const { owner, canonical } of selections) {
    if (owner === undefined || canonical === null) continue;
    const canonicalValues = canonicalValuesBySource.get(owner) ?? new Set<string>();
    canonicalValues.add(canonical);
    canonicalValuesBySource.set(owner, canonicalValues);
  }

  type ResolveQuery = {
    owner: RelationshipSource;
    values: readonly string[];
    queryKey: readonly unknown[];
    run: (signal: AbortSignal) => Promise<Readonly<Record<string, unknown | null>>>;
  };
  const resolveQueries: ResolveQuery[] = [];
  for (const candidate of sources) {
    const uniqueValues = [...(canonicalValuesBySource.get(candidate) ?? [])].sort();

    if (candidate.resolveMany !== undefined) {
      const resolveMany = candidate.resolveMany;
      const requestedSize = candidate.batchSize ?? MAX_RESOLVE_BATCH_SIZE;
      const batchSize = Number.isFinite(requestedSize)
        ? Math.min(MAX_RESOLVE_BATCH_SIZE, Math.max(1, Math.floor(requestedSize)))
        : MAX_RESOLVE_BATCH_SIZE;
      for (const batch of chunk(uniqueValues, batchSize)) {
        const queryKey = optionBatchCacheKey(candidate, batch, scope);
        resolveQueries.push({
          owner: candidate,
          values: batch,
          queryKey,
          run: async (signal) => {
            const state = queryClient.getQueryState(queryKey);
            const forceRefresh = state?.isInvalidated === true && state.dataUpdatedAt > 0;
            const valuesToResolve = forceRefresh
              ? batch
              : batch.filter(
                  (canonical) => !cacheEntryIsFresh(queryClient, optionCacheKey(candidate, canonical, scope)),
                );
            if (valuesToResolve.length > 0) {
              const documents = await resolveMany(valuesToResolve, signal);
              for (const canonical of valuesToResolve) {
                queryClient.setQueryData(
                  optionCacheKey(candidate, canonical, scope),
                  Object.hasOwn(documents, canonical) ? (documents[canonical] ?? null) : null,
                );
              }
            }
            return Object.fromEntries(
              batch.map((canonical) => [
                canonical,
                queryClient.getQueryData(optionCacheKey(candidate, canonical, scope)) ?? null,
              ]),
            );
          },
        });
      }
    }
  }

  const queries = useQueries({
    queries: resolveQueries.map((resolveQuery) => ({
      queryKey: resolveQuery.queryKey,
      // Search stores options in the single-value cache; restores populate it from their batch.
      staleTime: 15_000,
      queryFn: ({ signal }: { signal: AbortSignal }) => resolveQuery.run(signal),
    })),
  });
  const cacheSubscriptions = sources.flatMap((candidate) => {
    const canonicalValues = [...(canonicalValuesBySource.get(candidate) ?? [])];
    return canonicalValues.map((canonical) => ({ candidate, canonical }));
  });
  // The combined records hold plain data only, so TanStack's structural sharing keeps unchanged ones
  // identical between renders; option objects (which may hold JSX) are built from them below.
  const resolutions = useQueries({
    queries: cacheSubscriptions.map(({ candidate, canonical }) => ({
      queryKey: optionCacheKey(candidate, canonical, scope),
      enabled: false,
    })),
    combine: (cacheQueries) => {
      const queryByValue = new Map<string, (typeof queries)[number]>();
      resolveQueries.forEach((resolveQuery, index) => {
        for (const canonical of resolveQuery.values) {
          queryByValue.set(JSON.stringify([resolveQuery.owner.id, canonical]), queries[index]);
        }
      });
      const cacheQueryByValue = new Map<string, (typeof cacheQueries)[number]>();
      cacheSubscriptions.forEach((subscription, index) => {
        cacheQueryByValue.set(JSON.stringify([subscription.candidate.id, subscription.canonical]), cacheQueries[index]);
      });
      return selections.map(({ value, owner, canonical, malformed }) => {
        if (owner === undefined || canonical === null) return { value, ownerId: owner?.id, canonical, malformed };
        const query = queryByValue.get(JSON.stringify([owner.id, canonical]));
        const cacheQuery = cacheQueryByValue.get(JSON.stringify([owner.id, canonical]));
        const document =
          cacheQuery?.data !== undefined
            ? cacheQuery.data
            : (query?.data?.[canonical] ?? queryClient.getQueryData(optionCacheKey(owner, canonical, scope)));
        return {
          value,
          ownerId: owner.id,
          canonical,
          malformed,
          document,
          failed: query?.isError === true,
          missing: document === null || (query?.isSuccess === true && !Object.hasOwn(query.data ?? {}, canonical)),
        };
      });
    },
  });

  // Base UI's Combobox compares its value by reference, so options keep their identity between renders.
  return useMemo(
    () =>
      resolutions.map((resolution): RelationshipOptionWithSource => {
        const { value, canonical, malformed } = resolution;
        const owner = sources.find((candidate) => candidate.id === resolution.ownerId);
        if (owner === undefined) {
          return {
            value,
            label: value,
            sourceId: "unknown",
            restoreStatus: malformed ? "invalid" : "unknown",
          };
        }
        if (canonical === null) {
          return { value, label: value, sourceId: owner.id, restoreStatus: "invalid" };
        }

        const { document } = resolution;
        const failedRestore = (): RelationshipOptionWithSource => ({
          value,
          label: failedRestoreLabel(labels, value),
          sourceId: owner.id,
          restoreStatus: "failed",
        });
        if (document !== undefined && document !== null) {
          try {
            return { ...owner.toOption(document, labels), sourceId: owner.id };
          } catch {
            return failedRestore();
          }
        }
        if (resolution.failed) return failedRestore();
        if (resolution.missing) {
          return {
            value,
            label: labels.unavailableLabel?.(value) ?? value,
            sourceId: owner.id,
            restoreStatus: "missing",
          };
        }
        if (owner.resolveMany === undefined) {
          return { value, label: value, sourceId: owner.id, restoreStatus: "unresolved" };
        }
        return {
          value,
          label: value,
          sourceId: owner.id,
          restoreStatus: "loading",
        };
      }),
    [sources, labels, resolutions],
  );
}
