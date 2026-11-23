import * as v from "valibot";
import { apiV2WhereOperators } from "@/modules/common/api/v2/apiV2Where";

/** Filter operators are the typed REST API v2 `where` operator names; each maps to its RSQL token. */
export type FilterOperator = keyof typeof apiV2WhereOperators;
export type FilterOperatorToken = (typeof apiV2WhereOperators)[FilterOperator];

export const filterOperatorsByToken = Object.fromEntries(
  Object.entries(apiV2WhereOperators).map(([operator, token]) => [token, operator]),
) as Record<FilterOperatorToken, FilterOperator>;

export const relationshipFilterPickerSchema = v.object({
  resource: v.pipe(v.string(), v.regex(/^[a-z][a-z0-9-]*$/)),
  identity: v.literal("globalId"),
  globalIdPrefix: v.pipe(v.string(), v.regex(/^[A-Z]+$/)),
});

/** Optional API identity picker; resource selects an application-owned source, never a URL. */
export type RelationshipFilterPicker = v.InferOutput<typeof relationshipFilterPickerSchema>;

export const runtimeFieldValueTypes = ["text", "number", "date", "time", "radio", "choice"] as const;
