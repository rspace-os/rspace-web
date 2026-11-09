import type { DayTimelineEvent } from "./DayTimeline";

export const LONG_ITEM_NAME =
  "Confocal microscope with an intentionally long descriptive inventory name for truncation coverage";

export const dayTimelineEvents: ReadonlyArray<DayTimelineEvent> = [
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
    title: "Electron microscope",
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
