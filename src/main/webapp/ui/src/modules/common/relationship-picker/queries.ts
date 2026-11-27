import {
  type QueryClient,
  skipToken,
  type UseQueryResult,
  useQueries,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { chunk, clamp, uniq } from "es-toolkit";
import { useQueryKeyScope } from "@/modules/common/queries/queryIdentityScope";
import {
  canonicalValue,
  type RelationshipOptionContext,
  type RelationshipOptionWithSource,
  type RelationshipSource,
} from "@/modules/common/relationship-picker/sources";
import type {
  RelationshipOption,
  RelationshipOptionAvailabilitySource,
} from "@/modules/common/relationship-picker/types";

/**
 * Query key factory for relationship-picker queries
 */
export const relationshipPickerQueryKeys = {
  all: ["rspace.common.relationshipPicker"] as const,
  search: (sourceId: string, scope: string | number | null, term: string) =>
    [...relationshipPickerQueryKeys.all, "search", sourceId, scope, term] as const,
  option: (sourceId: string, scope: string | number | null, value: string) =>
    [...relationshipPickerQueryKeys.all, "option", sourceId, scope, value] as const,
  optionBatch: (sourceId: string, scope: string | number | null, values: readonly string[]) =>
    [...relationshipPickerQueryKeys.all, "optionBatch", sourceId, scope, ...values] as const,
};

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
  const scope = useQueryKeyScope(authScope);
  const queryClient = useQueryClient();
  // Plain useQueries: options load under the open picker input and report failures per source.
  const { data, failed, loading } = useQueries({
    queries: sources.map((candidate) => ({
      queryKey: relationshipPickerQueryKeys.search(candidate.id, scope, term.trim()),
      enabled,
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        const documents = await candidate.search(term.trim(), signal);
        for (const document of documents) {
          const option = candidate.toOption(document, labels);
          queryClient.setQueryData(
            relationshipPickerQueryKeys.option(
              candidate.id,
              scope,
              canonicalValue(candidate, String(option.value)) ?? String(option.value),
            ),
            document,
          );
        }
        return documents;
      },
    })),
    combine: combineSearches,
  });
  const options: RelationshipOptionWithSource[] = sources.flatMap((candidate, index) =>
    (data[index] ?? []).map((document) => ({ ...candidate.toOption(document, labels), sourceId: candidate.id })),
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
  const scope = useQueryKeyScope(authScope);
  const values = uniq(options.map((option) => String(option.value))).sort();
  // Plain useQuery: optional availability only annotates options already shown.
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

/** Identifies a selected value within its source, for matching it to its resolve and cache queries. */
function optionKey(sourceId: string, canonical: string): string {
  return JSON.stringify([sourceId, canonical]);
}

const MAX_RESOLVE_BATCH_SIZE = 100;
/** How long a resolved option stays fresh; the batch query and the per-value cache check share it. */
const OPTION_STALE_TIME_MS = 15_000;

function cacheEntryIsFresh(queryClient: QueryClient, key: readonly unknown[]) {
  const state = queryClient.getQueryState(key);
  return state?.data !== undefined && Date.now() - state.dataUpdatedAt < OPTION_STALE_TIME_MS;
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
  const scope = useQueryKeyScope(authScope);
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
    if (candidate.resolveMany === undefined) continue;
    const resolveMany = candidate.resolveMany;
    const uniqueValues = [...(canonicalValuesBySource.get(candidate) ?? [])].sort();
    const requestedSize = candidate.batchSize ?? MAX_RESOLVE_BATCH_SIZE;
    const batchSize = Number.isFinite(requestedSize)
      ? clamp(Math.floor(requestedSize), 1, MAX_RESOLVE_BATCH_SIZE)
      : MAX_RESOLVE_BATCH_SIZE;
    for (const batch of chunk(uniqueValues, batchSize)) {
      const queryKey = relationshipPickerQueryKeys.optionBatch(candidate.id, scope, batch);
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
                (canonical) =>
                  !cacheEntryIsFresh(queryClient, relationshipPickerQueryKeys.option(candidate.id, scope, canonical)),
              );
          if (valuesToResolve.length > 0) {
            const documents = await resolveMany(valuesToResolve, signal);
            for (const canonical of valuesToResolve) {
              queryClient.setQueryData(
                relationshipPickerQueryKeys.option(candidate.id, scope, canonical),
                Object.hasOwn(documents, canonical) ? (documents[canonical] ?? null) : null,
              );
            }
          }
          return Object.fromEntries(
            batch.map((canonical) => [
              canonical,
              queryClient.getQueryData(relationshipPickerQueryKeys.option(candidate.id, scope, canonical)) ?? null,
            ]),
          );
        },
      });
    }
  }

  // Plain useQueries: selected values resolve per source and report loading and failures inline.
  const queries = useQueries({
    queries: resolveQueries.map((resolveQuery) => ({
      queryKey: resolveQuery.queryKey,
      // Search stores options in the single-value cache; restores populate it from their batch.
      staleTime: OPTION_STALE_TIME_MS,
      queryFn: ({ signal }: { signal: AbortSignal }) => resolveQuery.run(signal),
    })),
  });
  const cacheSubscriptions = sources.flatMap((candidate) => {
    const canonicalValues = [...(canonicalValuesBySource.get(candidate) ?? [])];
    return canonicalValues.map((canonical) => ({ candidate, canonical }));
  });
  // The combined records hold plain data only, so TanStack's structural sharing keeps unchanged ones
  // identical between renders; option objects (which may hold JSX) are built from them below.
  // Plain useQueries: subscribes to cached options without fetching.
  const resolutions = useQueries({
    queries: cacheSubscriptions.map(({ candidate, canonical }) => ({
      queryKey: relationshipPickerQueryKeys.option(candidate.id, scope, canonical),
      enabled: false,
    })),
    combine: (cacheQueries) => {
      const queryByValue = new Map<string, (typeof queries)[number]>();
      resolveQueries.forEach((resolveQuery, index) => {
        for (const canonical of resolveQuery.values) {
          queryByValue.set(optionKey(resolveQuery.owner.id, canonical), queries[index]);
        }
      });
      const cacheQueryByValue = new Map<string, (typeof cacheQueries)[number]>();
      cacheSubscriptions.forEach((subscription, index) => {
        cacheQueryByValue.set(optionKey(subscription.candidate.id, subscription.canonical), cacheQueries[index]);
      });
      return selections.map(({ value, owner, canonical, malformed }) => {
        if (owner === undefined || canonical === null) return { value, ownerId: owner?.id, canonical, malformed };
        const query = queryByValue.get(optionKey(owner.id, canonical));
        const cacheQuery = cacheQueryByValue.get(optionKey(owner.id, canonical));
        const document =
          cacheQuery?.data !== undefined
            ? cacheQuery.data
            : (query?.data?.[canonical] ??
              queryClient.getQueryData(relationshipPickerQueryKeys.option(owner.id, scope, canonical)));
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

  return resolutions.map((resolution): RelationshipOptionWithSource => {
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
  });
}
