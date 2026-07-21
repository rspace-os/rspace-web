import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import type { FeatureFlagName } from "./generatedFeatureFlags";
import { featureFlagQueryKeys } from "./queries";
import { FEATURE_FLAGS_API_BASE_URL, featureFlagRequestHeaders, toFeatureFlagRequestError } from "./utils";

export type SetFeatureFlagParams = {
  flagName: FeatureFlagName;
  value: boolean;
};

export type ClearFeatureFlagOverrideParams = {
  flagName: FeatureFlagName;
};

async function writeFeatureFlag({
  flagName,
  token,
  document,
}: {
  flagName: FeatureFlagName;
  token: string;
  document: { overrideValue: boolean | null } | { baselineValue: boolean };
}): Promise<void> {
  const response = await fetch(`${FEATURE_FLAGS_API_BASE_URL}/${flagName}`, {
    method: "PATCH",
    headers: {
      ...featureFlagRequestHeaders(token),
      "Content-Type": "application/json",
    },
    body: JSON.stringify(document),
  });

  if (!response.ok) {
    throw toFeatureFlagRequestError(response);
  }
}

export async function setFeatureFlagOverride({ flagName, value }: SetFeatureFlagParams, token: string): Promise<void> {
  return writeFeatureFlag({ flagName, token, document: { overrideValue: value } });
}

export async function clearFeatureFlagOverride(
  { flagName }: ClearFeatureFlagOverrideParams,
  token: string,
): Promise<void> {
  return writeFeatureFlag({ flagName, token, document: { overrideValue: null } });
}

export async function setFeatureFlagBaseline({ flagName, value }: SetFeatureFlagParams, token: string): Promise<void> {
  return writeFeatureFlag({ flagName, token, document: { baselineValue: value } });
}

function useFeatureFlagMutation<TVariables>(mutationFn: (variables: TVariables, token: string) => Promise<void>) {
  const queryClient = useQueryClient();
  const { data: token } = useOauthTokenQuery();
  return useMutation({
    mutationFn: async (variables: TVariables) => mutationFn(variables, token),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...featureFlagQueryKeys.all, "flags"] }),
  });
}

export function useSetFeatureFlagOverrideMutation() {
  return useFeatureFlagMutation<SetFeatureFlagParams>(setFeatureFlagOverride);
}

export function useClearFeatureFlagOverrideMutation() {
  return useFeatureFlagMutation<ClearFeatureFlagOverrideParams>(clearFeatureFlagOverride);
}

export function useSetFeatureFlagBaselineMutation() {
  return useFeatureFlagMutation<SetFeatureFlagParams>(setFeatureFlagBaseline);
}
