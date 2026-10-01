import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import type { FeatureFlagName } from "./generatedFeatureFlags";
import {
  disabledFeatureFlags,
  FeatureFlagPageSchema,
  type FeatureFlagResponse,
  featureFlagsFromDocuments,
} from "./schema";
import {
  FEATURE_FLAGS_API_BASE_URL,
  FeatureFlagRequestError,
  featureFlagRequestHeaders,
  toFeatureFlagRequestError,
} from "./utils";

export const featureFlagQueryKeys = {
  all: ["rspace.featureFlags"] as const,
  flags: () => [...featureFlagQueryKeys.all, "flags"] as const,
};

export async function getFeatureFlags(token: string): Promise<FeatureFlagResponse> {
  const documents: unknown[] = [];
  let page = 1;
  while (true) {
    const response = await fetch(`${FEATURE_FLAGS_API_BASE_URL}?limit=100&page=${page}`, {
      method: "GET",
      headers: featureFlagRequestHeaders(token),
    });
    if (!response.ok) throw toFeatureFlagRequestError(response);

    const result = parseOrThrow(FeatureFlagPageSchema, (await response.json()) as unknown);
    documents.push(...result.docs);
    if (!result.hasNextPage) break;
    if (result.nextPage === null || result.nextPage <= page || result.nextPage > result.totalPages) {
      throw new Error("Feature flag pagination did not advance");
    }
    page = result.nextPage;
  }
  return featureFlagsFromDocuments(documents);
}

export function useFeatureFlags() {
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  return useQuery({
    queryKey: featureFlagQueryKeys.flags(),
    queryFn: () => getFeatureFlags(token),
    placeholderData: disabledFeatureFlags,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: (failureCount, error) =>
      !(error instanceof FeatureFlagRequestError && error.status >= 400 && error.status < 500) && failureCount < 2,
  });
}

export function useIsFeatureFlagEnabled(flagName: FeatureFlagName): boolean {
  const featureFlags = useFeatureFlags();
  // Keep app gating stable until an explicit page reload.
  const frozen = useRef<boolean | undefined>(undefined);
  if (frozen.current === undefined && !featureFlags.isPlaceholderData) {
    frozen.current = featureFlags.data?.flags[flagName]?.value ?? false;
  }
  return frozen.current ?? false;
}
