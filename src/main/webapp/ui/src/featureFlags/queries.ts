import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import type { ErrorType } from "@/modules/common/api/v2/apiV2Fetch";
import {
  getListFeatureFlagsQueryKey,
  listFeatureFlags,
} from "@/modules/common/api/v2/generated/feature-flags/feature-flags";
import type { FeatureFlagsRead } from "@/modules/common/api/v2/generated/models/featureFlagsRead";
import type { FeatureFlagName } from "./generatedFeatureFlags";
import { disabledFeatureFlags, type FeatureFlagResponse, featureFlagsFromDocuments } from "./schema";

export async function getFeatureFlags(): Promise<FeatureFlagResponse> {
  const documents: FeatureFlagsRead[] = [];
  let page = 1;
  while (true) {
    const result = await listFeatureFlags({ limit: 100, page });
    documents.push(...result.docs);
    if (!result.hasNextPage) break;
    if (result.page !== page) throw new Error("Feature flag pagination did not advance");
    page = result.page + 1;
  }
  return featureFlagsFromDocuments(documents);
}

export function useFeatureFlags() {
  return useQuery<FeatureFlagResponse, ErrorType<unknown>>({
    queryKey: [...getListFeatureFlagsQueryKey({ limit: 100 }), "featureFlags"],
    queryFn: getFeatureFlags,
    placeholderData: disabledFeatureFlags,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: (failureCount, error) =>
      !(error instanceof ApiV2ProblemError && error.status >= 400 && error.status < 500) && failureCount < 2,
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
