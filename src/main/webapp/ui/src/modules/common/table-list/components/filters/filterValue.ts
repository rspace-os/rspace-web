export type FilterValue = string | readonly string[];

export function scalarValue(value: FilterValue): string {
  return typeof value === "string" ? value : (value[0] ?? "");
}

export function listValue(value: FilterValue): readonly string[] {
  if (typeof value !== "string") return value;
  const trimmed = value.trim();
  return trimmed === "" ? [] : [trimmed];
}
