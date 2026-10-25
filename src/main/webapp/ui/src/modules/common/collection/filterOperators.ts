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
