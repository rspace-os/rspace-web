import type { FilterValue } from "../tableListState";

export type RsqlValue = string | number | boolean;

export function normalizeRsqlScalar(value: FilterValue): RsqlValue {
  if (Array.isArray(value)) throw new Error("Expected a scalar filter value");
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  throw new Error("Null filter values are not supported");
}

export function normalizeRsqlList(value: FilterValue): readonly RsqlValue[] {
  if (!Array.isArray(value) || value.length === 0) throw new Error("List filters require at least one value");
  return value.map((item) => (item instanceof Date ? item.toISOString() : item));
}
