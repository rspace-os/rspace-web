import { isNotNil } from "es-toolkit";
import type { FilterExpression } from "../types";

/** ANDs the present expressions: `null` for none, the expression itself for one. */
export function allOf<T>(parts: readonly (FilterExpression<T> | null | undefined)[]): FilterExpression<T> | null {
  const children = parts.filter(isNotNil);
  if (children.length === 0) return null;
  if (children.length === 1) return children[0];
  return { kind: "and", children };
}
