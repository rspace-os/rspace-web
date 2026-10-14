import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  RelationshipOption,
  RelationshipOptionAvailabilitySource,
  UnavailableRelationshipOption,
} from "@/modules/common/collection-form/RenderFields.types";
import type {
  RelationshipOptionContext,
  RelationshipOptionWithSource,
  RelationshipSource,
} from "./relationshipSources";
import { databaseIdFromGlobalId } from "./relationshipSources";

export const OPTION_PAGE_SIZE = 20;
const MAX_RESOLVE_BATCH_SIZE = 100;

function sourceList(source: RelationshipSource | undefined, sources: readonly RelationshipSource[] | undefined) {
  if (sources !== undefined) return sources;
  return source === undefined ? [] : [source];
}

function cacheScope(token: string | undefined, authScope: string | number | undefined) {
  return [authScope ?? "default", token ?? "anonymous"] as const;
}

function optionCacheKey(
  source: RelationshipSource | undefined,
  scope: readonly [string | number, string],
  value: string,
) {
  return ["relationship-option", source?.id ?? "unknown", ...scope, value] as const;
}

function canonicalValue(source: RelationshipSource, value: string): string | null {
  return source.normalizeValue === undefined ? value : source.normalizeValue(value);
}

function cacheEntryIsFresh(queryClient: ReturnType<typeof useQueryClient>, key: readonly unknown[]) {
  const updatedAt = queryClient.getQueryState(key)?.dataUpdatedAt ?? 0;
  return queryClient.getQueryData(key) !== undefined && Date.now() - updatedAt < 15_000;
}

function failedRestoreLabel(labels: RelationshipOptionContext, value: string) {
  return `${value} — ${labels.failedLabel?.(value) ?? value}`;
}

/** The numeric ID inside a global ID, or null when the prefix belongs to another resource. */
export function databaseId(source: RelationshipSource, globalId: string): number | null {
  return source.globalIdPrefix === undefined ? null : databaseIdFromGlobalId(globalId, source.globalIdPrefix);
}

export function useRelationshipOptions({
  source,
  sources,
  term,
  token,
  authScope,
  labels,
  enabled = true,
}: {
  source?: RelationshipSource;
  sources?: readonly RelationshipSource[];
  term: string;
  token: string | undefined;
  authScope?: string | number;
  labels: RelationshipOptionContext;
  enabled?: boolean;
}) {
  const queryClient = useQueryClient();
  const candidates = sourceList(source, sources);
  const scope = cacheScope(token, authScope);
  const sourceQueries = useQueries({
    queries: candidates.map((candidate) => ({
      // The v2 token is bound to the current run-as identity; partition caches so a role switch
      // cannot briefly display options fetched under the previous identity.
      queryKey: ["relationship-options", candidate.id, ...scope, term.trim()],
      enabled,
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        const documents = await candidate.search(term.trim(), token, signal);
        for (const document of documents) {
          const option = candidate.toOption(document, labels);
          queryClient.setQueryData(
            optionCacheKey(candidate, scope, canonicalValue(candidate, String(option.value)) ?? String(option.value)),
            document,
          );
        }
        return documents;
      },
    })),
  });
  const options: RelationshipOptionWithSource[] = candidates.flatMap((candidate, index) =>
    (sourceQueries[index]?.data ?? []).map((document) => {
      const option = candidate.toOption(document, labels);
      return { ...option, sourceId: candidate.id, sourceDocument: document };
    }),
  );
  return {
    options,
    failed: sourceQueries.some((query) => query.isError),
    loading: sourceQueries.some((query) => query.isFetching),
  };
}

/**
 * Resolves selected values through the source that owns each value. Values that no source can
 * identify remain visible as raw values instead of causing a request to every source.
 */
export function useSelectedRelationshipOptions({
  source,
  sources,
  values,
  token,
  authScope,
  labels,
}: {
  source?: RelationshipSource;
  sources?: readonly RelationshipSource[];
  values: readonly string[];
  token: string | undefined;
  authScope?: string | number;
  labels: RelationshipOptionContext;
}): RelationshipOptionWithSource[] {
  const queryClient = useQueryClient();
  const candidates = sourceList(source, sources);
  const scope = cacheScope(token, authScope);
  const selections = values.map((value) => {
    const owner = candidates.find((candidate) => candidate.ownsValue(value));
    const canonical = owner === undefined ? null : canonicalValue(owner, value);
    const malformed =
      owner === undefined &&
      candidates.some(
        (candidate) =>
          candidate.globalIdPrefix !== undefined &&
          candidate.normalizeValue?.(value) === null &&
          value.trim().toUpperCase().startsWith(candidate.globalIdPrefix.toUpperCase()),
      );
    return { value, owner, canonical, malformed };
  });

  type ResolveQuery = {
    owner: RelationshipSource;
    values: readonly string[];
    queryKey: readonly unknown[];
    run: (signal: AbortSignal) => Promise<Readonly<Record<string, unknown | null>>>;
  };
  const resolveQueries: ResolveQuery[] = [];
  for (const candidate of candidates) {
    const requestedValues = selections
      .filter((selection) => selection.owner === candidate)
      .map((selection) => selection.canonical)
      .filter((canonical): canonical is string => canonical !== null);
    const uniqueValues = [...new Set(requestedValues)].sort();

    if (candidate.resolveMany !== undefined) {
      const resolveMany = candidate.resolveMany;
      const requestedSize = candidate.batchSize ?? MAX_RESOLVE_BATCH_SIZE;
      const batchSize = Number.isFinite(requestedSize)
        ? Math.min(MAX_RESOLVE_BATCH_SIZE, Math.max(1, Math.floor(requestedSize)))
        : MAX_RESOLVE_BATCH_SIZE;
      for (let offset = 0; offset < uniqueValues.length; offset += batchSize) {
        const batch = uniqueValues.slice(offset, offset + batchSize);
        const queryKey = ["relationship-option-batch", candidate.id, ...scope, ...batch] as const;
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
                  (canonical) => !cacheEntryIsFresh(queryClient, optionCacheKey(candidate, scope, canonical)),
                );
            if (valuesToResolve.length > 0) {
              const documents = await resolveMany(valuesToResolve, token, signal);
              for (const canonical of valuesToResolve) {
                queryClient.setQueryData(
                  optionCacheKey(candidate, scope, canonical),
                  Object.hasOwn(documents, canonical) ? (documents[canonical] ?? null) : null,
                );
              }
            }
            return Object.fromEntries(
              batch.map((canonical) => [
                canonical,
                queryClient.getQueryData(optionCacheKey(candidate, scope, canonical)) ?? null,
              ]),
            );
          },
        });
      }
    } else if (candidate.resolve !== undefined) {
      const resolve = candidate.resolve;
      for (const canonical of uniqueValues) {
        const queryKey = ["relationship-option-restore", candidate.id, ...scope, canonical] as const;
        resolveQueries.push({
          owner: candidate,
          values: [canonical],
          queryKey,
          run: async (signal) => {
            const cacheKey = optionCacheKey(candidate, scope, canonical);
            const state = queryClient.getQueryState(queryKey);
            const forceRefresh = state?.isInvalidated === true && state.dataUpdatedAt > 0;
            if (forceRefresh || !cacheEntryIsFresh(queryClient, cacheKey)) {
              const document = (await resolve(canonical, token, signal)) ?? null;
              queryClient.setQueryData(cacheKey, document);
            }
            return { [canonical]: queryClient.getQueryData(cacheKey) ?? null };
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
  const cacheSubscriptions = candidates.flatMap((candidate) => {
    const canonicalValues = [
      ...new Set(
        selections
          .filter((selection) => selection.owner === candidate)
          .map((selection) => selection.canonical)
          .filter((canonical): canonical is string => canonical !== null),
      ),
    ];
    return canonicalValues.map((canonical) => ({ candidate, canonical }));
  });
  const cacheQueries = useQueries({
    queries: cacheSubscriptions.map(({ candidate, canonical }) => ({
      queryKey: optionCacheKey(candidate, scope, canonical),
      enabled: false,
    })),
  });
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

  return values.map((value, index) => {
    const selection = selections[index];
    const { owner, canonical, malformed } = selection;
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

    const query = queryByValue.get(JSON.stringify([owner.id, canonical]));
    const cacheQuery = cacheQueryByValue.get(JSON.stringify([owner.id, canonical]));
    const document =
      cacheQuery?.data !== undefined
        ? cacheQuery.data
        : (query?.data?.[canonical] ?? queryClient.getQueryData(optionCacheKey(owner, scope, canonical)));
    if (document !== undefined && document !== null) {
      try {
        return { ...owner.toOption(document, labels), sourceId: owner.id, sourceDocument: document };
      } catch {
        return {
          value,
          label: failedRestoreLabel(labels, value),
          sourceId: owner.id,
          restoreStatus: "failed",
        };
      }
    }
    if (query?.isError) {
      return {
        value,
        label: failedRestoreLabel(labels, value),
        sourceId: owner.id,
        restoreStatus: "failed",
      };
    }
    if (document === null || (query?.isSuccess && !Object.hasOwn(query.data ?? {}, canonical))) {
      return {
        value,
        label: labels.unavailableLabel?.(value) ?? value,
        sourceId: owner.id,
        restoreStatus: "missing",
      };
    }
    if (owner.resolveMany === undefined && owner.resolve === undefined) {
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

export function useRelationshipOptionAvailability({
  source,
  options,
  availabilitySource,
  token,
  authScope,
}: {
  source: RelationshipSource;
  options: readonly RelationshipOption[];
  availabilitySource: RelationshipOptionAvailabilitySource | undefined;
  token: string | undefined;
  authScope?: string | number;
}) {
  const values = [...new Set(options.map((option) => String(option.value)))].sort();
  const query = useQuery<Readonly<Record<string, UnavailableRelationshipOption>>>({
    queryKey: [...(availabilitySource?.queryKey ?? []), source.id, ...cacheScope(token, authScope), ...values],
    enabled: availabilitySource !== undefined && values.length > 0,
    staleTime: 15_000,
    gcTime: 5 * 60_000,
    queryFn: ({ signal }) =>
      availabilitySource === undefined ? {} : availabilitySource.loadUnavailable(values, token, signal),
  });

  return {
    unavailable: query.data ?? {},
    checking: availabilitySource !== undefined && values.length > 0 && query.isPending,
    failed: query.isError,
  };
}
