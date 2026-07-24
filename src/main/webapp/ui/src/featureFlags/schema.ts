import * as v from "valibot";
import { FEATURE_FLAGS, type FeatureFlagName } from "./generatedFeatureFlags";

export const featureFlagNames = Object.values(FEATURE_FLAGS) as ReadonlyArray<FeatureFlagName>;

export const FeatureFlagSourceSchema = v.picklist(["DEFAULT", "DATABASE", "USER_OVERRIDE", "PROPERTIES_FILE"]);
export type FeatureFlagSource = v.InferOutput<typeof FeatureFlagSourceSchema>;

export const FeatureFlagEntrySchema = v.object({
  value: v.boolean(),
  baselineValue: v.boolean(),
  source: FeatureFlagSourceSchema,
  canOverride: v.boolean(),
});
export type FeatureFlagEntry = v.InferOutput<typeof FeatureFlagEntrySchema>;

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

export const FeatureFlagResponseSchema = v.fallback(
  v.pipe(
    v.object({
      flags: v.record(v.string(), v.unknown()),
    }),
    v.transform(({ flags }) => {
      const response = disabledFeatureFlags();
      for (const flagName of featureFlagNames) {
        const result = v.safeParse(FeatureFlagEntrySchema, flags[flagName]);
        if (result.success) response.flags[flagName] = result.output;
      }
      return response;
    }),
  ),
  disabledFeatureFlags,
);
export type FeatureFlagResponse = v.InferOutput<typeof FeatureFlagResponseSchema>;

export const FeatureFlagApiTokenResponseSchema = v.object({
  data: v.string(),
});
