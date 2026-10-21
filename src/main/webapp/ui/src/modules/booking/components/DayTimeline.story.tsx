import { DayTimeline } from "./DayTimeline";
import { dayTimelineEvents } from "./DayTimeline.fixtures";

const OUTSIDE_LABEL = "Outside timeline";
const VIEW_DETAILS_LABEL = "View details";
const EDIT_LABEL = "Edit";

export function DayTimelineStory({ width = 480 }: { width?: number }) {
  return (
    <main className="min-h-screen bg-background p-4 text-foreground">
      {/* Inputs receive pointer focus consistently across the three test browsers. */}
      <input
        type="text"
        aria-label={OUTSIDE_LABEL}
        placeholder={OUTSIDE_LABEL}
        className="mb-3 rounded-sm border px-3 py-2"
      />
      <div style={{ width, maxWidth: "100%" }}>
        <DayTimeline
          date="2026-08-17"
          timezone="Europe/Berlin"
          events={dayTimelineEvents}
          startWindow={7 * 60}
          endWindow={19 * 60}
          showZoomControls={false}
          itemName="Test instruments"
          renderEventActions={(event) => (
            <div
              className={`grid border-t ${event.privacy === "full" && event.canEdit ? "grid-cols-2 divide-x" : "grid-cols-1"}`}
            >
              <a
                href={`/booking/bookable-items/${event.privacy === "full" ? event.item.globalId : "IN126"}`}
                className="py-2 text-center"
              >
                {VIEW_DETAILS_LABEL}
              </a>
              {event.privacy === "full" && event.canEdit ? (
                <a href={`/booking/calendar/bookings/${event.id}`} className="py-2 text-center">
                  {EDIT_LABEL}
                </a>
              ) : null}
            </div>
          )}
        />
      </div>
    </main>
  );
}
