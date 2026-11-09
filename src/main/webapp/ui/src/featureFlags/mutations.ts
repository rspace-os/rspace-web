import { updateFeatureFlag, useUpdateFeatureFlag } from "@/modules/common/api/v2/generated/feature-flags/feature-flags";
import type { FeatureFlagsUpdate } from "@/modules/common/api/v2/generated/models/featureFlagsUpdate";
import type { FeatureFlagName } from "./generatedFeatureFlags";

type PatchFeatureFlagParams = { flagName: FeatureFlagName; document: FeatureFlagsUpdate };
export async function patchFeatureFlag({ flagName, document }: PatchFeatureFlagParams): Promise<void> {
  await updateFeatureFlag(flagName, document);
}
export function usePatchFeatureFlagMutation() {
  return useUpdateFeatureFlag();
}
