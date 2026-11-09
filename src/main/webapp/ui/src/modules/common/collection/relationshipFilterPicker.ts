import * as v from "valibot";

// Imports only valibot, so the API v2 metadata generator can load it under plain `node`.
export const relationshipFilterPickerSchema = v.object({
  resource: v.pipe(v.string(), v.regex(/^[a-z][a-z0-9-]*$/)),
  identity: v.literal("globalId"),
  globalIdPrefix: v.pipe(v.string(), v.regex(/^[A-Z]+$/)),
});

/** Optional API identity picker; resource selects an application-owned source, never a URL. */
export type RelationshipFilterPicker = v.InferOutput<typeof relationshipFilterPickerSchema>;
