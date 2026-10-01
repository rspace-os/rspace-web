// PROTOTYPE ONLY. Explore a production booking form beside a calendar day view.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { AlertTriangleIcon, InfoIcon } from "lucide-react";
import * as React from "react";
import { type DayTimelineEvent, DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import {
  BookingForm,
  type BookingFormState,
  type BookingFormSubmission,
  type EditableBooking,
} from "@/modules/booking/creation/BookingForm";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { type BookingWindowDraft, wallClockDraftFromInstants } from "@/modules/booking/domain/bookingTime";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Heading } from "@/modules/common/ui/typography";

import { positionPrototypeEvents } from "./verticalTimelinePrototypeLanes";

const DATE = "2026-08-17";
const TIMEZONE = "Europe/Berlin";
const DAY_START = 7 * 60;
const DAY_END = 19 * 60;
const SNAP = 15;
const INITIAL_WINDOW: BookingWindowDraft = {
  startDate: DATE,
  startTime: "09:30",
  endDate: DATE,
  endTime: "10:30",
};

const item: BookableItemOption = {
  configurationId: 9001,
  targetId: 123,
  globalId: "IN123",
  name: "Confocal microscope",
  timezone: TIMEZONE,
  slotGranularityMinutes: 15,
  openingStart: "07:00",
  openingEnd: "19:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [],
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 240,
  allowDoubleBooking: false,
};

const existingEvents: readonly DayTimelineEvent[] = [
  {
    id: "booking-1",
    kind: "booking",
    privacy: "full",
    title: "Confocal microscope",
    bookedBy: "Ada Lovelace",
    item: { name: item.name, globalId: item.globalId },
    canEdit: false,
    notes: "Cell imaging",
    startMinute: 8 * 60 + 30,
    endMinute: 10 * 60,
  },
  {
    id: "maintenance",
    kind: "blockout",
    title: "Scheduled maintenance",
    item: { name: item.name, globalId: item.globalId },
    createdBy: "Facilities",
    notes: "Lens alignment",
    startMinute: 11 * 60,
    endMinute: 12 * 60,
  },
  {
    id: "booking-2",
    kind: "booking",
    privacy: "full",
    title: "Confocal microscope",
    bookedBy: "Grace Hopper",
    item: { name: item.name, globalId: item.globalId },
    canEdit: false,
    notes: "Cryo-grid screening",
    startMinute: 13 * 60,
    endMinute: 14 * 60 + 30,
  },
  {
    id: "overlap-1",
    kind: "booking",
    privacy: "full",
    title: "Confocal microscope",
    bookedBy: "Katherine Johnson",
    item: { name: "Confocal microscope", globalId: "IN123" },
    canEdit: false,
    notes: "Overlapping imaging session",
    startMinute: 9 * 60,
    endMinute: 10 * 60 + 30,
  },
  {
    id: "overlap-2",
    kind: "booking",
    privacy: "busy",
    startMinute: 10 * 60,
    endMinute: 11 * 60,
  },
];

const editBooking: EditableBooking = {
  id: 41,
  version: 0,
  target: {
    relationTo: "booking-instruments",
    globalId: item.globalId,
    value: { id: item.targetId, name: item.name, deleted: false },
  },
  timezone: TIMEZONE,
  start: "2026-08-17T07:30:00Z",
  end: "2026-08-17T08:30:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  cancellationReason: null,
  purpose: "Cell imaging",
  bookedBy: "You",
  createdBy: "You",
  canEdit: true,
  canCancel: true,
  createdAt: "2026-08-01T09:00:00Z",
  updatedAt: "2026-08-01T09:00:00Z",
};

function minutesFromDraft(draft: BookingWindowDraft) {
  if (draft.startDate !== DATE || draft.endDate !== DATE) return undefined;
  const start = /^([01]\d|2[0-3]):[0-5]\d$/.test(draft.startTime)
    ? Number(draft.startTime.slice(0, 2)) * 60 + Number(draft.startTime.slice(3))
    : undefined;
  const end = /^([01]\d|2[0-3]):[0-5]\d$/.test(draft.endTime)
    ? Number(draft.endTime.slice(0, 2)) * 60 + Number(draft.endTime.slice(3))
    : undefined;
  return start !== undefined && end !== undefined && end > start ? { start, end } : undefined;
}

function overlaps(left: { start: number; end: number }, event: DayTimelineEvent) {
  return left.start < event.endMinute && left.end > event.startMinute;
}

function time(minutes: number) {
  const rounded = Math.round(minutes);
  return `${String(Math.floor(rounded / 60)).padStart(2, "0")}:${String(rounded % 60).padStart(2, "0")}`;
}

function eventLabel(event: DayTimelineEvent) {
  if (event.kind === "blockout") return event.title;
  if (event.privacy === "full") return `${event.title} · ${event.bookedBy}`;
  return "Existing booking";
}

function draftFromRange(range: { start: number; end: number }): BookingWindowDraft {
  return { startDate: DATE, startTime: time(range.start), endDate: DATE, endTime: time(range.end) };
}

function VerticalDraftEditor({
  draft,
  onChange,
}: {
  draft: BookingWindowDraft;
  onChange: (next: BookingWindowDraft) => void;
}) {
  const timelineRef = React.useRef<HTMLDivElement>(null);
  const interaction = React.useRef<{
    mode: "move" | "start" | "end";
    pointerOffset: number;
    range: { start: number; end: number };
    pointerId: number;
  } | null>(null);
  const [preview, setPreview] = React.useState<{ start: number; end: number }>();
  const range = minutesFromDraft(draft);
  const visibleRange = preview ?? range;
  if (!visibleRange) return null;

  const minuteAt = (clientY: number, snap = true) => {
    const bounds = timelineRef.current?.getBoundingClientRect();
    if (!bounds) return visibleRange.start;
    const value = DAY_START + ((clientY - bounds.top) / bounds.height) * (DAY_END - DAY_START);
    return Math.min(DAY_END, Math.max(DAY_START, snap ? Math.round(value / SNAP) * SNAP : value));
  };
  const moveRange = (value: number, active: { start: number; end: number }) => {
    const duration = active.end - active.start;
    const start = Math.min(DAY_END - duration, Math.max(DAY_START, value));
    return { start, end: start + duration };
  };
  const adjust = (mode: "move" | "start" | "end", value: number, active: { start: number; end: number }) => {
    if (mode === "move") return active;
    return mode === "start"
      ? { start: Math.min(active.end - SNAP, Math.max(DAY_START, value)), end: active.end }
      : { start: active.start, end: Math.max(active.start + SNAP, Math.min(DAY_END, value)) };
  };
  const begin = (mode: "move" | "start" | "end", event: React.PointerEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    event.currentTarget.setPointerCapture(event.pointerId);
    const bounds = timelineRef.current?.getBoundingClientRect();
    const blockTop = bounds
      ? bounds.top + ((visibleRange.start - DAY_START) / (DAY_END - DAY_START)) * bounds.height
      : event.clientY;
    interaction.current = {
      mode,
      pointerOffset: event.clientY - blockTop,
      range: visibleRange,
      pointerId: event.pointerId,
    };
    setPreview(visibleRange);
  };
  const move = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = interaction.current;
    if (!active || active.pointerId !== event.pointerId) return;
    const value =
      active.mode === "move" ? minuteAt(event.clientY - active.pointerOffset, false) : minuteAt(event.clientY);
    setPreview(active.mode === "move" ? moveRange(value, active.range) : adjust(active.mode, value, active.range));
  };
  const finish = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = interaction.current;
    if (!active || active.pointerId !== event.pointerId) return;
    interaction.current = null;
    setPreview(undefined);
    const value = active.mode === "move" ? minuteAt(event.clientY - active.pointerOffset) : minuteAt(event.clientY);
    onChange(
      draftFromRange(
        active.mode === "move" ? moveRange(value, active.range) : adjust(active.mode, value, active.range),
      ),
    );
  };
  const cancel = () => {
    interaction.current = null;
    setPreview(undefined);
  };
  const keyboard = (mode: "move" | "start" | "end", event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
    event.preventDefault();
    const delta = event.key === "ArrowUp" ? -SNAP : SNAP;
    const value =
      mode === "move"
        ? visibleRange.start + delta
        : mode === "start"
          ? visibleRange.start + delta
          : visibleRange.end + delta;
    onChange(
      draftFromRange(
        mode === "move"
          ? {
              start: Math.min(DAY_END - (visibleRange.end - visibleRange.start), Math.max(DAY_START, value)),
              end: Math.min(DAY_END, value + visibleRange.end - visibleRange.start),
            }
          : adjust(mode, value, visibleRange),
      ),
    );
  };
  const top = ((visibleRange.start - DAY_START) / (DAY_END - DAY_START)) * 100;
  const height = ((visibleRange.end - visibleRange.start) / (DAY_END - DAY_START)) * 100;

  return (
    <div ref={timelineRef} className="pointer-events-none absolute inset-x-0 top-0 bottom-0 z-30">
      <div
        className="pointer-events-auto absolute right-2 left-16 z-20 rounded-md border-2 border-primary bg-[color-mix(in_srgb,var(--primary)_25%,var(--background))] text-foreground shadow-md ring-3 ring-ring/40"
        style={{ top: `${top}%`, height: `${height}%` }}
      >
        <button
          type="button"
          aria-label="Move draft booking"
          className="absolute inset-x-1 inset-y-3 z-10 flex cursor-grab touch-none items-start text-left text-xs font-semibold"
          onPointerDown={(event) => begin("move", event)}
          onPointerMove={move}
          onPointerUp={finish}
          onPointerCancel={cancel}
          onLostPointerCapture={cancel}
          onKeyDown={(event) => keyboard("move", event)}
        >
          <span className="rounded-sm bg-primary px-1 text-[10px] leading-tight text-primary-foreground">
            <span className="block">Draft</span>
            <span className="block whitespace-nowrap">
              {time(visibleRange.start)}–{time(visibleRange.end)}
            </span>
          </span>
        </button>
        <button
          type="button"
          aria-label="Adjust booking start"
          className="absolute inset-x-0 top-0 z-20 h-3 cursor-ns-resize touch-none rounded-t-md border-primary bg-background/80"
          onPointerDown={(event) => begin("start", event)}
          onPointerMove={move}
          onPointerUp={finish}
          onPointerCancel={cancel}
          onLostPointerCapture={cancel}
          onKeyDown={(event) => keyboard("start", event)}
        />
        <button
          type="button"
          aria-label="Adjust booking end"
          className="absolute inset-x-0 bottom-0 z-20 h-3 cursor-ns-resize touch-none rounded-b-md border-primary bg-background/80"
          onPointerDown={(event) => begin("end", event)}
          onPointerMove={move}
          onPointerUp={finish}
          onPointerCancel={cancel}
          onLostPointerCapture={cancel}
          onKeyDown={(event) => keyboard("end", event)}
        />
      </div>
    </div>
  );
}

function DayViewAside({
  draft,
  onChange,
}: {
  draft: BookingWindowDraft;
  onChange: (next: BookingWindowDraft) => void;
}) {
  const range = minutesFromDraft(draft);
  const overlappingEvents = range ? existingEvents.filter((event) => overlaps(range, event)) : [];
  const conflict = overlappingEvents.length > 0;

  return (
    <div className="min-w-0 space-y-3">
      <Card size="sm" className="gap-0 py-0">
        <CardHeader className="border-b py-4">
          <CardTitle className="text-base">Calendar preview</CardTitle>
          <p className="text-xs text-muted-foreground">Monday, 17 August 2026 · {TIMEZONE}</p>
        </CardHeader>
        <CardContent className="min-w-0 p-3">
          <section aria-label="Booking day view" className="relative overflow-hidden rounded-sm border bg-card">
            <header className="border-border border-b px-3 py-2 text-sm font-semibold">{item.name}</header>
            <div className="relative isolate h-[40rem]" data-testid="vertical-day-timeline">
              <div className="pointer-events-none absolute inset-0 z-0" aria-hidden="true">
                {Array.from({ length: 13 }, (_, index) => {
                  const minute = DAY_START + index * 60;
                  return (
                    <div
                      key={minute}
                      className="absolute inset-x-0 border-t border-border/70"
                      style={{ top: `${((minute - DAY_START) / (DAY_END - DAY_START)) * 100}%` }}
                    >
                      <span className="absolute top-0 left-2 -translate-y-1/2 bg-card px-1 text-[11px] text-muted-foreground">{`${String(Math.floor(minute / 60)).padStart(2, "0")}:00`}</span>
                    </div>
                  );
                })}
              </div>
              <ol className="absolute inset-y-0 right-2 left-16 z-10 m-0 list-none p-0">
                {positionPrototypeEvents(existingEvents).map(({ event, lane, lanes }) => (
                  <li
                    key={event.id}
                    data-event-id={event.id}
                    className="absolute [&>article]:max-w-full"
                    style={{
                      left: `${(lane / lanes) * 100}%`,
                      width: `calc(${100 / lanes}% - 4px)`,
                      top: `${((event.startMinute - DAY_START) / (DAY_END - DAY_START)) * 100}%`,
                      height: `${((event.endMinute - event.startMinute) / (DAY_END - DAY_START)) * 100}%`,
                    }}
                  >
                    <DayTimelineEventCard
                      event={event}
                      date={DATE}
                      timezone={TIMEZONE}
                      variant="timeline"
                      compactCards={false}
                    />
                  </li>
                ))}
              </ol>
              <VerticalDraftEditor draft={draft} onChange={onChange} />
            </div>
          </section>
        </CardContent>
      </Card>
      {conflict ? (
        <Alert variant="destructive">
          <AlertTriangleIcon />
          <AlertTitle>Booking conflict</AlertTitle>
          <AlertDescription>
            <p>The draft interval overlaps:</p>
            <ul className="mt-2 list-disc space-y-1 pl-4">
              {overlappingEvents.map((event) => (
                <li key={event.id}>
                  <span className="font-medium">{eventLabel(event)}</span> · {time(event.startMinute)}–
                  {time(event.endMinute)}
                </li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      ) : (
        <Alert>
          <InfoIcon />
          <AlertTitle>Available</AlertTitle>
          <AlertDescription>The draft interval is available.</AlertDescription>
        </Alert>
      )}
    </div>
  );
}

function BookingFormCalendarAsidePrototype({ mode }: { mode: "add" | "edit" }) {
  const [formState, setFormState] = React.useState<BookingFormState>();
  const [windowAdjustment, setWindowAdjustment] = React.useState<BookingWindowDraft>();
  const [saved, setSaved] = React.useState(false);
  const initialWindow =
    mode === "edit" ? wallClockDraftFromInstants(editBooking.start, editBooking.end, TIMEZONE) : INITIAL_WINDOW;
  const draft = formState?.draft ?? windowAdjustment ?? initialWindow;
  const range = minutesFromDraft(draft);
  const conflict = range ? existingEvents.some((event) => overlaps(range, event)) : false;

  const onStateChange = (next: BookingFormState) => {
    setFormState(next);
    setSaved(false);
  };
  const submit = async (_submission: BookingFormSubmission) => {
    setSaved(true);
  };
  const commonFormProps = {
    density: "comfortable" as const,
    displayTimezone: TIMEZONE,
    token: "prototype",
    pending: false,
    error: conflict ? "This period overlaps another booking or a maintenance event." : undefined,
    submissionBlocked: conflict,
    windowAdjustment,
    showRulesSummary: false,
    onStateChange,
    onSubmit: submit,
  };

  return (
    <main className="min-h-screen space-y-6 bg-background p-4 text-foreground sm:p-8">
      <div className="mx-auto max-w-6xl">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-primary">Prototype · {mode} booking</p>
        <div className="mb-6 flex items-end justify-between gap-4">
          <div>
            <Heading level={3} as="h1">
              {mode === "add" ? "Add booking" : "Edit booking"}
            </Heading>
            <p className="mt-1 text-sm text-muted-foreground">
              Adjust the time in the form or directly on the day view.
            </p>
          </div>
          <Badge variant={conflict ? "destructive" : "secondary"}>{conflict ? "Conflict" : "Available"}</Badge>
        </div>
        <div className="@container">
          <div className="grid gap-6 @2xl:grid-cols-[minmax(0,1fr)_30rem]">
            <div className="min-w-0">
              {mode === "add" ? (
                <BookingForm
                  {...commonFormProps}
                  mode="add"
                  eventKind="BOOKING"
                  initialTarget={item}
                  initialDate={DATE}
                  initialWindow={INITIAL_WINDOW}
                  lockTarget={false}
                />
              ) : (
                <BookingForm {...commonFormProps} mode="edit" booking={editBooking} configuration={item} />
              )}
              {saved ? (
                <p className="mt-4 text-sm text-emerald-700" role="status">
                  Changes saved in this prototype.
                </p>
              ) : null}
            </div>
            <div className="hidden @2xl:block">
              <DayViewAside
                draft={draft}
                onChange={(next) => {
                  setSaved(false);
                  setWindowAdjustment(next);
                }}
              />
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking Form Calendar Aside",
  component: BookingFormCalendarAsidePrototype,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof BookingFormCalendarAsidePrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Add: Story = { args: { mode: "add" } };
export const Edit: Story = { args: { mode: "edit" } };
