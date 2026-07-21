import { useMutation, useQueryClient } from "@tanstack/react-query";
import { resolveToken } from "@/modules/common/utils/auth";
import type { FeatureFlagName } from "./generatedFeatureFlags";
import { featureFlagQueryKeys, useFeatureFlagApiToken } from "./queries";
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
  path,
  method,
  token,
  value,
}: {
  flagName: FeatureFlagName;
  path: "override" | "baseline";
  method: "PUT" | "DELETE";
  token: string;
  value?: boolean;
}): Promise<void> {
  const response = await fetch(`${FEATURE_FLAGS_API_BASE_URL}/${flagName}/${path}`, {
    method,
    headers: {
      ...featureFlagRequestHeaders(token),
      ...(method === "PUT" ? { "Content-Type": "application/json" } : {}),
    },
    ...(method === "PUT" ? { body: JSON.stringify({ value }) } : {}),
  });

  if (!response.ok) {
    throw toFeatureFlagRequestError(response);
  }
}

export async function setFeatureFlagOverride({ flagName, value }: SetFeatureFlagParams, token: string): Promise<void> {
  return writeFeatureFlag({ flagName, path: "override", method: "PUT", token, value });
}

export async function clearFeatureFlagOverride(
  { flagName }: ClearFeatureFlagOverrideParams,
  token: string,
): Promise<void> {
  return writeFeatureFlag({ flagName, path: "override", method: "DELETE", token });
}

export async function setFeatureFlagBaseline({ flagName, value }: SetFeatureFlagParams, token: string): Promise<void> {
  return writeFeatureFlag({ flagName, path: "baseline", method: "PUT", token, value });
}

function useFeatureFlagMutation<TVariables>(mutationFn: (variables: TVariables, token: string) => Promise<void>) {
  const queryClient = useQueryClient();
  const tokenQuery = useFeatureFlagApiToken();
  return useMutation({
    mutationFn: async (variables: TVariables) =>
      mutationFn(variables, await resolveToken({ token: tokenQuery.data ?? undefined })),
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
