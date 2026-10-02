import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";

// The maintenance collection is public because logged-out users read this status.
export type MaintenanceStatus = "in-progress" | "clear";

export const maintenanceStatusQueryKeys = {
  all: ["rspace.public.maintenanceStatus"] as const,
};

const POLL_INTERVAL_MS = 30 * 1000;

const MaintenanceStatusEnvelopeSchema = v2ListEnvelope(v.object({ canUserLoginNow: v.boolean() }));

export async function getMaintenanceStatus(): Promise<MaintenanceStatus> {
  const parameters = new URLSearchParams({
    limit: "1",
    "fields[maintenances]": "canUserLoginNow",
  });
  const response = await fetch(`/api/v2/maintenances?${parameters}`, {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  // on error, assume still in maintenance rather than bounce the user to a rejecting login
  if (!response.ok) {
    return "in-progress";
  }

  try {
    const data: unknown = await response.json();
    const maintenance = parseOrThrow(MaintenanceStatusEnvelopeSchema, data).docs[0];
    return maintenance?.canUserLoginNow === false ? "in-progress" : "clear";
  } catch {
    return "in-progress";
  }
}

export function useMaintenanceStatusQuery() {
  return useQuery({
    queryKey: maintenanceStatusQueryKeys.all,
    queryFn: getMaintenanceStatus,
    refetchInterval: POLL_INTERVAL_MS,
    staleTime: POLL_INTERVAL_MS,
    retry: false,
  });
}
