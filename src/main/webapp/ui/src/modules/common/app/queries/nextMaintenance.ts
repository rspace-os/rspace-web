import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";

// Shared with /config. Only startDate is read; the rest tolerate nulls so a null date can't hide the banner.
export const MaintenanceSchema = v.object({
  id: v.number(),
  startDate: v.string(),
  endDate: v.nullable(v.string()),
  stopUserLoginDate: v.nullable(v.string()),
  message: v.optional(v.nullable(v.string())),
});

const MaintenancesEnvelopeSchema = v2ListEnvelope(MaintenanceSchema);

export type NextMaintenance = { startDate: Date };

const REQUEST_TIMEOUT_MS = 10_000;

const REFETCH_INTERVAL_MS = 5 * 60 * 1000;

export const nextMaintenanceQueryKeys = {
  all: ["rspace.api.v2.maintenances"] as const,
  next: () => [...nextMaintenanceQueryKeys.all, "next"] as const,
};

export async function getNextMaintenance(token: string): Promise<NextMaintenance | null> {
  try {
    const response = await fetch("/api/v2/maintenances?limit=1", {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Requested-With": "XMLHttpRequest",
      },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });

    if (!response.ok) {
      return null;
    }

    const data: unknown = await response.json();
    const envelope = parseOrThrow(MaintenancesEnvelopeSchema, data);
    const next = envelope.docs[0];
    return next ? { startDate: new Date(next.startDate) } : null;
  } catch (error) {
    console.warn("Could not read the next scheduled maintenance", error);
    return null;
  }
}

export function useNextMaintenanceQuery() {
  const { data: token } = useOauthTokenQuery();
  return useQuery({
    queryKey: nextMaintenanceQueryKeys.next(),
    queryFn: () => getNextMaintenance(token),
    staleTime: REFETCH_INTERVAL_MS,
    refetchInterval: REFETCH_INTERVAL_MS,
    retry: false,
  });
}
