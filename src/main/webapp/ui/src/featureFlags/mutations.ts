import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import type { FeatureFlagName } from "./generatedFeatureFlags";
import { featureFlagQueryKeys } from "./queries";
import { FEATURE_FLAGS_API_BASE_URL, featureFlagRequestHeaders, toFeatureFlagRequestError } from "./utils";

export type PatchFeatureFlagParams = {
  flagName: FeatureFlagName;
  document: { overrideValue: boolean | null } | { baselineValue: boolean };
};

export async function patchFeatureFlag({ flagName, document }: PatchFeatureFlagParams, token: string): Promise<void> {
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

export function usePatchFeatureFlagMutation() {
  const queryClient = useQueryClient();
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  return useMutation({
    mutationFn: async (variables: PatchFeatureFlagParams) => patchFeatureFlag(variables, token),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...featureFlagQueryKeys.all, "flags"] }),
  });
}
