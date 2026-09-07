import * as v from "valibot";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
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

const FeatureFlagDocumentSchema = v.object({
  name: v.string(),
  ...FeatureFlagEntrySchema.entries,
});

export const FeatureFlagPageSchema = v2ListEnvelope(v.unknown());

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

export function featureFlagsFromDocuments(documents: ReadonlyArray<unknown>): FeatureFlagResponse {
  const response = disabledFeatureFlags();
  for (const document of documents) {
    const result = v.safeParse(FeatureFlagDocumentSchema, document);
    if (!result.success || !featureFlagNames.includes(result.output.name as FeatureFlagName)) continue;
    const { name, ...entry } = result.output;
    response.flags[name as FeatureFlagName] = entry;
  }
  return response;
}

export const FeatureFlagResponseSchema = v.fallback(
  v.pipe(
    FeatureFlagPageSchema,
    v.transform(({ docs }) => featureFlagsFromDocuments(docs)),
  ),
  disabledFeatureFlags,
);
