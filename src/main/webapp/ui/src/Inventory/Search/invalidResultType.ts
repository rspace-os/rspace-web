import { parseResultType } from "../../stores/definitions/Search";

/**
 * Returns the `resultType` value of an Inventory search URL when it cannot be
 * listed, or null when the URL is fine. ALL is an internal search state, never
 * a valid listing type in a URL; a missing or empty value means the default.
 */
export function invalidResultType(urlSearchParams: URLSearchParams): string | null {
  const value = urlSearchParams.get("resultType");
  if (value === null || value === "") return null;
  const isListable = parseResultType(value)
    .map((type) => type !== "ALL")
    .orElse(false);
  return isListable ? null : value;
}
