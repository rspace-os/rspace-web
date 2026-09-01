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

export const OPTION_PAGE_SIZE = 20;

function sourceList(source: RelationshipSource | undefined, sources: readonly RelationshipSource[] | undefined) {
  if (sources !== undefined) return sources;
  return source === undefined ? [] : [source];
}

/** The numeric ID inside a global ID, or null when the prefix belongs to another resource. */
export function databaseId(source: RelationshipSource, globalId: string): number | null {
  if (!source.globalIdPrefix) return null;
  const match = new RegExp(`^${source.globalIdPrefix}(\\d+)$`, "i").exec(globalId.trim());
  return match ? Number(match[1]) : null;
}

export function useRelationshipOptions({
  source,
  sources,
  term,
  token,
  labels,
  enabled = true,
}: {
  source?: RelationshipSource;
  sources?: readonly RelationshipSource[];
  term: string;
  token: string | undefined;
  labels: RelationshipOptionContext;
  enabled?: boolean;
}) {
  const queryClient = useQueryClient();
  const candidates = sourceList(source, sources);
  const sourceQueries = useQueries({
    queries: candidates.map((candidate) => ({
      // The v2 token is bound to the current run-as identity; partition caches so a role switch
      // cannot briefly display options fetched under the previous identity.
      queryKey: ["relationship-options", candidate.id, token ?? "anonymous", term.trim()],
      enabled,
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        const documents = await candidate.search(term.trim(), token, signal);
        for (const document of documents) {
          const option = candidate.toOption(document, labels);
          queryClient.setQueryData(
            ["relationship-option", candidate.id, token ?? "anonymous", String(option.value)],
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
  labels,
}: {
  source?: RelationshipSource;
  sources?: readonly RelationshipSource[];
  values: readonly string[];
  token: string | undefined;
  labels: RelationshipOptionContext;
}): RelationshipOptionWithSource[] {
  const queryClient = useQueryClient();
  const candidates = sourceList(source, sources);
  const owners = values.map((value) => candidates.find((candidate) => candidate.ownsValue(value)));
  const queries = useQueries({
    queries: values.map((value, index) => {
      const owner = owners[index];
      return {
        queryKey: ["relationship-option", owner?.id ?? "unknown", token ?? "anonymous", value],
        enabled: owner?.resolve !== undefined,
        queryFn: ({ signal }: { signal: AbortSignal }) => owner?.resolve?.(value, token, signal),
      };
    }),
  });
  return values.map((value, index) => {
    const owner = owners[index];
    const document =
      queries[index]?.data ??
      queryClient.getQueryData(["relationship-option", owner?.id ?? "unknown", token ?? "anonymous", value]);
    if (owner && document !== undefined && document !== null) {
      return { ...owner.toOption(document, labels), sourceId: owner.id, sourceDocument: document };
    }
    return { value, label: value, sourceId: owner?.id ?? "unknown" };
  });
}

export function useRelationshipOptionAvailability({
  source,
  options,
  availabilitySource,
  token,
}: {
  source: RelationshipSource;
  options: readonly RelationshipOption[];
  availabilitySource: RelationshipOptionAvailabilitySource | undefined;
  token: string | undefined;
}) {
  const values = [...new Set(options.map((option) => String(option.value)))].sort();
  const query = useQuery<Readonly<Record<string, UnavailableRelationshipOption>>>({
    queryKey: [...(availabilitySource?.queryKey ?? []), source.id, token ?? "anonymous", ...values],
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
