import * as v from "valibot";
import {
  AjaxOperationFailure,
  AjaxOperationFailureResponseSchema,
} from "@/modules/common/api/schema";


// RaID Reference DTO Schema
export const RaIDReferenceDTOSchema = v.object({
  raidServerAlias: v.string(),
  raidIdentifier: v.string(),
  raidTitle: v.string(),
});

export type RaIDReferenceDTO = v.InferOutput<typeof RaIDReferenceDTOSchema>;

// Ajax return object for RaID list endpoint
export const GetAvailableRaIDListResponseSchema = v.variant("success", [
  v.object({
    success: v.literal(true),
    data: v.array(RaIDReferenceDTOSchema),
  }),
  AjaxOperationFailureResponseSchema,
]);

export type GetAvailableRaIDListResponse = v.InferOutput<
  typeof GetAvailableRaIDListResponseSchema
>;

// Schema for the credentials stored under each options key
export const RaIDOptionValueSchema = v.object({
  RAID_OAUTH_CONNECTED: v.pipe(v.string(), v.transform((val) => val === "true")),
  RAID_URL: v.pipe(v.string(), v.url()),
  RAID_ALIAS: v.string(),
});

export const RaIDConfiguredOptionValueSchema = v.object({
  url: v.pipe(v.string(), v.url()),
  alias: v.string(),
});

// Data object for the integration info
export const IntegrationRaidInfoDataSchema = v.object({
  name: v.literal("RAID"),
  displayName: v.literal("RaID"),
  available: v.boolean(),
  enabled: v.boolean(),
  oauthConnected: v.boolean(),
  options: v.objectWithRest(
    {
      RAID_CONFIGURED_SERVERS: v.optional(
        v.array(RaIDConfiguredOptionValueSchema),
      ),
    },
    RaIDOptionValueSchema,
  )
});

// Full response schema
export const IntegrationRaidInfoResponseSchema = v.variant("success", [
  v.object({
    success: v.literal(true),
    data: IntegrationRaidInfoDataSchema,
  }),
  AjaxOperationFailureResponseSchema,
]);

export type IntegrationRaidInfo = v.InferOutput<
  typeof IntegrationRaidInfoResponseSchema
>;

export type AddRaidIdentifierAssociationResponse = true | AjaxOperationFailure;
export type DeleteRaidIdentifierAssociationResponse = true | AjaxOperationFailure;

export const AssociateRaidIdentifierRequestBodySchema = v.object({
  projectGroupId: v.number(),
  raid: v.object({
    raidServerAlias: v.string(),
    raidIdentifier: v.string(),
  })
});

export type AssociateRaidIdentifierRequestBody = v.InferOutput<
  typeof AssociateRaidIdentifierRequestBodySchema
>;