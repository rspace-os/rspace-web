import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import { schedulingSettingsEntries } from "@/modules/booking/configuration/schedulingSettings";
import {
  type AvailabilityInterval,
  type CurrentDayAvailability,
  classifyCurrentDayAvailability,
} from "@/modules/booking/domain/availability";
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

const AvailabilityCandidateSchema = v.object({
  target: v.nullable(v.object({ globalId: v.string() })),
  timezone: v.string(),
  ...schedulingSettingsEntries,
});

type AvailabilityCandidate = v.InferOutput<typeof AvailabilityCandidateSchema>;

const CandidatePageSchema = v.object({
  docs: v.array(AvailabilityCandidateSchema),
  totalDocs: v.number(),
  totalPages: v.number(),
});

export class AvailabilityCandidateLimitError extends Error {
  constructor() {
    super("Availability quick filters support at most 1,000 bookable items");
    this.name = "AvailabilityCandidateLimitError";
  }
}

const now = () => new Date();
async function fetchCandidatePage(page: number, token: string, signal: AbortSignal) {
  const parameters = new URLSearchParams({
    where: "enabled==true;state==ACTIVE;target.deleted==false",
    page: String(page),
    limit: "100",
    depth: "1",
    "fields[booking-configurations]":
      "target,timezone,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking",
  });
  const response = await fetch(`/api/v2/booking-configurations?${parameters}`, {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
    signal,
  });
  if (!response.ok) throw new Error(`Bookable item request failed (${response.status})`);
  return v.parse(CandidatePageSchema, await response.json());
}

export async function fetchAvailabilityCandidates(
  token: string,
  signal: AbortSignal,
): Promise<readonly AvailabilityCandidate[]> {
  const first = await fetchCandidatePage(1, token, signal);
  if (first.totalDocs > 1000) throw new AvailabilityCandidateLimitError();
  const candidates = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    candidates.push(...(await fetchCandidatePage(page, token, signal)).docs);
  }
  return candidates.filter((candidate) => candidate.target !== null);
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
) {
  const enabled = token.length > 0;
  const minute = useAlignedMinute(clock);
  const candidates = useQuery({
    queryKey: ["api-v2", "booking-configurations", "availability-candidates"],
    queryFn: ({ signal }) => fetchAvailabilityCandidates(token, signal),
    enabled,
    staleTime: 60_000,
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
    enabled: enabled && candidates.isSuccess,
    meta: viewTransitionQueryMeta,
  });
  return {
    data: enabled ? index.data : undefined,
    now: new Date(minute),
    isPending: enabled && (candidates.isPending || index.isPending),
    isError: enabled && (candidates.isError || index.isError),
    error: candidates.error ?? index.error,
    refetch: candidates.isError ? candidates.refetch : index.refetch,
  };
}
