import { createPortal } from "react-dom";
import { instantToDayMinute, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { type BookingFormState } from "./BookingForm";
import { type BookingCreationContext } from "./bookingCreationStore";
import { resolveBookingWindow } from "./ZonedBookingWindowFields";

export function DraftMarker({
  creation,
  draft,
  timeZone,
}: {
  creation: BookingCreationContext;
  draft: BookingFormState["draft"];
  timeZone: string;
}) {
  const trigger = document.getElementById(creation.triggerId);
  const canvas =
    trigger?.querySelector<HTMLElement>('[data-testid="day-timeline-canvas"]') ??
    trigger?.closest("section")?.querySelector<HTMLElement>('[data-testid="day-timeline-canvas"]');
  const originDate = creation.initialDate;
  if (!canvas || !originDate || !draft.startDate || !draft.startTime || !draft.endDate || !draft.endTime) return null;
  const { window } = resolveBookingWindow(draft, timeZone);
  if (!window) return null;
  const dayMinutes = zonedDayBounds(originDate, timeZone).elapsedMinutes;
  const startMinute = instantToDayMinute(window.start, originDate, timeZone);
  const endMinute = instantToDayMinute(window.end, originDate, timeZone);
  if (endMinute <= 0 || startMinute >= dayMinutes) return null;
  const visibleStart = Math.max(0, startMinute);
  const visibleEnd = Math.min(dayMinutes, endMinute);
  return createPortal(
    <div
      aria-hidden="true"
      data-testid="compact-booking-draft-marker"
      className={
        creation.eventKind === "MAINTENANCE"
          ? "pointer-events-none absolute top-8 bottom-0 z-40 rounded-sm border-2 border-amber-600 bg-amber-200/60 ring-3 ring-ring/40"
          : "pointer-events-none absolute top-8 bottom-0 z-40 rounded-sm border-2 border-primary bg-primary/25 ring-3 ring-ring/40"
      }
      style={{
        left: `${(visibleStart / dayMinutes) * 100}%`,
        width: `${((visibleEnd - visibleStart) / dayMinutes) * 100}%`,
      }}
    />,
    canvas,
  );
}
