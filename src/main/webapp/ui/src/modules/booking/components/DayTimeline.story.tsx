import { DayTimeline, type DayTimelineEvent } from "./DayTimeline";

export const LONG_ITEM_NAME =
  "Confocal microscope with an intentionally long descriptive inventory name for truncation coverage";
const OUTSIDE_LABEL = "Outside timeline";
const VIEW_DETAILS_LABEL = "View details";
const EDIT_LABEL = "Edit";

const events: ReadonlyArray<DayTimelineEvent> = [
  {
    id: "editable",
    kind: "booking",
    privacy: "full",
    title: `${LONG_ITEM_NAME} · Ada Lovelace`,
    bookedBy: "Ada Lovelace",
    item: {
      name: LONG_ITEM_NAME,
      globalId: "IN123",
      location: { name: "Imaging lab", globalId: "IC123" },
    },
    canEdit: true,
    notes: "Cell imaging with the 63x oil objective.",
    startMinute: 9 * 60 + 30,
    endMinute: 10 * 60 + 30,
  },
  {
    id: "readonly",
    kind: "booking",
    privacy: "full",
    title: "Electron microscope · Grace Hopper",
    bookedBy: "Grace Hopper",
    item: { name: "Electron microscope", globalId: "IN124" },
    canEdit: false,
    notes: "Read-only booking purpose.",
    startMinute: 9 * 60 + 45,
    endMinute: 10 * 60 + 45,
  },
  {
    id: "blockout",
    kind: "blockout",
    title: "Scheduled maintenance",
    item: { name: "Flow cytometer", globalId: "IN125" },
    notes: "Laser alignment and inspection.",
    startMinute: 11 * 60,
    endMinute: 12 * 60,
  },
  {
    id: "busy",
    kind: "booking",
    privacy: "busy",
    startMinute: 12 * 60,
    endMinute: 13 * 60,
  },
];

export function DayTimelineStory({ width = 480 }: { width?: number }) {
  return (
    <main className="min-h-screen bg-background p-4 text-foreground">
      <button type="button" className="mb-3 rounded-sm border px-3 py-2">
        {OUTSIDE_LABEL}
      </button>
      <div style={{ width, maxWidth: "100%" }}>
        <DayTimeline
          date="2026-08-17"
          timezone="Europe/Berlin"
          events={events}
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
