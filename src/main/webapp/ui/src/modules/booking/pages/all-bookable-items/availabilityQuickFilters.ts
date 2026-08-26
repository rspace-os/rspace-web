import { useQuery } from "@tanstack/react-query";
import * as v from "valibot";
import {
  type AvailabilityInterval,
  type CurrentDayAvailability,
  classifyCurrentDayAvailability,
} from "@/modules/booking/domain/availability";
import { currentWallClock, type ZonedDayBounds, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { useAlignedMinute } from "@/modules/booking/hooks/useAlignedMinute";
import { viewTransitionQueryMeta } from "@/modules/common/queries/viewTransition";
import { type BookingConfiguration, BookingConfigurationSchema } from "../bookable-items/bookingConfiguration";
import { loadDatedCalendarAvailability } from "../calendar/calendarAvailability";

export type AvailabilityQuickFilter = "available-now" | "free-later-today";

export type AvailabilityQuickIndexEntry = {
  date: string;
  bounds: ZonedDayBounds;
  intervals: readonly AvailabilityInterval[];
  category: CurrentDayAvailability;
};

const CandidatePageSchema = v.object({
  docs: v.array(BookingConfigurationSchema),
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
    where: "enabled==true;target.deleted==false",
    page: String(page),
    limit: "100",
    depth: "1",
    "fields[booking-configurations]":
      "id,target,enabled,timezone,slotGranularityMinutes,openingStart,openingEnd,bufferBeforeMinutes,bufferAfterMinutes,maxBookingDurationMinutes,allowDoubleBooking",
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
): Promise<readonly BookingConfiguration[]> {
  const first = await fetchCandidatePage(1, token, signal);
  if (first.totalDocs > 1000) throw new AvailabilityCandidateLimitError();
  const candidates = [...first.docs];
  for (let page = 2; page <= first.totalPages; page += 1) {
    candidates.push(...(await fetchCandidatePage(page, token, signal)).docs);
  }
  return candidates.filter((candidate) => candidate.target !== null);
}

export async function loadAvailabilityQuickIndex(
  candidates: readonly BookingConfiguration[],
  current: Date,
  token: string,
  signal: AbortSignal,
): Promise<ReadonlyMap<string, AvailabilityQuickIndexEntry>> {
  const datedRows = candidates.flatMap((candidate) =>
    candidate.target
      ? [
          {
            globalId: candidate.target.globalId,
            timezone: candidate.timezone,
            date: currentWallClock(current.toISOString(), candidate.timezone).date,
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
  const availability = await loadDatedCalendarAvailability(datedRows, token, signal);
  const index = new Map<string, AvailabilityQuickIndexEntry>();
  for (const row of datedRows) {
    const bounds = zonedDayBounds(row.date, row.timezone);
    const intervals = availability.get(row.globalId) ?? [];
    index.set(row.globalId, {
      date: row.date,
      bounds,
      intervals,
      category: classifyCurrentDayAvailability(intervals, new Date(bounds.start), new Date(bounds.end), current),
    });
  }
  return index;
}

export function useAvailabilityQuickFilterIndex(
  mode: AvailabilityQuickFilter | undefined,
  token: string,
  clock: () => Date = now,
) {
  const enabled = mode !== undefined && token.length > 0;
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
    queryKey: ["api-v2", "bookings", "availability-quick-index", signature, minute],
    queryFn: ({ signal }) => loadAvailabilityQuickIndex(candidates.data ?? [], new Date(minute), token, signal),
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
