import * as v from "valibot";

export const filterOperatorTokens = {
  equals: "==",
  notEquals: "!=",
  greaterThan: "=gt=",
  greaterThanOrEqual: "=ge=",
  lessThan: "=lt=",
  lessThanOrEqual: "=le=",
  in: "=in=",
  notIn: "=out=",
  contains: "=contains=",
  matches: "=like=",
  exists: "=exists=",
} as const;

export type FilterOperator = keyof typeof filterOperatorTokens;
export type FilterOperatorToken = (typeof filterOperatorTokens)[FilterOperator];

export const filterOperatorsByToken = Object.fromEntries(
  Object.entries(filterOperatorTokens).map(([operator, token]) => [token, operator]),
) as Record<FilterOperatorToken, FilterOperator>;

export const relationshipFilterPickerSchema = v.object({
  resource: v.pipe(v.string(), v.regex(/^[a-z][a-z0-9-]*$/)),
  identity: v.literal("globalId"),
  globalIdPrefix: v.pipe(v.string(), v.regex(/^[A-Z]+$/)),
});

/** Optional API identity picker; resource selects an application-owned source, never a URL. */
export type RelationshipFilterPicker = v.InferOutput<typeof relationshipFilterPickerSchema>;

export const runtimeFieldValueTypes = ["text", "number", "date", "time", "radio", "choice"] as const;
