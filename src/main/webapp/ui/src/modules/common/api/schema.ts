// Error item schema for validation errors
import * as v from "valibot";

export const AjaxErrorItemSchema = v.object({
  field: v.string(),
  errorCode: v.string(),
  defaultMessage: v.string(),
});

// Error list schema
export const AjaxErrorListSchema = v.object({
  errorMessages: v.array(AjaxErrorItemSchema),
});

export const AjaxOperationFailureResponseSchema = v.object({
  success: v.literal(false),
  error: AjaxErrorListSchema,
  errorMsg: v.string(),
});

export type AjaxOperationFailure = v.InferOutput<typeof AjaxOperationFailureResponseSchema>;

// Error Schema
export const AjaxErrorSchema = v.object({
  status: v.string(),
  httpCode: v.number(),
  internalCode: v.number(),
  message: v.string(),
});

export type AjaxError = v.InferOutput<typeof AjaxErrorSchema>;

export const RestApiErrorSchema = v.object({
  status: v.string(),
  httpCode: v.number(),
  internalCode: v.number(),
  message: v.string(),
  messageCode: v.nullable(v.string()),
  errors: v.array(v.string()),
  iso8601Timestamp: v.string(),
  data: v.nullable(v.any()),
});

export type RestApiError = v.InferOutput<typeof RestApiErrorSchema>;
