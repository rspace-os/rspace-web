import { useQuery } from "@tanstack/react-query";
import {
  type CatalogueAvailability,
  type CatalogueAvailabilityWindow,
  fetchBookingCatalogueAvailabilityCounts,
} from "@/modules/booking/domain/bookingCatalogue";
import { type AbsoluteDisplayInterval, currentWallClock, displayInterval } from "@/modules/booking/domain/bookingTime";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";

export type AvailabilityQuickFilter = CatalogueAvailability;
export type AllBookableItem = BookingConfiguration & { availability?: AvailabilityQuickFilter };

type Comparison = Extract<FilterExpression<AllBookableItem>, { kind: "comparison" }>;

export function hasAvailabilityFilter(expression: FilterExpression<AllBookableItem> | null): boolean {
  return (
    expression !== null &&
    (expression.kind === "comparison"
      ? expression.field === "availability"
      : expression.children.some(hasAvailabilityFilter))
  );
}

/** Replaces only availability predicates; other rules and their grouping remain intact. */
export function withAvailability(
  expression: FilterExpression<AllBookableItem> | null,
  availability: AvailabilityQuickFilter | undefined,
): FilterExpression<AllBookableItem> | null {
  let remaining = expression;
  if (expression?.kind === "comparison") {
    if (expression.field === "availability") remaining = null;
  } else if (expression) {
    const children = expression.children.flatMap((child) => {
      const next = withAvailability(child, undefined);
      return next ? [next] : [];
    });
    remaining = children.length === 0 ? null : children.length === 1 ? children[0] : { ...expression, children };
  }
  if (!availability) return remaining;
  const rule: FilterExpression<AllBookableItem> = {
    kind: "comparison",
    field: "availability",
    operator: "equals",
    value: availability,
  };
  return remaining
    ? { kind: "and", children: remaining.kind === "and" ? [...remaining.children, rule] : [remaining, rule] }
    : rule;
}

function conjuncts(expression: FilterExpression<AllBookableItem>): FilterExpression<AllBookableItem>[] {
  return expression.kind === "and" ? expression.children.flatMap(conjuncts) : [expression];
}

const MATCHES_NOTHING: FilterExpression<AllBookableItem> = {
  kind: "comparison",
  field: "id",
  operator: "equals",
  value: 0,
};

/** The item rules, and the one availability category, that the catalogue request carries. */
export type ServerAvailabilityFilter =
  | { supported: true; where: FilterExpression<AllBookableItem> | null; availability?: AvailabilityQuickFilter }
  | { supported: false };

/**
 * Splits a filter into the catalogue's item rules and its `availability` parameter.
 *
 * The server applies one availability category as a conjunct, so availability rules must be
 * top-level AND rules, which is all the filter builder writes. Different categories cannot hold at
 * once, so they match nothing. An availability rule inside an OR group cannot be sent.
 */
export function serverAvailabilityFilter(
  expression: FilterExpression<AllBookableItem> | null,
): ServerAvailabilityFilter {
  if (!expression || !hasAvailabilityFilter(expression)) return { supported: true, where: expression };
  const rules = conjuncts(expression);
  const availability = rules.filter(
    (rule): rule is Comparison => rule.kind === "comparison" && rule.field === "availability",
  );
  if (rules.some((rule) => rule.kind !== "comparison" && hasAvailabilityFilter(rule))) return { supported: false };
  const categories = new Set(availability.map((rule) => rule.value));
  const [category] = categories;
  if (
    availability.some((rule) => rule.operator !== "equals") ||
    (category !== "available-now" && category !== "free-later-today")
  ) {
    return { supported: false };
  }
  const where = withAvailability(expression, undefined);
  if (categories.size > 1) {
    return {
      supported: true,
      where: where ? { kind: "and", children: [...conjuncts(where), MATCHES_NOTHING] } : MATCHES_NOTHING,
    };
  }
  return { supported: true, where, availability: category };
}

export type TodayAvailabilityWindow = CatalogueAvailabilityWindow & {
  /** Today in the display time zone. */
  date: string;
  bounds: AbsoluteDisplayInterval;
};

/**
 * Today's availability window in the display time zone for the page clock's current minute. Quick
 * filters always describe today, whatever date the page shows.
 */
export function todayAvailabilityWindow(
  now: Date,
  displayTimeZone: string,
  windowStart: string,
  windowEnd: string,
): TodayAvailabilityWindow {
  const date = currentWallClock(now.toISOString(), displayTimeZone).date;
  const bounds = displayInterval(date, displayTimeZone, windowStart, windowEnd);
  return { date, bounds, start: bounds.start, end: bounds.end, now: now.toISOString() };
}

/**
 * Keeps a query's previous result while only the last key element, the minute, changed, so rows
 * and counts refresh each minute without flashing back to a loading state.
 */
export function keepAcrossMinutes(queryKey: readonly unknown[]) {
  const scope = JSON.stringify(queryKey.slice(0, -1));
  return <T>(previous: T | undefined, previousQuery?: { queryKey: readonly unknown[] }): T | undefined =>
    previousQuery && JSON.stringify(previousQuery.queryKey.slice(0, -1)) === scope ? previous : undefined;
}

export type AvailabilityCountsSearch = {
  q?: string;
  types?: readonly string[];
  mine?: boolean;
  /** Item rules without availability. */
  where?: string;
};

/** Both quick-filter counts from the server, refreshed each minute. */
export function useAvailabilityCounts(
  token: string,
  authScope: string | number,
  search: AvailabilityCountsSearch,
  today: TodayAvailabilityWindow,
  enabled = true,
) {
  const queryKey = [
    "api-v2",
    "bookings",
    "booking-catalogue",
    "availability-counts",
    authScope,
    search.q,
    search.types,
    search.mine,
    search.where,
    today.start,
    today.end,
    today.now,
  ] as const;
  return useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchBookingCatalogueAvailabilityCounts(search, today, token, signal),
    enabled: enabled && token.length > 0,
    staleTime: 30_000,
    placeholderData: keepAcrossMinutes(queryKey),
  });
}
