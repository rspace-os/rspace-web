import type { RelationshipSource } from "./relationshipSources";

export function sourceList(source: RelationshipSource | undefined, sources: readonly RelationshipSource[] | undefined) {
  if (sources !== undefined) return sources;
  return source === undefined ? [] : [source];
}

export function cacheScope(token: string | undefined, authScope: string | number | undefined) {
  return [authScope ?? "default", token ?? "anonymous"] as const;
}

export function optionCacheKey(
  source: RelationshipSource | undefined,
  scope: readonly [string | number, string],
  value: string,
) {
  return ["relationship-option", source?.id ?? "unknown", ...scope, value] as const;
}

export function canonicalValue(source: RelationshipSource, value: string): string | null {
  return source.normalizeValue === undefined ? value : source.normalizeValue(value);
}
