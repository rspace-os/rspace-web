import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  RelationshipOption,
  RelationshipOptionAvailabilitySource,
  UnavailableRelationshipOption,
} from "@/modules/common/relationship-picker/types";
import { cacheScope, canonicalValue, optionCacheKey, sourceList } from "./relationshipOptionCache";
import type { RelationshipOptionContext, RelationshipOptionWithSource, RelationshipSource } from "./sources";
import { databaseIdFromGlobalId } from "./sources";

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
      return { ...option, sourceId: candidate.id };
    }),
  );
  return {
    options,
    failed: sourceQueries.some((query) => query.isError),
    loading: sourceQueries.some((query) => query.isFetching),
  };
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
