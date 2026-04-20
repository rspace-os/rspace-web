import * as v from "valibot";

export const WorkspaceRecordOidSchema = v.objectWithRest(
  {
    idString: v.string(),
    dbId: v.optional(v.number()),
  },
  v.unknown(),
);
export type WorkspaceRecordOid = v.InferOutput<typeof WorkspaceRecordOidSchema>;

export const WorkspaceRecordEditStatusSchema = v.picklist([
  "VIEW_MODE",
  "EDIT_MODE",
  "CANNOT_EDIT_OTHER_EDITING",
  "CANNOT_EDIT_NO_PERMISSION",
  "CAN_NEVER_EDIT",
]);
export type WorkspaceRecordEditStatus = v.InferOutput<
  typeof WorkspaceRecordEditStatusSchema
>;

export const WorkspaceRecordSignatureStatusSchema = v.picklist([
  "UNSIGNED",
  "SIGNED_AND_LOCKED",
  "AWAITING_WITNESS",
  "WITNESSED",
  "UNSIGNABLE",
  "SIGNED_AND_LOCKED_WITNESSES_DECLINED",
]);
export type WorkspaceRecordSignatureStatus = v.InferOutput<
  typeof WorkspaceRecordSignatureStatusSchema
>;

export const WorkspaceSharingMapSchema = v.record(v.string(), v.string());
export type WorkspaceSharingMap = v.InferOutput<typeof WorkspaceSharingMapSchema>;

export const WorkspaceRecordInformationSchema = v.objectWithRest(
  {
    id: v.number(),
    oid: WorkspaceRecordOidSchema,
    name: v.string(),
    type: v.string(),
    ownerFullName: v.optional(v.string()),
    ownerUsername: v.optional(v.string()),
    createdBy: v.optional(v.nullable(v.string())),
    description: v.optional(v.nullable(v.string())),
    extension: v.optional(v.nullable(v.string())),
    size: v.optional(v.nullable(v.number())),
    version: v.optional(v.nullable(v.number())),
    thumbnailId: v.optional(v.nullable(v.number())),
    widthResized: v.optional(v.nullable(v.number())),
    heightResized: v.optional(v.nullable(v.number())),
    modificationDate: v.optional(v.nullable(v.number())),
    revision: v.optional(v.nullable(v.number())),
    path: v.optional(v.nullable(v.string())),
    tags: v.optional(v.nullable(v.string())),
    fromImport: v.optional(v.boolean()),
    originalOwnerUsernamePreImport: v.optional(v.nullable(v.string())),
    creationDateWithClientTimezoneOffset: v.optional(v.string()),
    modificationDateWithClientTimezoneOffset: v.optional(v.string()),
    status: v.optional(WorkspaceRecordEditStatusSchema),
    currentEditor: v.optional(v.nullable(v.string())),
    signed: v.optional(v.boolean()),
    signatureStatus: v.optional(v.nullable(WorkspaceRecordSignatureStatusSchema)),
    linkedByCount: v.optional(v.number()),
    templateFormName: v.optional(v.nullable(v.string())),
    templateFormId: v.optional(v.nullable(WorkspaceRecordOidSchema)),
    templateName: v.optional(v.nullable(v.string())),
    templateOid: v.optional(v.nullable(v.string())),
    originalImageOid: v.optional(v.nullable(WorkspaceRecordOidSchema)),
    shared: v.optional(v.boolean()),
    implicitlyShared: v.optional(v.boolean()),
    sharedGroupsAndAccess: v.optional(WorkspaceSharingMapSchema),
    sharedUsersAndAccess: v.optional(WorkspaceSharingMapSchema),
    sharedNotebooksAndOwners: v.optional(WorkspaceSharingMapSchema),
    implicitShares: v.optional(WorkspaceSharingMapSchema),
  },
  v.unknown(),
);
export type WorkspaceRecordInformation = v.InferOutput<
  typeof WorkspaceRecordInformationSchema
>;

export const WorkspaceGetRecordInformationResponseSchema = v.objectWithRest(
  {
    data: v.nullable(WorkspaceRecordInformationSchema),
    error: v.optional(v.nullable(v.unknown())),
    errorMsg: v.optional(v.nullable(v.unknown())),
    success: v.optional(v.boolean()),
  },
  v.unknown(),
);
export type WorkspaceGetRecordInformationResponse = v.InferOutput<
  typeof WorkspaceGetRecordInformationResponseSchema
>;

