import type { FieldConfig, ResolvedFieldCapabilities } from "./config";
import type { FilterOperator } from "./filterOperators";

export type FieldType = FieldConfig<never>["type"];

type FieldTypeRules = {
  /** Defaults a field config may narrow; operators outside this list are incompatible. */
  capabilities: ResolvedFieldCapabilities;
  /** Whether a field config may turn wildcards on, and whether it may be a list search field. */
  textual: boolean;
};

const commonOperators: readonly FilterOperator[] = ["equals", "notEquals", "in", "notIn", "exists"];

/** Field-type rules shared by tables and forms. */
export const fieldTypeRules: Readonly<Record<FieldType, FieldTypeRules>> = {
  text: {
    capabilities: {
      sortable: true,
      filterOperators: [...commonOperators, "contains", "matches"],
      supportsWildcards: true,
    },
    textual: true,
  },
  number: {
    capabilities: {
      sortable: true,
      filterOperators: [...commonOperators, "greaterThan", "greaterThanOrEqual", "lessThan", "lessThanOrEqual"],
      supportsWildcards: false,
    },
    textual: false,
  },
  boolean: {
    capabilities: { sortable: true, filterOperators: ["equals", "notEquals", "exists"], supportsWildcards: false },
    textual: false,
  },
  dateTime: {
    capabilities: {
      sortable: true,
      filterOperators: [
        "equals",
        "notEquals",
        "exists",
        "greaterThan",
        "greaterThanOrEqual",
        "lessThan",
        "lessThanOrEqual",
      ],
      supportsWildcards: false,
    },
    textual: false,
  },
  select: {
    capabilities: {
      sortable: true,
      filterOperators: [...commonOperators, "contains", "matches"],
      supportsWildcards: false,
    },
    textual: true,
  },
  array: { capabilities: { sortable: false, filterOperators: [], supportsWildcards: false }, textual: false },
  object: { capabilities: { sortable: false, filterOperators: [], supportsWildcards: false }, textual: false },
  relationship: {
    capabilities: { sortable: false, filterOperators: commonOperators, supportsWildcards: false },
    textual: false,
  },
};

export function isFilterOperatorCompatible(type: FieldType, operator: FilterOperator): boolean {
  return fieldTypeRules[type].capabilities.filterOperators.includes(operator);
}
