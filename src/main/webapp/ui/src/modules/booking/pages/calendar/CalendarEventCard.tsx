import { DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { cn } from "@/modules/common/utils/cn";
import { BookingActions } from "./BookingEventActions";
import { toTimelineEvent } from "./calendarLayoutUtils";

export function CalendarEventCard({
  event,
  date,
  timezone,
  compact = false,
  overlay = false,
}: {
  event: BookingListDocument;
  date: string;
  timezone: string;
  compact?: boolean;
  overlay?: boolean;
}) {
  const card = (
    <DayTimelineEventCard
      event={toTimelineEvent(event, date, timezone)}
      date={date}
      timezone={timezone}
      compactCards={compact}
      variant={overlay ? "timeline" : "flow"}
      renderEventActions={() => <BookingActions event={event} timezone={timezone} />}
      renderBlockoutActions={() => <BookingActions event={event} timezone={timezone} />}
    />
  );
  return overlay ? <div className={cn("relative", compact ? "min-h-18" : "min-h-14")}>{card}</div> : card;
}
