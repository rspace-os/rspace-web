import { useQuery } from "@tanstack/react-query";
import {
  type AvailabilityInterval,
  type CurrentDayAvailability,
  classifyCurrentDayAvailability,
} from "@/modules/booking/domain/availability";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { type AbsoluteDisplayInterval, currentWallClock, displayInterval } from "@/modules/booking/domain/bookingTime";
import { useAlignedMinute } from "@/modules/booking/hooks/useAlignedMinute";
import { viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import { loadCalendarAvailability } from "../calendar/calendarAvailability";

export type AvailabilityQuickFilter = "available-now" | "free-later-today";
export type AllBookableItem = BookingConfiguration & { availability?: AvailabilityQuickFilter };

export function hasAvailabilityFilter(expression: FilterExpression<AllBookableItem> | null): boolean {
  return (
    expression !== null &&
    (expression.kind === "comparison"
      ? expression.field === "availability"
      : expression.children.some(hasAvailabilityFilter))
  );
}

/**
 * Derives the safe item-only predicate to use while loading availability candidates.
 *
 * Availability is computed locally, so its comparisons become `true`. A local
 * comparison in an OR branch makes that branch unrestricted; a local comparison
 * in an AND branch can simply be removed. Returning null means the candidate
 * request must remain unrestricted.
 */
export function deriveAvailabilityCandidateFilter(
  expression: FilterExpression<AllBookableItem> | null,
): FilterExpression<AllBookableItem> | null {
  if (!expression) return null;
  if (expression.kind === "comparison") return expression.field === "availability" ? null : expression;

  const children = expression.children.map(deriveAvailabilityCandidateFilter);
  if (expression.kind === "or" && children.some((child) => child === null)) return null;

  const remaining = children.filter((child): child is FilterExpression<AllBookableItem> => child !== null);
  if (remaining.length === 0) return null;
  if (remaining.length === 1) return remaining[0];
  return { ...expression, children: remaining };
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

/** Converts the derived availability field to ordinary server-side target predicates. */
export function resolveAvailabilityFilters(
  expression: FilterExpression<AllBookableItem> | null,
  index: ReadonlyMap<string, AvailabilityQuickIndexEntry> | undefined,
): FilterExpression<AllBookableItem> | null {
  if (!expression) return null;
  if (expression.kind !== "comparison")
    return {
      ...expression,
      children: expression.children.map((child) => resolveAvailabilityFilters(child, index) ?? child),
    };
  if (expression.field !== "availability") return expression;
  const ids = [...(index ?? [])].flatMap(([id, entry]) => (entry.category === expression.value ? [id] : []));
  return ids.length > 0
    ? { kind: "comparison", field: "target", operator: "in", value: ids }
    : { kind: "comparison", field: "id", operator: "equals", value: 0 };
}

export type AvailabilityQuickIndexEntry = {
  date: string;
  bounds: AbsoluteDisplayInterval;
  intervals: readonly AvailabilityInterval[];
  category: CurrentDayAvailability;
};

type AvailabilityCandidate = Pick<
  BookingConfiguration,
  | "target"
  | "timezone"
  | "slotGranularityMinutes"
  | "openingStart"
  | "openingEnd"
  | "bufferBeforeMinutes"
  | "bufferAfterMinutes"
  | "maxBookingDurationMinutes"
  | "allowDoubleBooking"
>;
type CandidateSearch = { q?: string; types?: readonly string[]; mine?: boolean };

export class AvailabilityCandidateLimitError extends Error {
  constructor() {
    super("Availability quick filters support at most 1,000 bookable items");
    this.name = "AvailabilityCandidateLimitError";
  }
}

const now = () => new Date();
export async function fetchAvailabilityCandidates(
  token: string,
  signal: AbortSignal,
  candidateWhere?: string,
  search: CandidateSearch = {},
): Promise<readonly AvailabilityCandidate[]> {
  const fetchPage = (page: number) =>
    fetchBookingCatalogue({ ...search, where: candidateWhere, page, pageSize: 100 }, token, signal);
  const first = await fetchPage(1);
  if (first.total > 1000) throw new AvailabilityCandidateLimitError();
  const candidates = [...first.items];
  for (let page = 2; page <= Math.ceil(first.total / first.pageSize); page += 1) {
    candidates.push(...(await fetchPage(page)).items);
    if (candidates.length > 1000) throw new AvailabilityCandidateLimitError();
  }
  return candidates.map(catalogueItemAsConfiguration);
}

export async function loadAvailabilityQuickIndex(
  candidates: readonly AvailabilityCandidate[],
  current: Date,
  displayTimeZone: string,
  availabilityWindowStart: string,
  availabilityWindowEnd: string,
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, AvailabilityQuickIndexEntry>> {
  const date = currentWallClock(current.toISOString(), displayTimeZone).date;
  const bounds = displayInterval(date, displayTimeZone, availabilityWindowStart, availabilityWindowEnd);
  const rows = candidates.flatMap((candidate) =>
    candidate.target
      ? [
          {
            globalId: candidate.target.globalId,
            timezone: candidate.timezone,
            openingStart: candidate.openingStart,
            openingEnd: candidate.openingEnd,
            bufferBeforeMinutes: candidate.bufferBeforeMinutes,
            bufferAfterMinutes: candidate.bufferAfterMinutes,
            maxBookingDurationMinutes: candidate.maxBookingDurationMinutes,
            allowDoubleBooking: candidate.allowDoubleBooking,
          },
        ]
      : [],
  );
  const availability = await loadCalendarAvailability(rows, bounds, token, signal);
  const index = new Map<string, AvailabilityQuickIndexEntry>();
  for (const row of rows) {
    const intervals = availability.get(row.globalId) ?? [];
    index.set(row.globalId, {
      date,
      bounds,
      intervals,
      category: classifyCurrentDayAvailability(intervals, new Date(bounds.start), new Date(bounds.end), current),
    });
  }
  return index;
}

export function useAvailabilityQuickFilterIndex(
  token: string,
  displayTimeZone = "UTC",
  availabilityWindowStart = "00:00",
  availabilityWindowEnd = "24:00",
  clock: () => Date = now,
  candidateWhere?: string,
  enabled = true,
  authScope: string | number = token,
  search: CandidateSearch = {},
) {
  const queryEnabled = enabled && token.length > 0;
  const minute = useAlignedMinute(clock);
  const candidates = useQuery({
    queryKey: [
      "api-v2",
      "booking-catalogue",
      "availability-candidates",
      authScope,
      candidateWhere,
      search.q,
      search.types,
      search.mine,
    ],
    queryFn: ({ signal }) => fetchAvailabilityCandidates(token, signal, candidateWhere, search),
    enabled: queryEnabled,
    staleTime: 60_000,
    retry: (failureCount, error) => !(error instanceof AvailabilityCandidateLimitError) && failureCount < 3,
  });
  const signature = candidates.data
    ?.flatMap((candidate) =>
      candidate.target
        ? [
            [
              candidate.target.globalId,
              candidate.timezone,
              candidate.openingStart,
              candidate.openingEnd,
              candidate.bufferBeforeMinutes,
              candidate.bufferAfterMinutes,
              candidate.maxBookingDurationMinutes,
              candidate.allowDoubleBooking,
            ] as const,
          ]
        : [],
    )
    .toSorted(([left], [right]) => left.localeCompare(right));
  const index = useQuery({
    queryKey: [
      "api-v2",
      "bookings",
      "availability-quick-index",
      authScope,
      signature,
      minute,
      displayTimeZone,
      availabilityWindowStart,
      availabilityWindowEnd,
    ],
    queryFn: ({ signal }) =>
      loadAvailabilityQuickIndex(
        candidates.data ?? [],
        new Date(minute),
        displayTimeZone,
        availabilityWindowStart,
        availabilityWindowEnd,
        token,
        signal,
      ),
    enabled: queryEnabled && candidates.isSuccess,
    meta: viewTransitionQueryMeta,
  });
  return {
    data: queryEnabled ? index.data : undefined,
    now: new Date(minute),
    isPending: queryEnabled && !candidates.isError && (candidates.isPending || index.isPending),
    isError: queryEnabled && (candidates.isError || index.isError),
    error: candidates.error ?? index.error,
    refetch: candidates.isError ? candidates.refetch : index.refetch,
  };
}
