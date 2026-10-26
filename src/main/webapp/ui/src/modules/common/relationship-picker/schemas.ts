import * as v from "valibot";

export const InstrumentSchema = v.object({
  id: v.number(),
  name: v.string(),
  globalId: v.string(),
});

export const GranteeSchema = v.object({
  kind: v.picklist(["USER", "GROUP", "AUDIENCE"]),
  id: v.union([v.number(), v.string()]),
  key: v.string(),
  name: v.string(),
  detail: v.optional(v.nullable(v.string())),
});

export const RelationshipSearchResponseSchema = v.object({ docs: v.array(v.unknown()) });
