import * as React from "react";
import { ALL_ISO_WEEKDAYS } from "@/modules/booking/domain/bookingOpeningHours";
import { type BookingWindowDraft, instantToDayMinute } from "@/modules/booking/domain/bookingTime";
import type { DayTimelineEvent } from "./DayTimelineEvent";
import { VerticalDayTimeline } from "./VerticalDayTimeline";

const DATE = "2026-10-25";
const TIMEZONE = "Europe/Berlin";

const event = (
  id: string,
  start: string,
  end: string,
  details: Omit<Extract<DayTimelineEvent, { kind: "booking"; privacy: "full" }>, "id" | "startMinute" | "endMinute">,
): DayTimelineEvent => ({
  ...details,
  id,
  startMinute: instantToDayMinute(start, DATE, TIMEZONE),
  endMinute: instantToDayMinute(end, DATE, TIMEZONE),
});

export const VERTICAL_DAY_TIMELINE_EVENTS: readonly DayTimelineEvent[] = [
  event("booking-ada", "2026-10-25T08:00:00+01:00", "2026-10-25T09:30:00+01:00", {
    kind: "booking",
    privacy: "full",
    title: "Confocal microscope",
    bookedBy: "Ada Lovelace",
    item: { name: "Confocal microscope", globalId: "IN123", location: { name: "Imaging lab", globalId: "IC123" } },
    canEdit: true,
    notes: "Cell imaging with the 63x oil objective.",
  }),
  event("booking-grace", "2026-10-25T08:30:00+01:00", "2026-10-25T09:30:00+01:00", {
    kind: "booking",
    privacy: "full",
    title: "Confocal microscope",
    bookedBy: "Grace Hopper",
    item: { name: "Confocal microscope", globalId: "IN123" },
    canEdit: false,
    notes: "Cryo-grid screening.",
  }),
  {
    id: "busy-short",
    kind: "booking",
    privacy: "busy",
    startMinute: instantToDayMinute("2026-10-25T12:00:00+01:00", DATE, TIMEZONE),
    endMinute: instantToDayMinute("2026-10-25T12:05:00+01:00", DATE, TIMEZONE),
  },
  event("short-full", "2026-10-25T14:00:00+01:00", "2026-10-25T14:05:00+01:00", {
    kind: "booking",
    privacy: "full",
    title: "Flow cytometer",
    bookedBy: "Katherine Johnson",
    item: { name: "Flow cytometer", globalId: "IN124" },
    canEdit: false,
    notes: "Calibration run.",
  }),
  {
    id: "maintenance",
    kind: "blockout",
    title: "Scheduled maintenance",
    item: { name: "Confocal microscope", globalId: "IN123" },
    createdBy: "Facilities",
    notes: "Laser alignment and inspection.",
    startMinute: instantToDayMinute("2026-10-25T13:00:00+01:00", DATE, TIMEZONE),
    endMinute: instantToDayMinute("2026-10-25T14:00:00+01:00", DATE, TIMEZONE),
  },
];

export function VerticalDayTimelineStory({
  initialDraft = {
    startDate: DATE,
    startTime: "02:00",
    startOccurrence: "later" as const,
    endDate: DATE,
    endTime: "03:00",
  },
}: {
  initialDraft?: BookingWindowDraft;
} = {}) {
  const [date, setDate] = React.useState(DATE);
  const [draft, setDraft] = React.useState<BookingWindowDraft>(initialDraft);

  return (
    <VerticalDayTimeline
      date={date}
      onDateChange={setDate}
      timezone={TIMEZONE}
      itemName="Confocal microscope"
      events={VERTICAL_DAY_TIMELINE_EVENTS}
      schedule={{ status: "success" }}
      scheduleWindow={{
        timezone: TIMEZONE,
        openingStart: "08:00",
        openingEnd: "18:00",
        openDays: ALL_ISO_WEEKDAYS,
        openingExceptions: [],
      }}
      editing={{
        draft,
        schedulingTimeZone: TIMEZONE,
        slotGranularityMinutes: 5,
        targetKey: "IN123",
        onChange: setDraft,
      }}
    />
  );
}
