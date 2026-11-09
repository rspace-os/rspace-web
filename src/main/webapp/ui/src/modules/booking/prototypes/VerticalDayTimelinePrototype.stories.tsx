// PROTOTYPE ONLY. A vertical alternative to the horizontal DayTimeline.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import * as React from "react";
import { type DayTimelineEvent, DayTimelineEventCard } from "@/modules/booking/components/DayTimeline";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { cn } from "@/modules/common/utils/cn";

import { positionPrototypeEvents } from "./verticalTimelinePrototypeLanes";

const DATE = "2026-08-17";
const TIMEZONE = "Europe/Berlin";
const DAY_START = 7 * 60;
const DAY_END = 19 * 60;
const SNAP = 15;

type Range = { start: number; end: number };
type Interaction = { mode: "move" | "start" | "end"; pointerOffset: number; range: Range; pointerId: number };

const events: readonly DayTimelineEvent[] = [
  {
    id: "41",
    kind: "booking",
    privacy: "full",
    title: "Confocal microscope",
    bookedBy: "Ada Lovelace",
    item: { name: "Confocal microscope", globalId: "IN123", location: { name: "Imaging lab", globalId: "IC123" } },
    canEdit: true,
    notes: "Cell imaging",
    startMinute: 8 * 60 + 30,
    endMinute: 10 * 60,
  },
  {
    id: "42",
    kind: "booking",
    privacy: "full",
    title: "Confocal microscope",
    bookedBy: "Grace Hopper",
    item: { name: "Confocal microscope", globalId: "IN123" },
    canEdit: false,
    notes: "Cryo-grid screening",
    startMinute: 13 * 60,
    endMinute: 14 * 60 + 30,
  },
  {
    id: "43",
    kind: "blockout",
    title: "Scheduled maintenance",
    item: { name: "Confocal microscope", globalId: "IN123" },
    createdBy: "Facilities",
    notes: "Lens alignment",
    startMinute: 11 * 60,
    endMinute: 12 * 60,
  },
  {
    id: "44",
    kind: "booking",
    privacy: "busy",
    startMinute: 16 * 60,
    endMinute: 17 * 60 + 30,
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

function time(minutes: number) {
  const rounded = Math.round(minutes);
  return `${String(Math.floor(rounded / 60)).padStart(2, "0")}:${String(rounded % 60).padStart(2, "0")}`;
}

function percent(minutes: number) {
  return `${((minutes - DAY_START) / (DAY_END - DAY_START)) * 100}%`;
}

function draftRange(draft: Range) {
  return `${time(draft.start)}–${time(draft.end)}`;
}

function VerticalDraft({ range, onChange }: { range: Range; onChange: (next: Range) => void }) {
  const timelineRef = React.useRef<HTMLDivElement>(null);
  const interaction = React.useRef<Interaction | null>(null);
  const [preview, setPreview] = React.useState<Range>();
  const visible = preview ?? range;

  const minuteAt = (clientY: number, snap = true) => {
    const bounds = timelineRef.current?.getBoundingClientRect();
    if (!bounds) return visible.start;
    const value = DAY_START + ((clientY - bounds.top) / bounds.height) * (DAY_END - DAY_START);
    return Math.min(DAY_END, Math.max(DAY_START, snap ? Math.round(value / SNAP) * SNAP : value));
  };
  const moveRange = (value: number, active: Range): Range => {
    const duration = active.end - active.start;
    const start = Math.min(DAY_END - duration, Math.max(DAY_START, value));
    return { start, end: start + duration };
  };
  const adjust = (active: Range, mode: Interaction["mode"], value: number): Range => {
    if (mode === "move") return active;
    return mode === "start"
      ? { start: Math.min(active.end - SNAP, Math.max(DAY_START, value)), end: active.end }
      : { start: active.start, end: Math.max(active.start + SNAP, Math.min(DAY_END, value)) };
  };
  const begin = (mode: Interaction["mode"], event: React.PointerEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    event.currentTarget.setPointerCapture(event.pointerId);
    const bounds = timelineRef.current?.getBoundingClientRect();
    const blockTop = bounds
      ? bounds.top + ((visible.start - DAY_START) / (DAY_END - DAY_START)) * bounds.height
      : event.clientY;
    interaction.current = { mode, pointerOffset: event.clientY - blockTop, range: visible, pointerId: event.pointerId };
    setPreview(visible);
  };
  const move = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = interaction.current;
    if (!active || active.pointerId !== event.pointerId) return;
    const value =
      active.mode === "move" ? minuteAt(event.clientY - active.pointerOffset, false) : minuteAt(event.clientY);
    setPreview(active.mode === "move" ? moveRange(value, active.range) : adjust(active.range, active.mode, value));
  };
  const finish = (event: React.PointerEvent<HTMLButtonElement>) => {
    const active = interaction.current;
    if (!active || active.pointerId !== event.pointerId) return;
    interaction.current = null;
    setPreview(undefined);
    const value = active.mode === "move" ? minuteAt(event.clientY - active.pointerOffset) : minuteAt(event.clientY);
    onChange(active.mode === "move" ? moveRange(value, active.range) : adjust(active.range, active.mode, value));
  };
  const cancel = () => {
    interaction.current = null;
    setPreview(undefined);
  };
  const keyboard = (mode: Interaction["mode"], event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
    event.preventDefault();
    const delta = event.key === "ArrowUp" ? -SNAP : SNAP;
    const value =
      mode === "move" ? visible.start + delta : mode === "start" ? visible.start + delta : visible.end + delta;
    if (mode === "move")
      onChange({
        start: Math.min(DAY_END - (visible.end - visible.start), Math.max(DAY_START, value)),
        end: Math.min(DAY_END, value + visible.end - visible.start),
      });
    else onChange(adjust(visible, mode, value));
  };

  return (
    <div ref={timelineRef} className="pointer-events-none absolute inset-0 z-30">
      <div
        className={cn(
          "pointer-events-auto absolute right-2 left-16 z-20 rounded-md border-2 text-xs font-semibold shadow-lg",
          "border-primary bg-[color-mix(in_srgb,var(--primary)_25%,var(--background))] text-foreground ring-3 ring-ring/40",
        )}
        style={{
          top: percent(visible.start),
          height: `${((visible.end - visible.start) / (DAY_END - DAY_START)) * 100}%`,
        }}
      >
        <button
          type="button"
          aria-label="Move draft booking"
          className="absolute inset-x-1 inset-y-3 z-10 flex cursor-grab touch-none items-start gap-1 text-left"
          onPointerDown={(event) => begin("move", event)}
          onPointerMove={move}
          onPointerUp={finish}
          onPointerCancel={cancel}
          onLostPointerCapture={cancel}
          onKeyDown={(event) => keyboard("move", event)}
        >
          <span className="rounded-sm bg-primary px-1 text-[10px] leading-tight text-primary-foreground">
            <span className="block">Draft</span>
            <span className="block whitespace-nowrap">{draftRange(visible)}</span>
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

function VerticalDayTimelinePrototype() {
  const [draft, setDraft] = React.useState<Range>({ start: 9 * 60 + 30, end: 10 * 60 + 30 });
  const overlappingEvents = events.filter((event) => draft.start < event.endMinute && draft.end > event.startMinute);
  const conflict = overlappingEvents.length > 0;
  return (
    <main className="min-h-screen bg-muted/20 p-4 text-foreground sm:p-8">
      <div className="mx-auto max-w-2xl">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-primary">Prototype · vertical calendar</p>
        <div className="mb-5 flex items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold">Calendar day view</h1>
            <p className="mt-1 text-sm text-muted-foreground">A vertical alternative to the horizontal DayTimeline.</p>
          </div>
          <Badge variant={conflict ? "destructive" : "secondary"}>{conflict ? "Conflict" : "Available"}</Badge>
        </div>
        <Card className="overflow-hidden">
          <CardHeader className="border-b py-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <CardTitle className="text-base">Monday, 17 August 2026</CardTitle>
                <p className="text-xs text-muted-foreground">{TIMEZONE} · Confocal microscope</p>
              </div>
              <div className="flex gap-1">
                <Button size="icon-sm" variant="ghost" aria-label="Previous day">
                  <ChevronLeftIcon />
                </Button>
                <Button size="icon-sm" variant="ghost" aria-label="Next day">
                  <ChevronRightIcon />
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardContent className="p-3">
            <div className="mb-3 flex flex-wrap gap-3 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                <i className="size-2 rounded-full bg-primary" /> Draft
              </span>
              <span className="inline-flex items-center gap-1.5">
                <i className="size-2 rounded-full bg-blue-600" /> Booking
              </span>
              <span className="inline-flex items-center gap-1.5">
                <i className="size-2 rounded-full bg-amber-600" /> Maintenance
              </span>
            </div>
            <section
              aria-label="Vertical booking day timeline"
              className="relative overflow-hidden rounded-sm border bg-card"
            >
              <div className="relative isolate h-[42rem]" data-testid="vertical-day-timeline-canvas">
                <div className="pointer-events-none absolute inset-0 z-0" aria-hidden="true">
                  {Array.from({ length: 13 }, (_, index) => {
                    const minute = DAY_START + index * 60;
                    return (
                      <div
                        key={minute}
                        className="absolute inset-x-0 border-t border-border/70"
                        style={{ top: percent(minute) }}
                      >
                        <span className="absolute top-0 left-2 -translate-y-1/2 bg-card px-1 text-[11px] text-muted-foreground">
                          {time(minute)}
                        </span>
                      </div>
                    );
                  })}
                </div>
                <ol className="absolute inset-y-0 right-2 left-16 z-10 m-0 list-none p-0">
                  {positionPrototypeEvents(events).map(({ event, lane, lanes }) => (
                    <li
                      key={event.id}
                      data-event-id={event.id}
                      className="absolute [&>article]:max-w-full"
                      style={{
                        left: `${(lane / lanes) * 100}%`,
                        width: `calc(${100 / lanes}% - 4px)`,
                        top: percent(event.startMinute),
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
                <VerticalDraft range={draft} onChange={setDraft} />
              </div>
            </section>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Vertical Day Timeline",
  component: VerticalDayTimelinePrototype,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof VerticalDayTimelinePrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
