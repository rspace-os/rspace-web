// PROTOTYPE ONLY. Explore ways to surface the gap between the viewer's timezone and the instrument's.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Temporal } from "@js-temporal/polyfill";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { ArrowRightIcon, ClockIcon, GlobeIcon } from "lucide-react";
import * as React from "react";
import { openingHoursOnDate } from "@/modules/booking/components/DayTimelineEvent";
import { BookingForm, type BookingFormState, type EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { resolveBookingWindow } from "@/modules/booking/creation/ZonedBookingWindowFields";
import {
  addCalendarDays,
  type BookingWindowDraft,
  currentWallClock,
  displayInterval,
  instantToDayMinute,
  wallClockDraftFromInstants,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { FieldDescription, FieldLabel } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Heading } from "@/modules/common/ui/typography";
import { cn } from "@/modules/common/utils/cn";

// The exact case from the bug report: a Tokyo instrument booked by someone in Berlin.
const DATE = "2026-09-25";
const VIEWER = "Europe/Berlin";
const INITIAL_WINDOW: BookingWindowDraft = { startDate: DATE, startTime: "15:00", endDate: DATE, endTime: "16:00" };

const item: BookableItemOption = {
  configurationId: 54,
  targetId: 54,
  globalId: "IN54",
  name: "test instrument booking in japan time zone",
  timezone: "Asia/Tokyo",
  slotGranularityMinutes: 15,
  openingStart: "10:00",
  openingEnd: "16:00",
  openDays: [1, 2, 3, 4, 5, 6, 7],
  openingExceptions: [],
  bufferBeforeMinutes: 0,
  bufferAfterMinutes: 0,
  maxBookingDurationMinutes: 240,
  allowDoubleBooking: false,
};

const editBooking: EditableBooking = {
  id: 41,
  version: 0,
  target: {
    relationTo: "booking-instruments",
    globalId: item.globalId,
    value: { id: 54, name: item.name, deleted: false },
  },
  timezone: item.timezone,
  start: "2026-09-25T02:00:00Z",
  end: "2026-09-25T03:00:00Z",
  state: "CONFIRMED",
  kind: "BOOKING",
  privacy: "full",
  cancellationReason: null,
  purpose: "Cell imaging",
  bookedBy: "You",
  createdBy: "You",
  canEdit: true,
  canCancel: true,
  createdAt: "2026-09-01T09:00:00Z",
  updatedAt: "2026-09-01T09:00:00Z",
};

const formProps = { token: "prototype", pending: false, onSubmit: () => Promise.resolve() };

function clock(instant: string, timezone: string) {
  return Temporal.Instant.from(instant).toZonedDateTimeISO(timezone).toPlainTime().toString({ smallestUnit: "minute" });
}

function weekday(instant: string, timezone: string) {
  return Temporal.Instant.from(instant).toZonedDateTimeISO(timezone).toLocaleString("en-GB", { weekday: "short" });
}

/** Hours the instrument runs ahead of the viewer at `instant`; negative when behind. */
function offsetHours(instant: string, viewer: string, instrument: string) {
  const at = Temporal.Instant.from(instant);
  const minutes =
    (at.toZonedDateTimeISO(instrument).offsetNanoseconds - at.toZonedDateTimeISO(viewer).offsetNanoseconds) / 60e9;
  return minutes / 60;
}

function offsetLabel(hours: number) {
  const magnitude = Math.abs(hours) % 1 === 0 ? String(Math.abs(hours)) : Math.abs(hours).toFixed(1);
  return hours === 0 ? "same time" : `${magnitude} h ${hours > 0 ? "ahead" : "behind"}`;
}

function draftWindow(draft: BookingWindowDraft, timezone: string) {
  return resolveBookingWindow(draft, timezone).window;
}

/** The instrument's opening window overlapping the viewer's day, plus the same window one day either side. */
function openingWindows(date: string, viewer: string, target: BookableItemOption) {
  const midday = Temporal.PlainDate.from(date).toZonedDateTime({ timeZone: viewer, plainTime: "12:00" }).toInstant();
  const centre = currentWallClock(midday, target.timezone).date;
  return [-1, 0, 1].map((days) =>
    displayInterval(addCalendarDays(centre, days), target.timezone, target.openingStart, target.openingEnd),
  );
}

function insideOpening(window: { start: string; end: string }, target: BookableItemOption) {
  const start = Temporal.Instant.from(window.start).toZonedDateTimeISO(target.timezone);
  const day = displayInterval(start.toPlainDate().toString(), target.timezone, target.openingStart, target.openingEnd);
  return Temporal.Instant.compare(window.start, day.start) >= 0 && Temporal.Instant.compare(window.end, day.end) <= 0;
}

function Frame({ title, source, children }: { title: string; source: string; children: React.ReactNode }) {
  return (
    <main className="mx-auto max-w-5xl space-y-4 p-6">
      <div className="space-y-1">
        <Heading level={1}>{title}</Heading>
        <p className="text-sm text-muted-foreground">
          Borrowed from {source}. Viewer in {VIEWER}, instrument in {item.timezone}.
        </p>
      </div>
      {children}
    </main>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 1. Offset banner with a suggested time (Outlook scheduling assistant / Google "suggested times").
 * ---------------------------------------------------------------------------------------------- */

function OffsetBanner({ draft, onApply }: { draft: BookingWindowDraft; onApply: (next: BookingWindowDraft) => void }) {
  const window = draftWindow(draft, VIEWER);
  if (!window)
    return (
      <span className="block">
        This instrument keeps {item.timezone} time. Pick a start and end to see it converted.
      </span>
    );
  const hours = offsetHours(window.start, VIEWER, item.timezone);
  const inside = insideOpening(window, item);
  const opening = openingWindows(draft.startDate, VIEWER, item)[1];
  const duration = Temporal.Instant.from(window.end).since(window.start);
  const suggestion = wallClockDraftFromInstants(
    opening.start,
    Temporal.Instant.from(opening.start).add(duration).toString(),
    VIEWER,
  );
  return (
    <span className="block space-y-2">
      <span className="block">
        <strong>{item.timezone}</strong> is {offsetLabel(hours)}. Your {clock(window.start, VIEWER)} to{" "}
        {clock(window.end, VIEWER)} is <strong>{clock(window.start, item.timezone)}</strong> to{" "}
        <strong>{clock(window.end, item.timezone)}</strong> ({weekday(window.start, item.timezone)}) on the instrument.
      </span>
      {inside ? (
        <span className="block">That is within the instrument's opening hours.</span>
      ) : (
        <span className="flex flex-wrap items-center gap-2">
          <span>
            The instrument opens {item.openingStart} to {item.openingEnd} its time, which is{" "}
            {clock(opening.start, VIEWER)} to {clock(opening.end, VIEWER)} for you.
          </span>
          <Button size="sm" variant="outline" type="button" onClick={() => onApply(suggestion)}>
            Move to {suggestion.startTime} to {suggestion.endTime}
          </Button>
        </span>
      )}
    </span>
  );
}

function OffsetBannerPrototype() {
  const [draft, setDraft] = React.useState<BookingWindowDraft>(INITIAL_WINDOW);
  const [adjustment, setAdjustment] = React.useState<BookingWindowDraft>();
  return (
    <Frame title="Offset banner with a suggested time" source="Outlook's scheduling assistant">
      <BookingForm
        {...formProps}
        mode="add"
        eventKind="BOOKING"
        initialTarget={item}
        initialWindow={INITIAL_WINDOW}
        displayTimezone={VIEWER}
        lockTarget
        windowAdjustment={adjustment}
        onStateChange={(state: BookingFormState) => setDraft(state.draft)}
        warning={<OffsetBanner draft={draft} onApply={setAdjustment} />}
      />
    </Frame>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 2. Aligned day strips: the viewer's hours on top, the instrument's hours beneath (World Time Buddy).
 * ---------------------------------------------------------------------------------------------- */

function AlignedTimeStrips({ draft }: { draft: BookingWindowDraft }) {
  const date = /^\d{4}-\d{2}-\d{2}$/.test(draft.startDate) ? draft.startDate : DATE;
  const bounds = zonedDayBounds(date, VIEWER);
  const dayMinutes = bounds.elapsedMinutes;
  const hours = Array.from({ length: dayMinutes / 60 }, (_, index) => index);
  const instantAt = (minute: number) => Temporal.Instant.from(bounds.start).add({ minutes: minute }).toString();
  const percent = (instant: string) =>
    Math.min(100, Math.max(0, (instantToDayMinute(instant, date, VIEWER) / dayMinutes) * 100));
  const open = openingWindows(date, VIEWER, item).filter(
    (window) =>
      Temporal.Instant.compare(window.end, bounds.start) > 0 && Temporal.Instant.compare(window.start, bounds.end) < 0,
  );
  const window = draftWindow(draft, VIEWER);
  const converted = openingHoursOnDate(date, VIEWER, item);
  const row = (label: string, timezone: string, tint: boolean) => (
    <div className="grid grid-cols-[7rem_1fr] items-center gap-2 text-xs">
      <span className="truncate font-medium">{label}</span>
      <div className="relative h-8">
        {tint
          ? open.map((segment) => (
              <div
                key={segment.start}
                className="absolute inset-y-0 rounded-sm bg-emerald-200"
                style={{
                  left: `${percent(segment.start)}%`,
                  width: `${percent(segment.end) - percent(segment.start)}%`,
                }}
              />
            ))
          : null}
        {window ? (
          <div
            className="absolute inset-y-0 rounded-sm border-2 border-primary"
            style={{ left: `${percent(window.start)}%`, width: `${percent(window.end) - percent(window.start)}%` }}
          />
        ) : null}
        <div className="absolute inset-0 grid" style={{ gridTemplateColumns: `repeat(${hours.length}, 1fr)` }}>
          {hours.map((hour) => {
            const at = Temporal.Instant.from(instantAt(hour * 60)).toZonedDateTimeISO(timezone);
            return (
              <span
                key={hour}
                className={cn("border-l border-border pl-0.5 leading-8 tabular-nums", at.hour === 0 && "font-semibold")}
              >
                {at.hour === 0 ? at.toLocaleString("en-GB", { day: "numeric", month: "short" }) : at.hour}
              </span>
            );
          })}
        </div>
      </div>
    </div>
  );
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <GlobeIcon className="size-4" aria-hidden />
          Same moment, both clocks
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {row(`You (${VIEWER})`, VIEWER, true)}
        {row(`Instrument (${item.timezone})`, item.timezone, true)}
        <p className="pt-1 text-sm text-muted-foreground">
          Green is when the instrument is open: {converted.start} to {converted.end} your time. The outline is your
          booking.
        </p>
      </CardContent>
    </Card>
  );
}

function AlignedTimeStripsPrototype() {
  const [draft, setDraft] = React.useState<BookingWindowDraft>(INITIAL_WINDOW);
  return (
    <Frame title="Aligned day strips under the time fields" source="World Time Buddy and Fantastical's time zone bar">
      <BookingForm
        {...formProps}
        mode="add"
        eventKind="BOOKING"
        initialTarget={item}
        initialWindow={INITIAL_WINDOW}
        displayTimezone={VIEWER}
        lockTarget
        showRulesSummary={false}
        onStateChange={(state: BookingFormState) => setDraft(state.draft)}
        afterWindowFields={<AlignedTimeStrips draft={draft} />}
      />
    </Frame>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 3. Zone switch: enter times in your zone or the instrument's, and the fields convert (Google Calendar).
 * ---------------------------------------------------------------------------------------------- */

function ZoneSwitchPrototype({ mode }: { mode: "add" | "edit" }) {
  const [zone, setZone] = React.useState(VIEWER);
  const [draft, setDraft] = React.useState<BookingWindowDraft>(
    mode === "edit" ? wallClockDraftFromInstants(editBooking.start, editBooking.end, VIEWER) : INITIAL_WINDOW,
  );
  const [adjustment, setAdjustment] = React.useState<BookingWindowDraft>();
  const window = draftWindow(draft, zone);
  const switchTo = (next: string) => {
    if (next === zone) return;
    // Keep the same instants; only the wall clock the fields show changes.
    if (window) setAdjustment(wallClockDraftFromInstants(window.start, window.end, next));
    setZone(next);
  };
  const choice = (value: string, label: string) => (
    <Button
      type="button"
      size="sm"
      variant={zone === value ? "default" : "outline"}
      aria-pressed={zone === value}
      onClick={() => switchTo(value)}
    >
      {label}
    </Button>
  );
  const other = zone === VIEWER ? item.timezone : VIEWER;
  return (
    <Frame title="Switch which clock the fields use" source="Google Calendar's time zone link beside the time">
      <div className="max-w-2xl space-y-3">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <ClockIcon className="size-4" aria-hidden />
          <span className="font-medium">Enter times in</span>
          <fieldset className="flex gap-1">
            <legend className="sr-only">Time zone for the time fields</legend>
            {choice(VIEWER, `Your time (${VIEWER})`)}
            {choice(item.timezone, `Instrument time (${item.timezone})`)}
          </fieldset>
        </div>
        {window ? (
          <p className="flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
            {clock(window.start, zone)} to {clock(window.end, zone)} {zone}
            <ArrowRightIcon className="size-3.5" aria-hidden />
            {clock(window.start, other)} to {clock(window.end, other)} {other} ({weekday(window.start, other)})
          </p>
        ) : null}
        {mode === "add" ? (
          <BookingForm
            {...formProps}
            mode="add"
            eventKind="BOOKING"
            initialTarget={item}
            initialWindow={INITIAL_WINDOW}
            displayTimezone={zone}
            lockTarget
            windowAdjustment={adjustment}
            onStateChange={(state: BookingFormState) => setDraft(state.draft)}
          />
        ) : (
          <BookingForm
            {...formProps}
            mode="edit"
            booking={editBooking}
            configuration={item}
            displayTimezone={zone}
            windowAdjustment={adjustment}
            onStateChange={(state: BookingFormState) => setDraft(state.draft)}
          />
        )}
      </div>
    </Frame>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 4. Dual clock under each time field (Fantastical's secondary time zone). Mock fields: the production
 *    fields cannot host content inside the input group yet.
 * ---------------------------------------------------------------------------------------------- */

function DualClockPrototype() {
  const [draft, setDraft] = React.useState<BookingWindowDraft>(INITIAL_WINDOW);
  const window = draftWindow(draft, VIEWER);
  const converted = openingHoursOnDate(draft.startDate || DATE, VIEWER, item);
  const inside = window ? insideOpening(window, item) : undefined;
  const timeField = (name: "start" | "end", label: string) => {
    const instant = window?.[name];
    const hours = instant ? offsetHours(instant, VIEWER, item.timezone) : undefined;
    return (
      <div className="space-y-1">
        <FieldLabel htmlFor={`dual-${name}`}>{label}</FieldLabel>
        <Input
          id={`dual-${name}`}
          type="time"
          step={item.slotGranularityMinutes * 60}
          value={draft[`${name}Time`]}
          aria-invalid={inside === false ? true : undefined}
          aria-describedby={`dual-${name}-instrument`}
          onChange={(event) => setDraft({ ...draft, [`${name}Time`]: event.currentTarget.value })}
        />
        <FieldDescription id={`dual-${name}-instrument`} className="flex flex-wrap items-center gap-1.5">
          {instant && hours !== undefined ? (
            <>
              <span className="tabular-nums">
                = {clock(instant, item.timezone)} {weekday(instant, item.timezone)}
              </span>
              <Badge variant="secondary">
                {item.timezone}, {offsetLabel(hours)}
              </Badge>
            </>
          ) : (
            <span>Shown in {item.timezone} once set.</span>
          )}
        </FieldDescription>
      </div>
    );
  };
  return (
    <Frame title="Instrument clock under each time field" source="Fantastical's secondary time zone">
      <div className="max-w-2xl space-y-6">
        <div className="space-y-1">
          <FieldLabel htmlFor="dual-date">Date</FieldLabel>
          <Input
            id="dual-date"
            type="date"
            value={draft.startDate}
            onChange={(event) =>
              setDraft({ ...draft, startDate: event.currentTarget.value, endDate: event.currentTarget.value })
            }
          />
          <FieldDescription>
            Times shown in {VIEWER}. Instrument open {converted.start} to {converted.end} your time ({item.openingStart}{" "}
            to {item.openingEnd} in {item.timezone}).
          </FieldDescription>
        </div>
        <div className="grid grid-cols-2 gap-4">
          {timeField("start", "Start")}
          {timeField("end", "End")}
        </div>
        {inside === false ? (
          <p className="text-sm text-destructive" role="alert">
            This booking must be within the bookable item's opening hours, {converted.start} to {converted.end} your
            time.
          </p>
        ) : null}
      </div>
    </Frame>
  );
}

function BookingFormTimezonePrototype({
  story,
}: {
  story: "banner" | "strips" | "switchAdd" | "switchEdit" | "dualClock";
}) {
  if (story === "banner") return <OffsetBannerPrototype />;
  if (story === "strips") return <AlignedTimeStripsPrototype />;
  if (story === "switchAdd") return <ZoneSwitchPrototype mode="add" />;
  if (story === "switchEdit") return <ZoneSwitchPrototype mode="edit" />;
  return <DualClockPrototype />;
}

const meta = {
  title: "Booking/Prototypes/Booking Form Timezones",
  component: BookingFormTimezonePrototype,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof BookingFormTimezonePrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const OffsetBannerWithSuggestion: Story = { args: { story: "banner" } };
export const AlignedDayStrips: Story = { args: { story: "strips" } };
export const ZoneSwitchAdd: Story = { args: { story: "switchAdd" } };
export const ZoneSwitchEdit: Story = { args: { story: "switchEdit" } };
export const DualClockFields: Story = { args: { story: "dualClock" } };
