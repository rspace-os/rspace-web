// PROTOTYPE — the inline "add booking" popover, opened from the calendar rather than from the full
// /booking/calendar/bookings/add page.
//
// Entry points (all three open the same popover):
//   1. "New booking" in the shell toolbar — no slot pre-filled, anchors to the button.
//   2. "Add" beside each DayTimeline row — that resource pre-filled, next free hour proposed.
//   3. Click-drag across a timeline — resource, start and end all pre-filled.
//
// Earlier rounds compared three dialog layouts (A stacked sheet, B duration chips, C slot preview).
// C won and A and B are gone; what is left is C iterated on its distinguishing question, which is
// how an add-booking surface should show that the proposed period is already taken.
//
// It follows plan 002's decided design for the expanded calendar card, so the two surfaces behave
// alike: a non-modal Base UI popover anchored to the thing it describes, positioned by the shared
// PopoverContent wrapper, and persistent — outside press and focus-out are cancelled, so an
// add-booking popover and an expanded event card can sit open and overlapping while their times
// are compared. Closing is the X, Cancel, or Escape.
//
// The form itself is RenderFields over a formisch store, the same path BookingForm takes, at the
// new `density="compact"`. It is wider than plan 002's 22rem read-only card: a form needs its
// Start and End controls to pair, and RowField only pairs them once its container clears 24rem.
// The overlap panel is a `ui` field: an arbitrary component in the field list that subscribes to
// the live values and renders the vertical day preview plus the conflict list.
//
// Nothing is persisted and no mutation runs; submitting appends to in-memory story state.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Form, getInput, useField, useForm } from "@formisch/react";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CalendarPlusIcon, CheckIcon, PencilIcon, PlusIcon, TriangleAlertIcon, XIcon } from "lucide-react";
import * as React from "react";
import { createPortal } from "react-dom";
import { expect, fireEvent, userEvent, within } from "storybook/test";
import * as v from "valibot";
import { DayTimeline, type DayTimelineEvent } from "@/modules/booking/components/DayTimeline";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { RenderFields } from "@/modules/common/collection-form/RenderFields";
import type { FormFieldConfig, UiFieldProps } from "@/modules/common/collection-form/RenderFields.types";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { ActionBar } from "@/modules/common/ui/action-bar";
import { Button } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { Popover, PopoverClose, PopoverContent, PopoverTrigger } from "@/modules/common/ui/popover";
import { cn } from "@/modules/common/utils/cn";

const DAY_MINUTES = 24 * 60;
const SNAP_MINUTES = 15;
const WINDOW = { start: 7 * 60, end: 19 * 60 };
const DATE = "2026-08-17";
const TIMEZONE = "Europe/Berlin";

type Resource = { id: string; name: string; location: string };
type BookingType = "booking" | "blockout";
type Draft = { resourceId: string; startMinute: number; endMinute: number };
type Created = Draft & { type: BookingType; purpose: string };

const RESOURCES: readonly Resource[] = [
  { id: "IN123", name: "Confocal microscope", location: "Imaging suite 1.02" },
  { id: "IN124", name: "Electron microscope", location: "Imaging suite 1.06" },
  { id: "IN125", name: "Flow cytometer", location: "Cell biology 0.14" },
];

/** The identity DayTimeline's expanded card needs, derived from the row a seed belongs to. */
function itemOf(resourceId: string) {
  const resource = RESOURCES.find((option) => option.id === resourceId);
  return {
    name: resource?.name ?? "Unknown item",
    globalId: resourceId,
    location: resource ? { name: resource.location, globalId: `${resourceId}-LOC` } : undefined,
  };
}

const SEED: readonly (Draft & { event: DayTimelineEvent })[] = [
  {
    resourceId: "IN123",
    startMinute: 8 * 60,
    endMinute: 10 * 60,
    event: {
      id: "41",
      kind: "booking",
      privacy: "full",
      title: "Confocal microscope · Ada Lovelace",
      bookedBy: "Ada Lovelace",
      item: itemOf("IN123"),
      canEdit: true,
      notes: "Cell imaging",
      startMinute: 8 * 60,
      endMinute: 10 * 60,
    },
  },
  {
    resourceId: "IN124",
    startMinute: 12 * 60,
    endMinute: 13 * 60 + 30,
    event: { id: "42", kind: "booking", privacy: "busy", startMinute: 12 * 60, endMinute: 13 * 60 + 30 },
  },
  {
    resourceId: "IN125",
    startMinute: 13 * 60,
    endMinute: 15 * 60,
    event: {
      id: "44",
      kind: "blockout",
      title: "Laser realignment",
      item: itemOf("IN125"),
      notes: "Vendor on site",
      startMinute: 13 * 60,
      endMinute: 15 * 60,
    },
  },
];

function clock(minute: number): string {
  const wrapped = Math.max(0, Math.min(DAY_MINUTES, Math.round(minute)));
  return `${String(Math.floor(wrapped / 60)).padStart(2, "0")}:${String(wrapped % 60).padStart(2, "0")}`;
}

function minuteOf(time: string): number {
  const [hours, minutes] = time.split(":").map(Number);
  return Number.isFinite(hours) && Number.isFinite(minutes) ? hours * 60 + minutes : 0;
}

function snap(minute: number): number {
  return Math.max(0, Math.min(DAY_MINUTES, Math.round(minute / SNAP_MINUTES) * SNAP_MINUTES));
}

function durationLabel(draft: Draft): string {
  const total = draft.endMinute - draft.startMinute;
  const hours = Math.floor(total / 60);
  const minutes = total % 60;
  return [hours && `${hours}h`, minutes && `${minutes}m`].filter(Boolean).join(" ") || "0m";
}

function summarise(draft: Draft, resource: Resource | undefined): string {
  return `${resource?.name ?? "No item"} · ${clock(draft.startMinute)}–${clock(draft.endMinute)} · ${durationLabel(draft)}`;
}

/**
 * Every event the draft runs into, not merely whether it runs into one.
 *
 * `excludeId` is what makes editing work: an edit opens on the very minutes of the event being
 * edited, which is still on the timeline, so without this every edit would open accusing itself of
 * a clash. The event stays in the row either way, because seeing the original underneath the draft
 * is the point of editing in place.
 */
function conflictsWith(
  draft: Draft,
  events: readonly DayTimelineEvent[],
  excludeId?: string,
): readonly DayTimelineEvent[] {
  return events.filter(
    (event) => event.id !== excludeId && event.startMinute < draft.endMinute && event.endMinute > draft.startMinute,
  );
}

/**
 * What a conflict may be called. Branches on the privacy variant rather than on whether some
 * nullable text happens to be present: a busy response must not leak a booker.
 */
function describeConflict(event: DayTimelineEvent): string {
  if (event.kind === "blockout") return event.title;
  return event.privacy === "busy" ? "Busy" : event.bookedBy;
}

/** The minutes a draft and an event actually share, which is the part worth drawing. */
function intersection(draft: Draft, event: DayTimelineEvent) {
  return {
    from: Math.max(draft.startMinute, event.startMinute),
    to: Math.min(draft.endMinute, event.endMinute),
  };
}

/**
 * Builds an event from a submitted entry. Shared by creating and editing so a saved edit rebuilds
 * `title` and `item` too, which matters when the edit moves the booking to a different row.
 */
function eventFrom(id: string, entry: Created, bookedBy: string): DayTimelineEvent {
  return entry.type === "blockout"
    ? {
        id,
        kind: "blockout",
        title: "Maintenance blockout",
        item: itemOf(entry.resourceId),
        notes: entry.purpose || undefined,
        startMinute: entry.startMinute,
        endMinute: entry.endMinute,
      }
    : {
        id,
        kind: "booking",
        privacy: "full",
        title: `${itemOf(entry.resourceId).name} · ${bookedBy}`,
        bookedBy,
        item: itemOf(entry.resourceId),
        canEdit: true,
        notes: entry.purpose || undefined,
        startMinute: entry.startMinute,
        endMinute: entry.endMinute,
      };
}

/** Next hour that does not collide, so the row "Add" button opens with a usable proposal. */
function nextFreeHour(events: readonly DayTimelineEvent[]): { startMinute: number; endMinute: number } {
  for (let start = WINDOW.start; start + 60 <= WINDOW.end; start += SNAP_MINUTES) {
    const candidate = { startMinute: start, endMinute: start + 60 };
    if (!events.some((event) => event.startMinute < candidate.endMinute && event.endMinute > candidate.startMinute)) {
      return candidate;
    }
  }
  return { startMinute: WINDOW.start, endMinute: WINDOW.start + 60 };
}

/**
 * PROTOTYPE HACK: reaches into DayTimeline's rendered canvas to add drag-to-create without changing
 * the production component. A real implementation would take an `onRangeSelect` prop instead.
 */
function DraggableTimeline({
  resource,
  events,
  hoverEnabled,
  onCanvas,
  renderEventActions,
  onSelect,
}: {
  resource: Resource;
  events: readonly DayTimelineEvent[];
  /** Off while a draft is open, so the hover card does not compete with the popover. */
  hoverEnabled: boolean;
  /** Lets the shell anchor its popover to a slot on this row's canvas. */
  onCanvas: (canvas: HTMLElement | null) => void;
  /** DayTimeline's caller-owned action seam, which is where the inline Edit lives. */
  renderEventActions: (event: Extract<DayTimelineEvent, { kind: "booking" }>, period: string) => React.ReactNode;
  onSelect: (range: { startMinute: number; endMinute: number }) => void;
}) {
  const hostRef = React.useRef<HTMLDivElement>(null);
  const [canvas, setCanvas] = React.useState<HTMLElement | null>(null);
  const [band, setBand] = React.useState<{ from: number; to: number } | null>(null);
  const [hover, setHover] = React.useState<{ minute: number; x: number; y: number } | null>(null);

  React.useEffect(() => {
    const found = hostRef.current?.querySelector<HTMLElement>('[data-testid="day-timeline-canvas"]') ?? null;
    setCanvas(found);
    onCanvas(found);
  }, [onCanvas]);

  /** Same guard for hover and press: existing event cards and the row's own buttons are not canvas. */
  const isFreeCanvas = (target: EventTarget | null) =>
    canvas?.contains(target as Node) === true && !(target as HTMLElement).closest("article, button");

  const minuteAt = (clientX: number) => {
    const box = canvas?.getBoundingClientRect();
    return box ? snap(((clientX - box.left) / box.width) * DAY_MINUTES) : 0;
  };

  const onPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!isFreeCanvas(event.target)) return;
    const from = minuteAt(event.clientX);
    event.currentTarget.setPointerCapture(event.pointerId);
    setHover(null);
    setBand({ from, to: from + SNAP_MINUTES });
  };

  const onPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (band) {
      setBand({ from: band.from, to: minuteAt(event.clientX) });
      return;
    }
    // The band labels its own range, so one floating indicator at a time.
    if (!hoverEnabled) return;
    setHover(
      isFreeCanvas(event.target) ? { minute: minuteAt(event.clientX), x: event.clientX, y: event.clientY } : null,
    );
  };

  const onPointerUp = () => {
    if (!band) return;
    const startMinute = Math.min(band.from, band.to);
    const endMinute = Math.max(band.from, band.to);
    setBand(null);
    if (endMinute - startMinute >= SNAP_MINUTES) onSelect({ startMinute, endMinute });
  };

  // What a click right here would create, so the hover card can promise exactly that.
  const hoverSlot: Draft | null = hover
    ? { resourceId: resource.id, startMinute: hover.minute, endMinute: hover.minute + SNAP_MINUTES }
    : null;
  const hoverConflicts = hoverSlot ? conflictsWith(hoverSlot, events) : [];

  return (
    <div
      ref={hostRef}
      // select-none: without it a drag text-selects the hour labels and smears them along with the
      // pointer. They sit in a pointer-events-none grid, which does not stop selection.
      className="min-w-0 cursor-crosshair select-none touch-none"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerLeave={() => setHover(null)}
      onPointerCancel={() => {
        setBand(null);
        setHover(null);
      }}
    >
      <DayTimeline
        date={DATE}
        timezone={TIMEZONE}
        events={events}
        startWindow={WINDOW.start}
        endWindow={WINDOW.end}
        showZoomControls={false}
        variant="table-row"
        itemName={resource.name}
        renderEventActions={renderEventActions}
      />
      {band &&
        canvas &&
        createPortal(
          <div
            className="pointer-events-none absolute inset-y-0 z-50 rounded-sm border-2 border-primary bg-primary/20"
            style={{
              left: `${(Math.min(band.from, band.to) / DAY_MINUTES) * 100}%`,
              width: `${(Math.abs(band.to - band.from) / DAY_MINUTES) * 100}%`,
            }}
          >
            <span className="absolute top-1 left-1 rounded-sm bg-primary px-1 text-[10px] font-semibold text-primary-foreground">
              {clock(Math.min(band.from, band.to))}–{clock(Math.max(band.from, band.to))}
            </span>
          </div>,
          canvas,
        )}
      {hover &&
        hoverSlot &&
        createPortal(
          // Portalled to the body, not the canvas: the canvas sits in an overflow-x-auto scroller,
          // which clips anything taller than the row. Decorative — keyboard users get the row's
          // "Add booking for …" button instead.
          <div
            aria-hidden="true"
            className="pointer-events-none fixed z-50 w-56 rounded-sm border bg-popover p-2 text-popover-foreground shadow-md"
            style={{ left: Math.min(hover.x + 14, window.innerWidth - 240), top: hover.y + 14 }}
          >
            <p className="truncate text-xs font-semibold">{resource.name}</p>
            <p className="font-mono text-sm">
              {clock(hoverSlot.startMinute)}–{clock(hoverSlot.endMinute)}
              <span className="ml-1 text-muted-foreground">· {durationLabel(hoverSlot)}</span>
            </p>
            {hoverConflicts.length > 0 ? (
              <p className="mt-1 flex items-center gap-1 text-[11px] text-destructive">
                <TriangleAlertIcon className="size-3" aria-hidden="true" />
                {describeConflict(hoverConflicts[0])} is here
              </p>
            ) : (
              <p className="mt-1 text-[11px] text-muted-foreground">Click to book, or drag to set the duration.</p>
            )}
          </div>,
          document.body,
        )}
    </div>
  );
}

/**
 * The draft drawn where it will land, doubling as the popover's anchor.
 *
 * Borrows the expanded calendar card's ring treatment so a slot being created reads as the same
 * "opened" state as an expanded event, and shades the minutes it actually shares with each
 * conflicting event, so a clash is visible on the day and not only stated in the form.
 */
function DraftBand({
  draft,
  type,
  conflicts,
}: {
  draft: Draft;
  type: BookingType;
  conflicts: readonly DayTimelineEvent[];
}) {
  const span = Math.max(SNAP_MINUTES, draft.endMinute - draft.startMinute);
  return (
    <PopoverTrigger
      aria-label={`Draft booking ${clock(draft.startMinute)} to ${clock(draft.endMinute)}`}
      // Base UI's default button element, not a rendered span: it sets aria-expanded on the
      // trigger, which axe rejects on a roleless element.
      className={cn(
        "pointer-events-none absolute inset-y-0 z-40 block rounded-sm border-2 ring-3 ring-ring/40",
        type === "blockout" ? "border-amber-600 bg-amber-100/70" : "border-blue-700 bg-blue-600/30",
        conflicts.length > 0 && "border-destructive",
      )}
      style={{
        left: `${(draft.startMinute / DAY_MINUTES) * 100}%`,
        width: `${(span / DAY_MINUTES) * 100}%`,
      }}
    >
      {conflicts.map((event) => {
        const { from, to } = intersection(draft, event);
        return (
          <span
            key={event.id}
            aria-hidden="true"
            className="absolute inset-y-0 bg-destructive/45"
            style={{
              left: `${((from - draft.startMinute) / span) * 100}%`,
              width: `${((to - from) / span) * 100}%`,
            }}
          />
        );
      })}
      <span className="absolute top-1 left-1 rounded-sm bg-primary px-1 text-[10px] font-semibold text-primary-foreground">
        {clock(draft.startMinute)}–{clock(draft.endMinute)}
      </span>
    </PopoverTrigger>
  );
}

type DraftDocument = { resourceId: string; start: string; end: string; type: string; purpose: string };

const DRAFT_SCHEMA = v.object({
  resourceId: v.string(),
  start: v.string(),
  end: v.string(),
  type: v.string(),
  purpose: v.pipe(v.string(), v.maxLength(1000)),
});

/** Reads the values being edited straight off the store, so every field sees the same draft. */
function draftOf(form: Parameters<typeof getInput>[0]): Draft & { type: BookingType } {
  const input = getInput(form) as Partial<DraftDocument>;
  return {
    resourceId: input.resourceId ?? "",
    startMinute: minuteOf(input.start ?? "09:00"),
    endMinute: minuteOf(input.end ?? "10:00"),
    type: input.type === "blockout" ? "blockout" : "booking",
  };
}

/**
 * The vertical day preview, restored and now driven by form state.
 *
 * This is the `ui` field added to RenderFields for this work: an arbitrary component in the
 * field list, handed the live store. It subscribes with `useField` so editing Start or End
 * re-renders it, which is what lets a preview panel react to the values without the surrounding
 * form knowing anything about overlaps.
 */
function overlapPanel(eventsFor: (resourceId: string) => readonly DayTimelineEvent[], excludeId?: string) {
  return function OverlapPanel({ form }: UiFieldProps) {
    // Subscribing to each edited path is what makes this component reactive; the values themselves
    // come from `draftOf` so the panel and the timeline band agree by construction.
    useField(form, { path: ["start"] });
    useField(form, { path: ["end"] });
    useField(form, { path: ["resourceId"] });
    useField(form, { path: ["type"] });

    const draft = draftOf(form);
    const events = draft.resourceId ? eventsFor(draft.resourceId) : [];
    const conflicts = conflictsWith(draft, events, excludeId);
    const span = WINDOW.end - WINDOW.start;
    const offset = (minute: number) =>
      ((Math.min(Math.max(minute, WINDOW.start), WINDOW.end) - WINDOW.start) / span) * 100;

    return (
      <section aria-label="Slot preview" className="flex gap-2">
        <div className="relative hidden h-36 w-16 shrink-0 overflow-hidden rounded-sm border bg-muted/20 lg:block">
          {events.map((event) => (
            <div
              key={event.id}
              className="absolute inset-x-1 rounded-xs border border-slate-400 bg-slate-200 px-0.5 text-[9px] text-slate-900"
              style={{
                top: `${offset(event.startMinute)}%`,
                height: `${Math.max(3, offset(event.endMinute) - offset(event.startMinute))}%`,
              }}
            >
              {clock(event.startMinute)}
            </div>
          ))}
          <div
            className={cn(
              "absolute inset-x-1 rounded-xs border-2",
              draft.type === "blockout" ? "border-amber-600 bg-amber-200/80" : "border-primary bg-primary/25",
              conflicts.length > 0 && "border-destructive",
            )}
            style={{
              top: `${offset(draft.startMinute)}%`,
              height: `${Math.max(3, offset(draft.endMinute) - offset(draft.startMinute))}%`,
            }}
          >
            {conflicts.map((event) => {
              const { from, to } = intersection(draft, event);
              return (
                <span
                  key={event.id}
                  aria-hidden="true"
                  className="absolute inset-x-0 bg-destructive/50"
                  style={{
                    top: `${((from - draft.startMinute) / Math.max(1, draft.endMinute - draft.startMinute)) * 100}%`,
                    height: `${((to - from) / Math.max(1, draft.endMinute - draft.startMinute)) * 100}%`,
                  }}
                />
              );
            })}
          </div>
        </div>
        {conflicts.length > 0 ? (
          // No tinted fill: destructive text on bg-destructive/10 measures 3.98:1 at this size,
          // under the 4.5 threshold the global Storybook a11y gate enforces.
          <div role="alert" className="min-w-0 flex-1 space-y-1 rounded-sm border-2 border-destructive/60 p-2">
            <p className="flex items-center gap-1.5 text-sm font-semibold text-destructive">
              <TriangleAlertIcon className="size-4 shrink-0" aria-hidden="true" />
              {conflicts.length === 1 ? "Overlaps 1 booking" : `Overlaps ${conflicts.length} bookings`}
            </p>
            <ul className="space-y-0.5">
              {conflicts.map((event) => {
                const { from, to } = intersection(draft, event);
                return (
                  <li key={event.id} className="flex items-baseline justify-between gap-2 text-xs text-foreground">
                    <span className="truncate">{describeConflict(event)}</span>
                    <span className="shrink-0 font-mono font-medium">
                      {clock(from)}–{clock(to)}
                    </span>
                  </li>
                );
              })}
            </ul>
          </div>
        ) : (
          <p className="flex-1 self-center text-xs text-muted-foreground">Nothing else is booked in this period.</p>
        )}
      </section>
    );
  };
}

/**
 * Prototype copy sits in `labelKey`, so i18next echoes the key back as the visible label rather
 * than routing throwaway strings through the catalog.
 */
const DRAFT_CONFIG = resolveCollectionConfig<DraftDocument>({
  slug: "booking-drafts",
  idField: "resourceId",
  labels: { singularKey: "Booking", pluralKey: "Bookings" },
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  fields: [
    {
      name: "resourceId",
      type: "select",
      labelKey: "Bookable item",
      options: RESOURCES.map((option) => ({ label: option.name, value: option.id })),
    },
    { name: "start", type: "text", labelKey: "Start", form: { widget: "time" } },
    { name: "end", type: "text", labelKey: "End", form: { widget: "time" } },
    {
      name: "type",
      type: "select",
      labelKey: "Booking type",
      options: [
        { label: "Booking", value: "booking" },
        { label: "Maintenance blockout", value: "blockout" },
      ],
    },
    { name: "purpose", type: "text", labelKey: "Purpose", form: { widget: "textarea" } },
  ],
});

const FIELD = Object.fromEntries(DRAFT_CONFIG.fields.map((field) => [field.name, field])) as Record<
  keyof DraftDocument,
  FormFieldConfig<DraftDocument>
>;

/**
 * The popover body. Owns the form store and reports the edited period back up, so the band drawn
 * on the timeline and the popover's own anchor follow whatever is currently typed.
 *
 * Remounted per draft by its key, which is how a new drag or Add press resets the fields.
 */
function AddBookingForm({
  seed,
  isSysAdmin,
  editingId,
  eventsFor,
  onDraftChange,
  onCancel,
  onSubmit,
}: {
  seed: Created;
  isSysAdmin: boolean;
  /** Set when editing an existing event, so it does not count as its own conflict. */
  editingId: string | null;
  eventsFor: (resourceId: string) => readonly DayTimelineEvent[];
  onDraftChange: (draft: Draft) => void;
  onCancel: () => void;
  onSubmit: (entry: Created) => void;
}) {
  const form = useForm({
    schema: DRAFT_SCHEMA,
    initialInput: {
      resourceId: seed.resourceId,
      start: clock(seed.startMinute),
      end: clock(seed.endMinute),
      type: seed.type,
      purpose: seed.purpose,
    },
  });

  // Stable across edits: a fresh component type each render would remount the panel on every
  // keystroke, and a fresh field list would do the same to every control.
  const fields = React.useMemo<readonly FormFieldConfig<DraftDocument>[]>(
    () => [
      FIELD.resourceId,
      { type: "row", fields: [FIELD.start, FIELD.end] as never },
      // A sysadmin-only field is decided outside the form, so it is added or omitted here rather
      // than hidden with a `condition`, which only sees the values being edited.
      ...(isSysAdmin ? [FIELD.type] : []),
      FIELD.purpose,
      { type: "ui", name: "overlaps", component: overlapPanel(eventsFor, editingId ?? undefined) },
    ],
    [isSysAdmin, eventsFor, editingId],
  );

  useField(form, { path: ["start"] });
  useField(form, { path: ["end"] });
  useField(form, { path: ["resourceId"] });
  const live = draftOf(form);

  React.useEffect(() => {
    onDraftChange({
      resourceId: live.resourceId,
      startMinute: live.startMinute,
      endMinute: live.endMinute,
    });
  }, [live.resourceId, live.startMinute, live.endMinute, onDraftChange]);

  const resource = RESOURCES.find((option) => option.id === live.resourceId);
  const conflicts = conflictsWith(live, live.resourceId ? eventsFor(live.resourceId) : [], editingId ?? undefined);

  return (
    <Form of={form} className="contents" onSubmit={() => undefined}>
      {/* The only part that scrolls. `min-h-0` is what lets a flex child shrink below its content
          height; without it the parent's max-height is ignored and the stack leaves the screen. */}
      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 py-3">
        <RenderFields fields={fields} form={form} density="compact" />
      </div>
      {/* More options leads this prototype's action row; the shared ActionBar preserves this order
          when no action is marked as the cancel role. */}
      <ActionBar
        actions={[
          { label: "More options" },
          {
            label: editingId ? "Save" : "Book",
            icon: CheckIcon,
            preferred: true,
            disabled: !resource || conflicts.length > 0 || live.endMinute <= live.startMinute,
            onClick: () => {
              const input = getInput(form) as DraftDocument;
              onSubmit({
                resourceId: live.resourceId,
                startMinute: live.startMinute,
                endMinute: live.endMinute,
                type: input.type === "blockout" ? "blockout" : "booking",
                purpose: input.purpose,
              });
            },
          },
          // Deliberately iconless, so the overflow menu shows its reserved icon slot doing its job.
          { label: "Cancel", onClick: onCancel },
        ]}
      />
    </Form>
  );
}

/** The host page. */
function DayShell() {
  const [isSysAdmin, setIsSysAdmin] = React.useState(true);
  const [created, setCreated] = React.useState<readonly Created[]>([]);
  // Saved edits, keyed by event id, laid over the seeds and anything created this session. Keeping
  // them separate is what lets a seeded event be edited without seeds becoming mutable state.
  const [edits, setEdits] = React.useState<Readonly<Record<string, Created & { bookedBy: string }>>>({});
  const [editingId, setEditingId] = React.useState<string | null>(null);
  const [seed, setSeed] = React.useState<Created | null>(null);
  const [draft, setDraft] = React.useState<Draft | null>(null);
  // Identifies one opening of the popover, so editing the fields never remounts the form.
  const [openedAt, setOpenedAt] = React.useState(0);
  const [type, setType] = React.useState<BookingType>("booking");
  const canvases = React.useRef<Record<string, HTMLElement | null>>({});
  const toolbarAnchor = React.useRef<HTMLDivElement>(null);

  const open = (next: Created, eventId: string | null) => {
    setType(next.type);
    setEditingId(eventId);
    setSeed(next);
    setOpenedAt((count) => count + 1);
    setDraft({ resourceId: next.resourceId, startMinute: next.startMinute, endMinute: next.endMinute });
  };

  const openCreate = (next: Draft) => open({ ...next, type: "booking", purpose: "" }, null);

  const openEdit = (event: Extract<DayTimelineEvent, { kind: "booking"; privacy: "full" }>, resourceId: string) =>
    open(
      {
        resourceId,
        startMinute: event.startMinute,
        endMinute: event.endMinute,
        type: "booking",
        purpose: event.notes ?? "",
      },
      event.id,
    );

  const close = () => {
    setDraft(null);
    setEditingId(null);
    setSeed(null);
  };

  // An edit may move an event to another row, so placement is resolved once over the whole day
  // rather than per row.
  const placed = React.useMemo(() => {
    const base = [
      ...SEED.map((entry) => ({ resourceId: entry.resourceId, event: entry.event })),
      ...created.map((entry, index) => ({
        resourceId: entry.resourceId,
        event: eventFrom(`new-${index}`, entry, "You"),
      })),
    ];
    return base.map((item) => {
      const edit = edits[item.event.id];
      return edit ? { resourceId: edit.resourceId, event: eventFrom(item.event.id, edit, edit.bookedBy) } : item;
    });
  }, [created, edits]);

  const eventsFor = React.useCallback(
    (resourceId: string): readonly DayTimelineEvent[] =>
      placed.filter((item) => item.resourceId === resourceId).map((item) => item.event),
    [placed],
  );

  /** One submit path, whether the entry is new or an edit of something already on the day. */
  const submit = (entry: Created) => {
    if (editingId) {
      const previous = placed.find((item) => item.event.id === editingId)?.event;
      const bookedBy =
        previous && previous.kind === "booking" && previous.privacy === "full" ? previous.bookedBy : "You";
      setEdits((current) => ({ ...current, [editingId]: { ...entry, bookedBy } }));
    } else {
      setCreated((current) => [...current, entry]);
    }
    close();
  };

  /**
   * The expanded card's action row, and, while that event is being edited, the edit form itself.
   *
   * Editing happens inside the card rather than in a second popover: the card is already open,
   * already anchored to the event, and already the thing being changed, so a dialog on top of it
   * was one surface too many. Its buttons then sit at the bottom of the card, which is the
   * placement the add popover uses.
   *
   * Edit is offered on full bookings the viewer may edit, matching BookingActions. A busy response
   * exposes nothing to edit, and plan 002 puts blockout actions out of scope, so neither gets one.
   */
  const eventActionsFor = (resourceId: string) => (event: Extract<DayTimelineEvent, { kind: "booking" }>) => {
    if (editingId === event.id && seed) {
      return (
        // The card's popup is plan 002's and sets no max-height, so the bound goes here instead:
        // the form scrolls within a fixed slice of the viewport rather than pushing the card off it.
        <div className="flex max-h-[min(22rem,45vh)] flex-col border-border border-t">
          <AddBookingForm
            key={openedAt}
            seed={seed}
            isSysAdmin={isSysAdmin}
            editingId={editingId}
            eventsFor={eventsFor}
            onDraftChange={setDraft}
            onCancel={close}
            onSubmit={submit}
          />
        </div>
      );
    }
    return event.privacy === "full" && event.canEdit ? (
      <ActionBar
        actions={[{ label: "Edit", icon: PencilIcon, preferred: true, onClick: () => openEdit(event, resourceId) }]}
      />
    ) : null;
  };

  const conflicts = draft?.resourceId ? conflictsWith(draft, eventsFor(draft.resourceId), editingId ?? undefined) : [];
  const canvas = draft?.resourceId ? (canvases.current[draft.resourceId] ?? null) : null;

  return (
    <main className="min-h-screen space-y-4 bg-background p-4 text-foreground sm:p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-xl font-semibold">Monday 17 August 2026</h1>
          <p className="text-sm text-muted-foreground">{TIMEZONE} · drag any timeline to propose a slot</p>
        </div>
        <div className="flex items-center gap-3">
          <Label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={isSysAdmin} onChange={(event) => setIsSysAdmin(event.target.checked)} />
            Sysadmin
          </Label>
          <div ref={toolbarAnchor} className="relative">
            <Button
              type="button"
              onClick={() => openCreate({ resourceId: "", startMinute: 9 * 60, endMinute: 10 * 60 })}
              data-testid="toolbar-add"
            >
              <CalendarPlusIcon aria-hidden="true" />
              New booking
            </Button>
          </div>
        </div>
      </header>

      <div className="divide-y overflow-hidden rounded-sm border bg-card">
        {RESOURCES.map((option) => {
          const events = eventsFor(option.id);
          return (
            <section key={option.id} className="grid grid-cols-[13rem_minmax(0,1fr)_auto] items-stretch">
              <header className="border-r bg-muted/30 p-2">
                <InventoryItem name={option.name} globalId={option.id} size="xs">
                  <InventoryLocationLink name={option.location} globalId={option.id} />
                </InventoryItem>
              </header>
              <DraggableTimeline
                resource={option}
                events={events}
                hoverEnabled={draft === null}
                onCanvas={(element) => {
                  canvases.current[option.id] = element;
                }}
                renderEventActions={eventActionsFor(option.id)}
                onSelect={(range) => openCreate({ resourceId: option.id, ...range })}
              />
              <div className="flex items-center border-l p-2">
                <Button
                  type="button"
                  size="icon-sm"
                  variant="outline"
                  aria-label={`Add booking for ${option.name}`}
                  onClick={() => openCreate({ resourceId: option.id, ...nextFreeHour(events) })}
                >
                  <PlusIcon />
                </Button>
              </div>
            </section>
          );
        })}
      </div>

      {draft && (
        <Popover
          // Mounted but closed while editing, so the draft band keeps rendering as its trigger and
          // the day still shows where the edit is landing.
          open={editingId === null}
          onOpenChange={(next, eventDetails) => {
            // Plan 002's persistent-disclosure rule: only the X, Cancel, or Escape close it, so this
            // popover and an expanded event card can stay open together while times are compared.
            if (!next && (eventDetails.reason === "outside-press" || eventDetails.reason === "focus-out")) {
              eventDetails.cancel();
              return;
            }
            if (!next) close();
          }}
          modal={false}
        >
          {createPortal(
            <DraftBand draft={draft} type={type} conflicts={conflicts} />,
            canvas ?? toolbarAnchor.current ?? document.body,
          )}
          <PopoverContent
            role="dialog"
            aria-label="New booking"
            align="start"
            side="bottom"
            sideOffset={8}
            collisionPadding={8}
            sticky
            // Plan 002 gives the expanded card the row's scroller as its collision boundary. This
            // form is several times taller than that ~130px strip, so the same boundary has nowhere
            // to put it below the slot and flips it out to the side, away from the thing it
            // describes. Plan 002 already allows the viewport fallback; an add surface needs it.
            // `--available-height` is what Base UI's positioner measured between the anchor and the
            // viewport edge. Bounding the popup by it, with only the field block scrolling, keeps the
            // header and the action stack on screen however long the form gets.
            className="max-h-[var(--available-height)] w-[min(30rem,var(--available-width))] max-w-none gap-0 overflow-hidden rounded-lg border border-primary p-0 ring-4 ring-ring/20"
          >
            <div className="flex shrink-0 items-start justify-between gap-2 border-border border-b px-4 py-3">
              <div className="min-w-0">
                <h2 className="font-heading text-sm font-semibold">New booking</h2>
                <p className="truncate text-xs text-muted-foreground">
                  {summarise(
                    draft,
                    RESOURCES.find((option) => option.id === draft.resourceId),
                  )}
                </p>
              </div>
              <PopoverClose
                aria-label="Discard this booking"
                className="-mr-1 flex size-6 shrink-0 items-center justify-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground"
              >
                <XIcon className="size-3.5" aria-hidden="true" />
              </PopoverClose>
            </div>

            {seed && (
              <AddBookingForm
                key={openedAt}
                seed={seed}
                isSysAdmin={isSysAdmin}
                editingId={null}
                eventsFor={eventsFor}
                onDraftChange={setDraft}
                onCancel={close}
                onSubmit={submit}
              />
            )}
          </PopoverContent>
        </Popover>
      )}

      <output className="block rounded-sm border border-dashed px-3 py-2 font-mono text-xs">
        {[
          `sysadmin=${isSysAdmin}`,
          `draft=${draft ? `${draft.resourceId || "unset"}@${clock(draft.startMinute)}-${clock(draft.endMinute)}` : "none"}`,
          `mode=${editingId ? `edit:${editingId}` : "create"}`,
          `conflicts=${conflicts.length}`,
          `edits=[${Object.keys(edits).join(", ")}]`,
          `created=[${created.map((entry) => `${entry.resourceId}:${entry.type}@${clock(entry.startMinute)}`).join(", ")}]`,
        ].join("; ")}
      </output>
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Compact Add Booking Dialog",
  component: DayShell,
  parameters: {
    layout: "fullscreen",
    // Every DayTimeline row heads itself with the same date, so a stacked day view produces
    // duplicate landmark names. Production ResourceSchedule has the same shape; fix it there, not here.
    a11y: { config: { rules: [{ id: "landmark-unique", enabled: false }] } },
  },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof DayShell>;

export default meta;
type Story = StoryObj<typeof meta>;

export const SlotPopover: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: "Add booking for Flow cytometer" }));
    const popover = within(await within(document.body).findByRole("dialog", { name: "New booking" }));
    expect(popover.queryByRole("alert")).not.toBeInTheDocument();

    // Flow cytometer is blocked out 13:00-15:00; stretching the end into it must name the clash and
    // the minutes shared with it, not merely warn that one exists.
    fireEvent.change(popover.getByLabelText("End"), { target: { value: "14:00" } });
    expect(popover.getByLabelText("End")).toHaveValue("14:00");
    const alert = await popover.findByRole("alert");
    expect(alert).toHaveTextContent("Overlaps 1 booking");
    expect(alert).toHaveTextContent("Laser realignment");
    expect(alert).toHaveTextContent("13:00–14:00");
    expect(popover.getByRole("button", { name: "Book" })).toBeDisabled();
  },
};
