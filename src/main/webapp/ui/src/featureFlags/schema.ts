import type { FeatureFlagsRead } from "@/modules/common/api/v2/generated/models/featureFlagsRead";
import { FEATURE_FLAGS, type FeatureFlagName } from "./generatedFeatureFlags";

export const featureFlagNames = Object.values(FEATURE_FLAGS);
export type FeatureFlagEntry = Required<Pick<FeatureFlagsRead, "value" | "baselineValue" | "source" | "canOverride">>;

const disabledFeatureFlag = (): FeatureFlagEntry => ({
  value: false,
  baselineValue: false,
  source: "DEFAULT",
  canOverride: false,
});

export const disabledFeatureFlags = () => ({
  flags: Object.fromEntries(featureFlagNames.map((flagName) => [flagName, disabledFeatureFlag()])) as Record<
    FeatureFlagName,
    FeatureFlagEntry
  >,
});

export type FeatureFlagResponse = ReturnType<typeof disabledFeatureFlags>;

export function featureFlagsFromDocuments(documents: readonly FeatureFlagsRead[]): FeatureFlagResponse {
  const response = disabledFeatureFlags();
  for (const document of documents) {
    const name = featureFlagNames.find((name) => name === document.name);
    const { value, baselineValue, source, canOverride } = document;
    if (
      !name ||
      value === undefined ||
      baselineValue === undefined ||
      source === undefined ||
      canOverride === undefined
    )
      continue;
    response.flags[name] = { value, baselineValue, source, canOverride };
  }
  return response;
}
