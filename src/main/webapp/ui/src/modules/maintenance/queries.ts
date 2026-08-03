import { useQuery } from "@tanstack/react-query";

// Public/unauthenticated endpoint: shown to logged-out users, so no OAuth token or /api/v2 resource.
export type MaintenanceStatus = "in-progress" | "clear";

export const maintenanceStatusQueryKeys = {
  all: ["rspace.public.maintenanceStatus"] as const,
};

const POLL_INTERVAL_MS = 30 * 1000;

export async function getMaintenanceStatus(): Promise<MaintenanceStatus> {
  const response = await fetch("/public/maintenanceStatus", {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  // on error, assume still in maintenance rather than bounce the user to a rejecting login
  if (!response.ok) {
    return "in-progress";
  }

  const text = (await response.text()).trim();
  return text === "No maintenance" ? "clear" : "in-progress";
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
