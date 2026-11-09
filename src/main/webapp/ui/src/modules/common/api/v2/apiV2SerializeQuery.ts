import { type ApiV2FilterResource, type ApiV2WhereObject, apiV2WhereToRsql } from "./apiV2Where";

/**
 * Orval `paramsSerializer` for collection operations whose `where` carries filter metadata. It serializes
 * exactly like Orval's generated default (scalars through URLSearchParams, `null` as "null", object values
 * as deepObject `key[sub]=` entries after them), except that a `where` object becomes RSQL.
 */
export function apiV2SerializeQuery(params?: Record<string, unknown>): string {
  const normalizedParams = new URLSearchParams();
  const deepObjectEntries: string[] = [];
  for (const [key, value] of Object.entries(params ?? {})) {
    if (key === "where" && typeof value === "object" && value !== null) {
      // The generated parameter type already restricts the object to the operation's filter.
      const where = apiV2WhereToRsql(value as ApiV2WhereObject<ApiV2FilterResource>);
      if (where) normalizedParams.append(key, where);
      continue;
    }
    if (typeof value === "object" && value !== null && !Array.isArray(value)) {
      for (const [subKey, subValue] of Object.entries(value)) {
        if (subValue !== undefined)
          deepObjectEntries.push(
            `${encodeURIComponent(key)}[${encodeURIComponent(subKey)}]=${subValue === null ? "null" : encodeURIComponent(String(subValue))}`,
          );
      }
      continue;
    }
    if (value !== undefined) normalizedParams.append(key, value === null ? "null" : String(value));
  }
  return [normalizedParams.toString(), deepObjectEntries.join("&")].filter(Boolean).join("&");
}
