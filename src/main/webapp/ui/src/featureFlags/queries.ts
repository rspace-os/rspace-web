import { useQuery } from "@tanstack/react-query";
import { useRef } from "react";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { getStoredToken, isExpiringSoon, saveStoredToken } from "@/modules/common/utils/auth";
import type { FeatureFlagName } from "./generatedFeatureFlags";
import {
  disabledFeatureFlags,
  FeatureFlagApiTokenResponseSchema,
  type FeatureFlagResponse,
  FeatureFlagResponseSchema,
} from "./schema";
import { FEATURE_FLAGS_API_BASE_URL, featureFlagRequestHeaders, toFeatureFlagRequestError } from "./utils";

export const featureFlagQueryKeys = {
  all: ["rspace.featureFlags"] as const,
  apiToken: () => [...featureFlagQueryKeys.all, "apiToken"] as const,
  flags: (authenticated: boolean) => [...featureFlagQueryKeys.all, "flags", authenticated] as const,
};

export async function getFeatureFlagApiToken(): Promise<string | null> {
  const savedToken = getStoredToken();
  if (savedToken && !isExpiringSoon(savedToken)) {
    return savedToken;
  }

  try {
    const response = await fetch("/userform/ajax/inventoryOauthToken", {
      method: "GET",
      headers: featureFlagRequestHeaders(),
    });
    if (!response.ok) return null;

    const data: unknown = await response.json();
    const result = v.safeParse(FeatureFlagApiTokenResponseSchema, data);
    if (!result.success) return null;
    saveStoredToken(result.output.data);
    return result.output.data;
  } catch {
    return null;
  }
}

export async function getFeatureFlags(token: string | null): Promise<FeatureFlagResponse> {
  const response = await fetch(FEATURE_FLAGS_API_BASE_URL, {
    method: "GET",
    headers: featureFlagRequestHeaders(token),
  });

  if (!response.ok) {
    throw toFeatureFlagRequestError(response);
  }

  const data: unknown = await response.json();
  return parseOrThrow(FeatureFlagResponseSchema, data);
}

export function useFeatureFlagApiToken() {
  return useQuery({
    queryKey: featureFlagQueryKeys.apiToken(),
    queryFn: getFeatureFlagApiToken,
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

export function useFeatureFlags() {
  const tokenQuery = useFeatureFlagApiToken();
  return useQuery({
    queryKey: featureFlagQueryKeys.flags(Boolean(tokenQuery.data)),
    queryFn: () => getFeatureFlags(tokenQuery.data ?? null),
    enabled: !tokenQuery.isLoading,
    placeholderData: disabledFeatureFlags,
    staleTime: Infinity,
    gcTime: Infinity,
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
