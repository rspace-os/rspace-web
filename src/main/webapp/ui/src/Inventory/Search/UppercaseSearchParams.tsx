import type React from "react";
import { useContext } from "react";
import { Navigate } from "react-router";
import NavigateContext from "../../stores/contexts/Navigate";
import { parseDeletedItems, parseResultType } from "../../stores/definitions/Search";

const ENUM_PARAMS = [
  ["resultType", parseResultType],
  ["deletedItems", parseDeletedItems],
] as const;

/*
 * Hand-typed search links may use lowercase type or status values, such as
 * `resultType=sample`. If the uppercase form of a value is valid, silently
 * redirect to it, so the address bar only ever holds the URL the app itself
 * would write and a copied link is the corrected one. Unknown values are left
 * as they are and fall back to the default search.
 */
export default function UppercaseSearchParams({ children }: { children: React.ReactNode }): React.ReactNode {
  const { useLocation } = useContext(NavigateContext);
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);

  let changed = false;
  for (const [key, parse] of ENUM_PARAMS) {
    const value = searchParams.get(key);
    const uppercase = value?.toUpperCase();
    if (value && uppercase && uppercase !== value && parse(uppercase).isOk) {
      searchParams.set(key, uppercase);
      changed = true;
    }
  }

  if (changed) return <Navigate to={`${location.pathname}?${searchParams.toString()}`} replace={true} />;
  return children;
}
