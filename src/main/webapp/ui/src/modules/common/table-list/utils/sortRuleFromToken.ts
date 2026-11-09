import type { FieldName, SortRule } from "@/modules/common/collection/config";

// Imports only types, so the API v2 metadata generator can load it under plain `node`.

/** The sort rule a `sort` token names: `field` sorts ascending and `-field` descending. */
export function sortRuleFromToken<TDocument>(value: string): SortRule<TDocument> {
  return {
    field: (value.startsWith("-") ? value.slice(1) : value) as FieldName<TDocument>,
    direction: value.startsWith("-") ? "desc" : "asc",
  };
}
