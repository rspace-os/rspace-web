import { invert } from "es-toolkit";
import { apiV2WhereOperators } from "@/modules/common/api/v2/apiV2Where";

/** Filter operators are the typed REST API v2 `where` operator names; each maps to its RSQL token. */
export type FilterOperator = keyof typeof apiV2WhereOperators;
export type FilterOperatorToken = (typeof apiV2WhereOperators)[FilterOperator];

export const filterOperatorsByToken = invert(apiV2WhereOperators);
