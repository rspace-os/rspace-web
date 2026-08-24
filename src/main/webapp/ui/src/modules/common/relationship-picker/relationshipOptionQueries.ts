import type { QueryClient } from "@tanstack/react-query";
import { keepPreviousData, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { cmp, eq, escapeValue, Operation } from "rsql-builder";
import * as v from "valibot";
import type {
  RelationshipOption,
  RelationshipOptionAvailabilitySource,
  UnavailableRelationshipOption,
} from "@/modules/common/collection-form/RenderFields.types";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import type { RelationshipOptionContext, RelationshipSource } from "./relationshipSources";

/** Options shown in one dropdown. Deliberately small: this is a picker, not a listing. */
export const OPTION_PAGE_SIZE = 20;

/**
 * Cached for minutes rather than seconds: instrument names change rarely, and the same searches
 * recur as a user reopens the dropdown. The same cache is the seam for offline support later.
 */
const CACHE = { staleTime: 5 * 60_000, gcTime: 30 * 60_000 } as const;
const AVAILABILITY_CACHE = { staleTime: 15_000, gcTime: 5 * 60_000 } as const;

const DocumentSchema = v.looseObject({ globalId: v.string() });
const ListSchema = v2ListEnvelope(DocumentSchema);

function optionsKey(resourceName: string, term: string) {
  return ["relationship-options", resourceName, term] as const;
}

function optionKey(resourceName: string, globalId: string) {
  return ["relationship-option", resourceName, globalId] as const;
}

/** The numeric ID inside a global ID, or null when the prefix belongs to another resource. */
export function databaseId(source: RelationshipSource, globalId: string): number | null {
  const match = new RegExp(`^${source.globalIdPrefix}(\\d+)$`, "i").exec(globalId.trim());
  return match ? Number(match[1]) : null;
}

function searchParams(source: RelationshipSource, where: string | null, limit: number) {
  const params = new URLSearchParams({ page: "1", limit: String(limit) });
  params.set(`fields[${source.resourceName}]`, source.fields.join(","));
  if (where) params.set("where", where);
  return params;
}

/**
 * Searches by global ID when the input is one, otherwise by name. Global IDs are derived from the
 * row ID server-side, so they are matched through the ID rather than as a filterable field.
 */
function searchExpression(source: RelationshipSource, term: string): string | null {
  if (term === "") return null;
  const id = databaseId(source, term);
  return id === null
    ? cmp(source.searchField, new Operation(escapeValue(term), "=contains=")).toString()
    : cmp("id", eq(id)).toString();
}

async function request(url: string, token: string | undefined, signal: AbortSignal): Promise<unknown> {
  const headers = new Headers({ "X-Requested-With": "XMLHttpRequest" });
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(url, { headers, signal });
  if (!response.ok) throw new Error(`Relationship option request failed with status ${response.status}`);
  return response.json();
}

/**
 * Seeds each row into its own cache entry, so a chip or a filter restored from the URL renders
 * without a second round trip.
 */
function cacheOptions(client: QueryClient, source: RelationshipSource, documents: readonly unknown[]) {
  for (const document of documents) {
    const globalId = parseOrThrow(DocumentSchema, document).globalId;
    client.setQueryData(optionKey(source.resourceName, globalId), document);
  }
}

export function useRelationshipOptions({
  source,
  term,
  token,
  labels,
  enabled = true,
}: {
  source: RelationshipSource;
  term: string;
  token: string | undefined;
  labels: RelationshipOptionContext;
  enabled?: boolean;
}) {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: optionsKey(source.resourceName, term.trim()),
    enabled,
    placeholderData: keepPreviousData,
    ...CACHE,
    queryFn: async ({ signal }) => {
      const where = searchExpression(source, term.trim());
      const params = searchParams(source, where, OPTION_PAGE_SIZE);
      const body = await request(`/api/v2/${source.resourceName}?${params}`, token, signal);
      const documents = parseOrThrow(ListSchema, body).docs;
      cacheOptions(client, source, documents);
      return documents;
    },
  });
  return {
    options: (query.data ?? []).map((document) => source.toOption(document, labels)),
    // Surfaced by the picker: a refused or failed search must not read as "no such record".
    failed: query.isError,
    loading: query.isFetching,
  };
}

/**
 * Resolves the already-selected global IDs. Normally cache hits seeded by the dropdown; the
 * requests matter for a filter restored from a shared URL, which is selected before any search.
 */
export function useSelectedRelationshipOptions({
  source,
  globalIds,
  token,
  labels,
}: {
  source: RelationshipSource;
  globalIds: readonly string[];
  token: string | undefined;
  labels: RelationshipOptionContext;
}): RelationshipOption[] {
  const results = useQueries({
    queries: globalIds.map((globalId) => {
      const id = databaseId(source, globalId);
      return {
        queryKey: optionKey(source.resourceName, globalId),
        enabled: id !== null,
        ...CACHE,
        queryFn: ({ signal }: { signal: AbortSignal }) => {
          const params = new URLSearchParams();
          params.set(`fields[${source.resourceName}]`, source.fields.join(","));
          return request(`/api/v2/${source.resourceName}/${id}?${params}`, token, signal);
        },
      };
    }),
  });
  return globalIds.map((globalId, index) => {
    const document = results[index]?.data;
    // Falls back to the global ID itself: an unresolved value is still a valid filter value.
    return document === undefined ? { value: globalId, label: globalId } : source.toOption(document, labels);
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
    queryKey: [...(availabilitySource?.queryKey ?? []), source.resourceName, ...values],
    enabled: availabilitySource !== undefined && values.length > 0,
    ...AVAILABILITY_CACHE,
    queryFn: ({ signal }) =>
      availabilitySource === undefined ? {} : availabilitySource.loadUnavailable(values, token, signal),
  });

  return {
    unavailable: query.data ?? {},
    checking: availabilitySource !== undefined && values.length > 0 && query.isPending,
    failed: query.isError,
  };
}
