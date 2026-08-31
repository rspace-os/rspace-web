import * as v from "valibot";

export const GranteeKindSchema = v.picklist(["USER", "GROUP", "AUDIENCE"]);
export type GranteeKind = v.InferOutput<typeof GranteeKindSchema>;

const SourceGranteeSchema = v.object({
  kind: v.string(),
  id: v.union([v.number(), v.string()]),
  key: v.string(),
  name: v.string(),
});

export const RoleSourceSchema = v.object({
  kind: v.picklist(["DIRECT", "GROUP", "AUDIENCE", "IMPLICIT"]),
  role: v.string(),
  grantee: v.optional(v.nullable(SourceGranteeSchema)),
});

export const ResourceGranteeSchema = v.object({
  kind: GranteeKindSchema,
  id: v.union([v.number(), v.string()]),
  key: v.string(),
  name: v.string(),
  detail: v.optional(v.nullable(v.string())),
  available: v.boolean(),
  effectiveRole: v.optional(v.nullable(v.string())),
  roleSources: v.optional(v.array(RoleSourceSchema), []),
});

export const ResourceAccessAssignmentSchema = v.object({
  grantee: ResourceGranteeSchema,
  role: v.string(),
});

export const ResourceAccessDocumentSchema = v.object({
  scheme: v.string(),
  version: v.number(),
  assignments: v.array(ResourceAccessAssignmentSchema),
  caller: v.object({
    effectiveRole: v.optional(v.nullable(v.string())),
    roleSources: v.optional(v.array(RoleSourceSchema), []),
    capabilities: v.object({
      canManageAssignments: v.boolean(),
      canManageOwners: v.boolean(),
      canLeave: v.boolean(),
    }),
  }),
});

export const ResourceGranteeDirectoryEntrySchema = v.object({
  kind: GranteeKindSchema,
  id: v.number(),
  key: v.string(),
  name: v.string(),
  detail: v.optional(v.nullable(v.string())),
});

export type ResourceAccessDocument = v.InferOutput<typeof ResourceAccessDocumentSchema>;
export type ResourceAccessAssignment = v.InferOutput<typeof ResourceAccessAssignmentSchema>;
export type ResourceGranteeDirectoryEntry = v.InferOutput<typeof ResourceGranteeDirectoryEntrySchema>;
