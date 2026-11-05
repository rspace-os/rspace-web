import { DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { cn } from "@/modules/common/utils/cn";
import { BookingActions } from "./BookingEventActions";
import { CALENDAR_EVENT_FOCUS_HIGHLIGHT_CLASS } from "./calendarEventFocus";

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
  const renderActions = () => <BookingActions event={event} timezone={timezone} />;
  const card = (
    <DayTimelineEventCard
      event={toTimelineEvent(event, date, timezone)}
      date={date}
      timezone={timezone}
      compactCards={compact}
      variant={overlay ? "timeline" : "flow"}
      renderEventActions={renderActions}
      renderBlockoutActions={renderActions}
    />
  );
  return (
    <div
      data-calendar-event-focus={`${date}:${event.id}`}
      className={cn("relative", CALENDAR_EVENT_FOCUS_HIGHLIGHT_CLASS, overlay && (compact ? "min-h-18" : "min-h-14"))}
    >
      {card}
    </div>
  );
}
