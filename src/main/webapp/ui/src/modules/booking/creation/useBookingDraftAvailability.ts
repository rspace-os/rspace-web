import { Temporal } from "@js-temporal/polyfill";
import { useMemo } from "react";
import type { BookingFormState } from "@/modules/booking/creation/BookingForm";
import { bookingConflicts } from "@/modules/booking/domain/availability";
import type { BookingEventKind } from "@/modules/booking/domain/booking";
import { ALWAYS_OPEN } from "@/modules/booking/domain/bookingOpeningHours";
import {
  calendarAvailabilityRow,
  useCalendarAvailability,
} from "@/modules/booking/pages/calendar/calendarAvailability";

function sameInstant(left: string, right: string): boolean {
  try {
    return Temporal.Instant.compare(left, right) === 0;
  } catch {
    return false;
  }
}

export function useBookingDraftAvailability({
  formState,
  displayTimezone,
  token,
  eventKind,
  originalWindow,
  excludedBookingId,
}: {
  formState: BookingFormState | undefined;
  displayTimezone: string;
  token: string;
  eventKind: BookingEventKind;
  originalWindow?: { start: string; end: string };
  excludedBookingId?: number;
}) {
  const target = formState?.target;
  // Checked as soon as both endpoints are entered, before the item's rules are met.
  const window = formState?.enteredWindow;
  const intervalChanged = Boolean(
    window &&
      (!originalWindow ||
        !sameInstant(window.start, originalWindow.start) ||
        !sameInstant(window.end, originalWindow.end)),
  );
  const row = useMemo(
    () =>
      target
        ? calendarAvailabilityRow({
            ...target,
            // Opening hours are the form's own check, so closed time must not read as an overlap here.
            ...ALWAYS_OPEN,
            // Fetch overlaps even when the target permits double booking so the form can explain them.
            allowDoubleBooking: false,
          })
        : undefined,
    [target],
  );
  const interval = window
    ? {
        ...window,
        date: formState?.draft.startDate ?? "",
        timeZone: target?.timezone ?? displayTimezone,
        elapsedMinutes: (Date.parse(window.end) - Date.parse(window.start)) / 60_000,
      }
    : { start: "", end: "", date: "", timeZone: displayTimezone, elapsedMinutes: 0 };
  const availability = useCalendarAvailability(row && intervalChanged ? [row] : [], interval, token);
  const checking = Boolean(row && intervalChanged && availability.isPending);
  const intervals = availability.isSuccess ? (availability.data.get(target?.globalId ?? "") ?? []) : [];
  const violation = Boolean(
    intervalChanged &&
      intervals.some(({ source }) => source.id !== (excludedBookingId ? `booking:${excludedBookingId}` : "")),
  );
  const conflicts = violation ? bookingConflicts(intervals, displayTimezone, excludedBookingId) : [];
  const maintenanceConflict = conflicts.some(({ kind }) => kind === "MAINTENANCE");
  const canOverlapBookings = eventKind === "BOOKING" && target?.allowDoubleBooking === true;

  return {
    conflicts,
    violation,
    checking,
    failed: Boolean(row && intervalChanged && availability.isError),
    blocksSubmission: violation && (!canOverlapBookings || maintenanceConflict || conflicts.length === 0),
    conflictSeverity:
      canOverlapBookings && !maintenanceConflict && conflicts.length > 0 ? ("warning" as const) : ("error" as const),
  };
}
