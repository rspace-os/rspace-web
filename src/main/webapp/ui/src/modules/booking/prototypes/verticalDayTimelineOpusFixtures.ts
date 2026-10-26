// PROTOTYPE ONLY (Opus 5.5). Throwaway shared code for the vertical day timeline stories; do not import from production.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { type BookingWindowDraft, wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";

export type FixtureEvent = { id: number; kind: "BOOKING" | "MAINTENANCE"; start: string; end: string } & (
  | { privacy: "busy" }
  | { privacy: "full"; bookedBy: string; purpose?: string }
);

export type Scenario = {
  label: string;
  date: string;
  displayTimezone: string;
  item: BookableItemOption;
  events: readonly FixtureEvent[];
  draftStart: string;
  draftEnd: string;
  initialWindow: BookingWindowDraft;
};

function instrument(overrides: Partial<BookableItemOption>): BookableItemOption {
  return {
    configurationId: 9001,
    targetId: 123,
    globalId: "IN123",
    name: "Confocal microscope",
    timezone: "Europe/Berlin",
    slotGranularityMinutes: 15,
    incrementUnit: "MINUTES",
    alignToIncrement: true,
    openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "07:00", end: "19:00" })),

    bufferBeforeMinutes: 0,
    bufferAfterMinutes: 0,
    maxBookingDurationMinutes: 240,
    allowDoubleBooking: false,
    ...overrides,
  };
}

function scenario(value: Omit<Scenario, "initialWindow">): Scenario {
  return {
    ...value,
    initialWindow: wallClockDraftFromInstants(value.draftStart, value.draftEnd, value.displayTimezone),
  };
}

const full = (id: number, start: string, end: string, bookedBy: string, purpose?: string): FixtureEvent => ({
  id,
  kind: "BOOKING",
  privacy: "full",
  start,
  end,
  bookedBy,
  purpose,
});
const busy = (id: number, start: string, end: string): FixtureEvent => ({
  id,
  kind: "BOOKING",
  privacy: "busy",
  start,
  end,
});
const maintenance = (id: number, start: string, end: string, purpose: string): FixtureEvent => ({
  id,
  kind: "MAINTENANCE",
  privacy: "full",
  start,
  end,
  bookedBy: "Facilities",
  purpose,
});

export const SCENARIOS = {
  standard: scenario({
    label: "Ordinary day (Berlin, 15 min slots, 15 min buffer after)",
    date: "2026-11-16",
    displayTimezone: "Europe/Berlin",
    item: instrument({ bufferAfterMinutes: 15 }),
    events: [
      full(101, "2026-11-16T07:00:00Z", "2026-11-16T08:15:00Z", "Ada Lovelace", "Cell imaging"),
      maintenance(102, "2026-11-16T10:00:00Z", "2026-11-16T11:00:00Z", "Lens alignment"),
      full(103, "2026-11-16T12:00:00Z", "2026-11-16T13:30:00Z", "Grace Hopper", "Cryo-grid screening"),
      busy(104, "2026-11-16T12:30:00Z", "2026-11-16T13:00:00Z"),
      busy(105, "2026-11-16T15:00:00Z", "2026-11-16T16:30:00Z"),
    ],
    draftStart: "2026-11-16T08:30:00Z",
    draftEnd: "2026-11-16T09:30:00Z",
  }),
  dense: scenario({
    label: "Dense and 5-minute bookings (double booking allowed)",
    date: "2026-11-17",
    displayTimezone: "Europe/Berlin",
    item: instrument({ slotGranularityMinutes: 5, allowDoubleBooking: true }),
    events: [
      full(201, "2026-11-17T08:00:00Z", "2026-11-17T08:05:00Z", "Ada Lovelace", "Calibration"),
      busy(202, "2026-11-17T08:05:00Z", "2026-11-17T08:10:00Z"),
      full(203, "2026-11-17T09:00:00Z", "2026-11-17T10:00:00Z", "Grace Hopper", "Screening"),
      busy(204, "2026-11-17T09:10:00Z", "2026-11-17T09:40:00Z"),
      full(205, "2026-11-17T09:20:00Z", "2026-11-17T10:30:00Z", "Katherine Johnson", "Tomography"),
      busy(206, "2026-11-17T09:30:00Z", "2026-11-17T09:50:00Z"),
      maintenance(207, "2026-11-17T11:00:00Z", "2026-11-17T11:05:00Z", "Laser check"),
      full(208, "2026-11-17T13:00:00Z", "2026-11-17T14:00:00Z", "Ada Lovelace", "Live imaging"),
    ],
    draftStart: "2026-11-17T09:15:00Z",
    draftEnd: "2026-11-17T09:45:00Z",
  }),
  springForward: scenario({
    label: "Spring forward: 23-hour day (Berlin, 28 Mar 2027)",
    date: "2027-03-28",
    displayTimezone: "Europe/Berlin",
    item: instrument({
      slotGranularityMinutes: 30,
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

      maxBookingDurationMinutes: 0,
    }),
    events: [
      full(301, "2027-03-27T23:30:00Z", "2027-03-28T00:30:00Z", "Ada Lovelace", "Night acquisition"),
      busy(302, "2027-03-28T00:30:00Z", "2027-03-28T01:30:00Z"),
      maintenance(303, "2027-03-28T04:00:00Z", "2027-03-28T05:00:00Z", "Chiller service"),
    ],
    draftStart: "2027-03-28T01:30:00Z",
    draftEnd: "2027-03-28T02:30:00Z",
  }),
  fallBack: scenario({
    label: "Fall back: 25-hour day, repeated 02:00 (Berlin, 25 Oct 2026)",
    date: "2026-10-25",
    displayTimezone: "Europe/Berlin",
    item: instrument({
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),
      maxBookingDurationMinutes: 0,
    }),
    events: [
      full(401, "2026-10-25T00:15:00Z", "2026-10-25T00:45:00Z", "Grace Hopper", "First pass"),
      busy(402, "2026-10-25T01:15:00Z", "2026-10-25T01:45:00Z"),
      full(403, "2026-10-25T04:00:00Z", "2026-10-25T05:00:00Z", "Ada Lovelace", "Second pass"),
    ],
    draftStart: "2026-10-25T01:45:00Z",
    draftEnd: "2026-10-25T02:30:00Z",
  }),
  overnight: scenario({
    label: "Overnight and cross-day (Berlin)",
    date: "2026-11-19",
    displayTimezone: "Europe/Berlin",
    item: instrument({
      slotGranularityMinutes: 30,
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" })),

      maxBookingDurationMinutes: 0,
    }),
    events: [
      full(501, "2026-11-18T20:00:00Z", "2026-11-18T23:00:00Z", "Grace Hopper", "Ends at next-day midnight"),
      maintenance(502, "2026-11-18T22:00:00Z", "2026-11-19T02:00:00Z", "Overnight chiller service"),
      full(503, "2026-11-19T07:00:00Z", "2026-11-19T11:00:00Z", "Ada Lovelace", "Long acquisition"),
      busy(504, "2026-11-20T03:00:00Z", "2026-11-20T05:00:00Z"),
    ],
    draftStart: "2026-11-19T19:00:00Z",
    draftEnd: "2026-11-20T01:00:00Z",
  }),
  displayDiffers: scenario({
    label: "Display Asia/Kolkata (+05:30), instrument Berlin, hourly slots",
    date: "2026-11-16",
    displayTimezone: "Asia/Kolkata",
    item: instrument({
      slotGranularityMinutes: 60,
      openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "08:00", end: "18:00" })),
    }),
    events: [
      full(601, "2026-11-16T11:00:00Z", "2026-11-16T12:00:00Z", "Grace Hopper", "Live imaging"),
      busy(602, "2026-11-16T13:00:00Z", "2026-11-16T15:00:00Z"),
    ],
    draftStart: "2026-11-16T08:00:00Z",
    draftEnd: "2026-11-16T10:00:00Z",
  }),
} satisfies Record<string, Scenario>;

export type ScenarioId = keyof typeof SCENARIOS;
export const SCENARIO_IDS = Object.keys(SCENARIOS) as ScenarioId[];

export const ORIGINAL_BOOKING_ID = 41;
const ORIGINAL_PURPOSE = "My imaging session";

/** The edited booking as the day query would return it, so exclusion is exercised rather than assumed. */
export function originalFixtureFor(value: Scenario): FixtureEvent {
  return full(ORIGINAL_BOOKING_ID, value.draftStart, value.draftEnd, "You", ORIGINAL_PURPOSE);
}

export function editableBookingFor(value: Scenario): EditableBooking {
  return {
    id: ORIGINAL_BOOKING_ID,
    version: 0,
    target: {
      relationTo: "booking-instruments",
      globalId: value.item.globalId,
      value: { id: value.item.targetId, name: value.item.name, deleted: false },
    },
    timezone: value.item.timezone,
    start: value.draftStart,
    end: value.draftEnd,
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy: "full",
    cancellationReason: null,
    purpose: ORIGINAL_PURPOSE,
    bookedBy: "You",
    createdBy: "You",
    canEdit: true,
    canCancel: true,
    createdAt: "2026-08-01T09:00:00Z",
    updatedAt: "2026-08-01T09:00:00Z",
  };
}

export type DayQueryFixture = "ready" | "loading" | "empty" | "error" | "truncated";

export const SCENARIO_OPTIONS = SCENARIO_IDS.map((id) => ({ value: id, label: SCENARIOS[id].label }));
export const QUERY_OPTIONS: readonly { value: DayQueryFixture; label: string }[] = [
  { value: "ready", label: "Loaded" },
  { value: "loading", label: "Loading" },
  { value: "empty", label: "Empty" },
  { value: "error", label: "Failed" },
  { value: "truncated", label: "Over 1,000 results" },
];
export const HOUR_HEIGHT_OPTIONS: readonly { value: number; label: string }[] = [
  { value: 48, label: "48 px/hour" },
  { value: 72, label: "72 px/hour" },
  { value: 120, label: "120 px/hour" },
];
