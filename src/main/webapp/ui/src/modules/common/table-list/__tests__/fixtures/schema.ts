import * as v from "valibot";

export const testRecordSchema = v.object({
  id: v.string(),
  title: v.string(),
  owner: v.string(),
  score: v.number(),
  enabled: v.boolean(),
  modifiedAt: v.string(),
  capability: v.optional(v.boolean()),
});

export const expandedRelationshipSchema = v.object({
  relationTo: v.literal("instruments"),
  value: v.object({
    id: v.number(),
    name: v.string(),
    deleted: v.boolean(),
    label: v.optional(v.string()),
    floor: v.optional(v.number()),
  }),
});

export const bookingRelationshipsSchema = v.object({
  id: v.string(),
  target: expandedRelationshipSchema,
  room: expandedRelationshipSchema,
});

export const ownerlessDocumentSchema = v.object({ id: v.string(), title: v.string() });

export const runtimeDocumentSchema = v.object({ id: v.number(), name: v.string() });

export const tableListDocumentSchema = v.object({ id: v.number(), name: v.string(), target: v.optional(v.string()) });

export const expandedTargetDocumentSchema = v.object({
  id: v.string(),
  target: v.object({
    relationTo: v.literal("instruments"),
    value: v.object({ id: v.number(), name: v.string() }),
  }),
});
