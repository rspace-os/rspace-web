import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
  getListMaintenancesQueryOptions,
  listMaintenances,
} from "@/modules/common/api/v2/generated/maintenances/maintenances";
import type { ListMaintenances200 } from "@/modules/common/api/v2/generated/models/listMaintenances200";

export type NextMaintenance = { startDate: Date };
const REQUEST_TIMEOUT_MS = 10_000;
const REFETCH_INTERVAL_MS = 5 * 60 * 1000;

function parameters() {
  return { where: `endDate=gt=${new Date().toISOString()}`, limit: 1, fields: { maintenances: "startDate" } };
}
function nextMaintenance(result: ListMaintenances200): NextMaintenance | null {
  const start = result.docs[0]?.startDate;
  return start ? { startDate: new Date(start) } : null;
}

export async function getNextMaintenance(): Promise<NextMaintenance | null> {
  try {
    return nextMaintenance(await listMaintenances(parameters(), { signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) }));
  } catch (error) {
    console.warn("Could not read the next scheduled maintenance", error);
    return null;
  }
}

export function useNextMaintenanceQuery() {
  const [params] = useState(parameters);
  const query = useQuery({
    ...getListMaintenancesQueryOptions(params, {
      query: {
        select: nextMaintenance,
        staleTime: REFETCH_INTERVAL_MS,
        refetchInterval: REFETCH_INTERVAL_MS,
        retry: false,
      },
    }),
    queryFn: ({ signal }) =>
      listMaintenances(parameters(), {
        signal: AbortSignal.any([signal, AbortSignal.timeout(REQUEST_TIMEOUT_MS)]),
      }),
  });
  return { ...query, data: query.isError ? null : (query.data ?? null) };
}
