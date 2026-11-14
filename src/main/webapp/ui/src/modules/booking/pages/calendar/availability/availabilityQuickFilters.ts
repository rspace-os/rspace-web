import { useQuery } from "@tanstack/react-query";
import type { BookingCatalogueItem } from "@/modules/booking/domain/bookingCatalogue";
import {
  bookingCatalogueAvailabilityCountsQuery,
  type CatalogueAvailability,
  type CatalogueAvailabilityWindow,
} from "@/modules/booking/domain/bookingCatalogue";
import { type AbsoluteDisplayInterval, currentWallClock, displayInterval } from "@/modules/booking/domain/bookingTime";
import { togglePreset, withoutPresets } from "@/modules/common/table-list/presets";
import type { FilterExpression } from "@/modules/common/table-list/types";
import type { BookingConfiguration } from "../../bookable-items/bookingConfiguration";

export type AvailabilityQuickFilter = CatalogueAvailability;
export type AllBookableItem = BookingConfiguration & {
  target: (NonNullable<BookingConfiguration["target"]> & { location?: BookingCatalogueItem["location"] }) | null;
  availability?: AvailabilityQuickFilter;
};

type Comparison = Extract<FilterExpression<AllBookableItem>, { kind: "comparison" }>;

export function hasAvailabilityFilter(expression: FilterExpression<AllBookableItem> | null): boolean {
  return (
    expression !== null &&
    (expression.kind === "comparison"
      ? expression.field === "availability"
      : expression.children.some(hasAvailabilityFilter))
  );
}

export const availabilityPresets = ["available-now", "free-later-today"].map(
  (value) => ({ kind: "comparison", field: "availability", operator: "equals", value }) as const,
);

/** Replaces the exclusive top-level availability preset, preserving OR subtrees. */
export function withAvailability(
  expression: FilterExpression<AllBookableItem> | null,
  availability: AvailabilityQuickFilter | undefined,
): FilterExpression<AllBookableItem> | null {
  const remaining = withoutPresets(expression, availabilityPresets);
  return availability
    ? togglePreset(remaining, { kind: "comparison", field: "availability", operator: "equals", value: availability })
    : remaining;
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
  const scope = (key: readonly unknown[]) =>
    JSON.stringify(key, (name, value: unknown) => (name === "now" ? undefined : value));
  const current = scope(queryKey);
  return <T>(previous: T | undefined, previousQuery?: { queryKey: readonly unknown[] }): T | undefined =>
    previousQuery && scope(previousQuery.queryKey) === current ? previous : undefined;
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
  authScope: string | number,
  search: AvailabilityCountsSearch,
  today: TodayAvailabilityWindow,
  enabled = true,
) {
  const options = bookingCatalogueAvailabilityCountsQuery(authScope, search, today);
  return useQuery({ ...options, enabled, placeholderData: keepAcrossMinutes(options.queryKey) });
}
