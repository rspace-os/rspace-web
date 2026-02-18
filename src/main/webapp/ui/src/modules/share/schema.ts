import * as v from "valibot";

export const SharePermissionSchema = v.picklist(["READ", "EDIT"]);
export type SharePermission = v.InferOutput<typeof SharePermissionSchema>;

export const ShareTargetTypeSchema = v.picklist(["GROUP", "USER"]);
export type ShareTargetType = v.InferOutput<typeof ShareTargetTypeSchema>;

const ShareBaseSchema = v.object({
  sharedItemId: v.number(),
  shareItemName: v.string(),
  sharedTargetId: v.number(),
  sharedTargetType: ShareTargetTypeSchema,
  permission: SharePermissionSchema,
});

export const ShareInfoSchema = v.object({
  id: v.number(),
  ...ShareBaseSchema.entries,
});

export type ShareInfo = v.InferOutput<typeof ShareInfoSchema>;

export const SharedListSchema = v.array(ShareInfoSchema);
export type SharedList = v.InferOutput<typeof SharedListSchema>;

export const ShareFolderInfoSchema = v.object({
  id: v.null(),
  ...ShareBaseSchema.entries,
});

export type ShareFolderInfo = v.InferOutput<typeof ShareFolderInfoSchema>;

export const SharedFolderListSchema = v.array(ShareFolderInfoSchema);
export type SharedFolderList = v.InferOutput<typeof SharedFolderListSchema>;

export type ShareLike = ShareInfo | ShareFolderInfo;

export const ShareTargetInfoSchema = v.object({
  sharedTargetId: v.number(),
  sharedTargetType: ShareTargetTypeSchema,
  permission: v.optional(SharePermissionSchema),
});
export type ShareTargetInfo = v.InferOutput<typeof ShareTargetInfoSchema>;

export const LinkItemSchema = v.object({
  link: v.string(),
  rel: v.string(),
});
export type LinkItem = v.InferOutput<typeof LinkItemSchema>;

export const LinkItemListSchema = v.array(LinkItemSchema);
export type LinkItemList = v.InferOutput<typeof LinkItemListSchema>;

export const ShareSearchResponseSchema = v.object({
  totalHits: v.number(),
  pageNumber: v.number(),
  shares: SharedListSchema,
  folderShares: v.optional(SharedFolderListSchema),
  _links: LinkItemListSchema,
});

export type ShareSearchResponse = v.InferOutput<
  typeof ShareSearchResponseSchema
>;
