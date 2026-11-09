import { useQuery } from "@tanstack/react-query";
import {
  getListMaintenancesQueryOptions,
  listMaintenances,
} from "@/modules/common/api/v2/generated/maintenances/maintenances";
import type { ListMaintenances200 } from "@/modules/common/api/v2/generated/models/listMaintenances200";

export type MaintenanceStatus = "in-progress" | "clear";
const POLL_INTERVAL_MS = 30 * 1000;
const parameters = { limit: 1, fields: { maintenances: "canUserLoginNow" } };
function status(result: ListMaintenances200): MaintenanceStatus {
  const maintenance = result.docs[0];
  return maintenance && maintenance.canUserLoginNow !== true ? "in-progress" : "clear";
}

export async function getMaintenanceStatus(): Promise<MaintenanceStatus> {
  try {
    return status(await listMaintenances(parameters));
  } catch {
    return "in-progress";
  }
}

export function useMaintenanceStatusQuery() {
  const query = useQuery(
    getListMaintenancesQueryOptions(parameters, {
      query: {
        select: status,
        refetchInterval: POLL_INTERVAL_MS,
        staleTime: POLL_INTERVAL_MS,
        retry: false,
      },
    }),
  );
  return { ...query, data: query.isError ? "in-progress" : (query.data ?? "in-progress") };
}
