import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";

export const MaintenanceSchema = v.object({
  id: v.pipe(v.number(), v.integer()),
  startDate: v.pipe(v.string(), v.isoTimestamp()),
});

const MaintenancesEnvelopeSchema = v2ListEnvelope(MaintenanceSchema);

export type NextMaintenance = { startDate: Date };

const REQUEST_TIMEOUT_MS = 10_000;

const REFETCH_INTERVAL_MS = 5 * 60 * 1000;

export const nextMaintenanceQueryKeys = {
  all: ["rspace.api.v2.maintenances"] as const,
  next: () => [...nextMaintenanceQueryKeys.all, "next"] as const,
};

/** Keep the filter so authenticated callers cannot receive an expired maintenance window. */
function nextMaintenanceUrl(): string {
  const parameters = new URLSearchParams({
    where: `endDate=gt=${new Date().toISOString()}`,
    limit: "1",
    "fields[maintenances]": "startDate",
  });
  return `/api/v2/maintenances?${parameters}`;
}

export async function getNextMaintenance(): Promise<NextMaintenance | null> {
  try {
    const response = await fetch(nextMaintenanceUrl(), {
      method: "GET",
      headers: {
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
  return useQuery({
    queryKey: nextMaintenanceQueryKeys.next(),
    queryFn: getNextMaintenance,
    staleTime: REFETCH_INTERVAL_MS,
    refetchInterval: REFETCH_INTERVAL_MS,
    retry: false,
  });
}
