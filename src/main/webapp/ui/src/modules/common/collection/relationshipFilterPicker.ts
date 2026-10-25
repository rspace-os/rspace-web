import * as v from "valibot";

export const relationshipFilterPickerSchema = v.object({
  resource: v.pipe(v.string(), v.regex(/^[a-z][a-z0-9-]*$/)),
  identity: v.literal("globalId"),
  globalIdPrefix: v.pipe(v.string(), v.regex(/^[A-Z]+$/)),
});

/** Optional API identity picker; resource selects an application-owned source, never a URL. */
export type RelationshipFilterPicker = v.InferOutput<typeof relationshipFilterPickerSchema>;
