import * as v from "valibot";
import { RaIDReferenceDTOSchema } from "@/modules/raid/schema";

// User Group Info Schema - represents a user in a group
export const UserGroupInfoSchema = v.object({
  id: v.number(),
  username: v.string(),
  role: v.picklist(["PI", "LAB_ADMIN", "USER", "GROUP_OWNER"]),
});

export type UserGroupInfo = v.InferOutput<typeof UserGroupInfoSchema>;

// Group Info Schema - represents a group
export const GroupInfoSchema = v.object({
  id: v.number(),
  globalId: v.string(),
  name: v.string(),
  type: v.picklist(["LAB_GROUP", "COLLABORATION_GROUP", "PROJECT_GROUP"]),
  sharedFolderId: v.number(),
  sharedSnippetFolderId: v.number(),
  members: v.array(UserGroupInfoSchema),
  raid: v.nullable(
    RaIDReferenceDTOSchema,
  ),
});

export type GroupInfo = v.InferOutput<typeof GroupInfoSchema>;
