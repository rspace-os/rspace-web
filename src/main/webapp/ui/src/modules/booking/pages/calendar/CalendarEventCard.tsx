import { DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { cn } from "@/modules/common/utils/cn";
import { BookingActions } from "./BookingEventActions";

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
  return (
    <div
      data-calendar-event-focus={`${date}:${event.id}`}
      className={cn(
        "relative data-[calendar-event-focus-highlight=true]:ring-4 data-[calendar-event-focus-highlight=true]:ring-ring data-[calendar-event-focus-highlight=true]:ring-offset-2",
        overlay && (compact ? "min-h-18" : "min-h-14"),
      )}
    >
      {card}
    </div>
  );
}
