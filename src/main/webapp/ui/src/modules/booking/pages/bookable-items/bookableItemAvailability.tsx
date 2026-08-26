import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import type { UnavailableRelationshipOption } from "@/modules/common/collection-form/RenderFields.types";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import { OPTION_PAGE_SIZE } from "@/modules/common/relationship-picker/relationshipOptionQueries";
import { serializeRsql } from "@/modules/common/table-list/adapters/apiV2/rsql/serializeRsql";

const ALREADY_CONFIGURED = "alreadyConfigured";

const ConfigurationSchema = v.object({
  id: v.number(),
  target: v.object({
    relationTo: v.literal("instruments"),
    value: v.number(),
    globalId: v.string(),
  }),
});
const ConfigurationListSchema = v2ListEnvelope(ConfigurationSchema);

const filterLimits = {
  maximumComparisons: 1,
  maximumLikeComparisons: 0,
  maximumNesting: 1,
  maximumArguments: OPTION_PAGE_SIZE,
  maximumWhereLength: 2000,
};

export async function loadUnavailableBookableItems(
  globalIds: readonly string[],
  token: string | undefined,
  signal: AbortSignal,
): Promise<Readonly<Record<string, UnavailableRelationshipOption>>> {
  if (globalIds.length === 0) return {};
  const where = serializeRsql<Record<string, unknown>>(
    { kind: "comparison", field: "target", operator: "in", value: globalIds },
    { target: { operators: ["=in="], wildcards: false } },
    filterLimits,
  );
  const params = new URLSearchParams({ page: "1", limit: String(OPTION_PAGE_SIZE), depth: "0", where });
  params.set("fields[booking-configurations]", "id,target");
  const headers = new Headers({ "X-Requested-With": "XMLHttpRequest" });
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`/api/v2/booking-configurations?${params}`, { headers, signal });
  if (!response.ok) throw new Error(`Bookable item availability request failed with status ${response.status}`);
  const configurations = parseOrThrow(ConfigurationListSchema, (await response.json()) as unknown).docs;
  return Object.fromEntries(
    configurations.map((configuration) => [
      configuration.target.globalId,
      { reason: ALREADY_CONFIGURED, relatedRecordId: configuration.id },
    ]),
  );
}

export function useSelectedBookableItemAvailability(globalId: string | undefined, token: string | undefined) {
  return useQuery({
    queryKey: ["api-v2", "booking-configurations", "availability", globalId],
    enabled: globalId !== undefined,
    staleTime: 15_000,
    gcTime: 5 * 60_000,
    queryFn: async ({ signal }) => {
      if (globalId === undefined) return undefined;
      const unavailable = await loadUnavailableBookableItems([globalId], token, signal);
      return unavailable[globalId] ?? null;
    },
  });
}
