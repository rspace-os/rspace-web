import type { FilterExpression } from "@/modules/common/table-list/types";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";

function hasStatusRule(expression: FilterExpression<BookingConfiguration> | null): boolean {
  if (!expression) return false;
  if (expression.kind === "comparison") return expression.field === "state" || expression.field === "enabled";
  return expression.kind === "and" && expression.children.some(hasStatusRule);
}

/** Explicit top-level status rules replace the default status scope for every reader. */
export function allItemsBaseFilter(
  expression: FilterExpression<BookingConfiguration> | null,
): FilterExpression<BookingConfiguration> | undefined {
  return hasStatusRule(expression)
    ? undefined
    : {
        kind: "and",
        children: [
          { kind: "comparison", field: "state", operator: "equals", value: "ACTIVE" },
          { kind: "comparison", field: "enabled", operator: "equals", value: true },
        ],
      };
}
