import { useSuspenseQuery } from "@tanstack/react-query";
import {
  GetAvailableRaidListResponseSchema,
  IntegrationRaidInfoResponseSchema,
  type IntegrationRaidInfo,
  GetAvailableRaidListResponse,
} from "@/modules/raid/schema";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

/**
 * Query key factory for RAiD-related queries
 */
export const raidQueryKeys = {
  all: ["rspace.apps.raid"] as const,
  availableRaidIdentifiers: () => [...raidQueryKeys.all, "availableIds"] as const,
  integrationInfo: () => [...raidQueryKeys.all, "integrationInfo"] as const,
  projectAssociation: (alias: string, groupId: string) => [...raidQueryKeys.all, "projectAssociation", alias, groupId] as const,
};

/**
 * Fetches the list of available RAiD identifiers for the current user
 * @returns Promise that resolves to the GetRAiDListResponse
 * @throws Error if the request fails or validation fails
 */
export async function getAvailableRaidIdentifiersAjax(): Promise<GetAvailableRaidListResponse> {
  const response = await fetch("/apps/raid", {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  const data: unknown = await response.json();

  if (!response.ok) {
    throw new Error(`Failed to fetch RAiD apps: ${response.statusText}`);
  }

  return parseOrThrow(GetAvailableRaidListResponseSchema, data);
}

/**
 * Hook to fetch the list of RAiD apps with Suspense
 * Throws on error - wrap with Error Boundary
 * @returns useSuspenseQuery result with GetRAiDListResponse data
 */
export function useGetAvailableRaidIdentifiersAjaxQuery() {
  return useSuspenseQuery({
    queryKey: raidQueryKeys.availableRaidIdentifiers(),
    queryFn: getAvailableRaidIdentifiersAjax,
  });
}

/**
 * Fetch RAiD integration information
 * @returns Promise that resolves to the IntegrationRaIdInfo
 * @throws Error if the request fails or validation fails
 */
export async function getRaidIntegrationInfoAjax(): Promise<IntegrationRaidInfo> {
  const response = await fetch("/integration/integrationInfo?name=RAID", {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  const data: unknown = await response.json();

  if (!response.ok) {
    throw new Error(
      `Failed to fetch RAiD integration info: ${response.statusText}`,
    );
  }

  return parseOrThrow(IntegrationRaidInfoResponseSchema, data);
}

/**
 * Hook to fetch RAiD integration information with Suspense
 * Throws on error - wrap with Error Boundary
 * @returns useSuspenseQuery result with IntegrationRaIdInfo data
 */
export function useRaidIntegrationInfoAjaxQuery() {
  return useSuspenseQuery({
    queryKey: raidQueryKeys.integrationInfo(),
    queryFn: () => getRaidIntegrationInfoAjax(),
  });
}