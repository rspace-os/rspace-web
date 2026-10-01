// PROTOTYPE ONLY. Timezone patterns borrowed from Outlook and Google Calendar, applied to the booking form.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import { Temporal } from "@js-temporal/polyfill";
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { GlobeIcon, InfoIcon } from "lucide-react";
import * as React from "react";
import { openingWindowsOnDate } from "@/modules/booking/components/DayTimelineEvent";
import { BookingForm, type BookingFormState, type EditableBooking } from "@/modules/booking/creation/BookingForm";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { resolveBookingWindow } from "@/modules/booking/creation/ZonedBookingWindowFields";
import { coversInterval } from "@/modules/booking/domain/bookingOpeningHours";
import {
  addCalendarDays,
  type BookingWindowDraft,
  currentWallClock,
  displayInterval,
  isPlainDate,
  wallClockDraftFromInstants,
  zonedDayBounds,
} from "@/modules/booking/domain/bookingTime";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/modules/common/ui/dialog";
import { FieldDescription, FieldError, FieldLabel } from "@/modules/common/ui/field";
import { Input } from "@/modules/common/ui/input";
import { Label } from "@/modules/common/ui/label";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Heading } from "@/modules/common/ui/typography";
import { cn } from "@/modules/common/utils/cn";

// The bug report's case (a Tokyo instrument booked from Berlin), a week out so the form never flags it as past.
const VIEWER = "Europe/Berlin";
const DATE = addCalendarDays(currentWallClock(new Date().toISOString(), VIEWER).date, 7);
const INITIAL_WINDOW: BookingWindowDraft = { startDate: DATE, startTime: "15:00", endDate: DATE, endTime: "16:00" };
const SELECT_CLASS_NAME = "h-9 w-full rounded-sm bg-input/50 px-3 text-sm";

const tokyo: BookableItemOption = {
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

const berlin: BookableItemOption = {
  ...tokyo,
  configurationId: 55,
  targetId: 55,
  globalId: "IN55",
  name: "Confocal microscope in the Berlin lab",
  timezone: VIEWER,
  openingStart: "08:00",
  openingEnd: "18:00",
};

const editInterval = displayInterval(DATE, tokyo.timezone, "11:00", "12:00");
const editBooking: EditableBooking = {
  id: 41,
  version: 0,
  target: {
    relationTo: "booking-instruments",
    globalId: tokyo.globalId,
    value: { id: 54, name: tokyo.name, deleted: false },
  },
  timezone: tokyo.timezone,
  start: editInterval.start,
  end: editInterval.end,
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

function zoned(instant: string, timezone: string) {
  return Temporal.Instant.from(instant).toZonedDateTimeISO(timezone);
}

function clock(instant: string, timezone: string) {
  return zoned(instant, timezone).toPlainTime().toString({ smallestUnit: "minute" });
}

function weekday(instant: string, timezone: string) {
  return zoned(instant, timezone).toLocaleString("en-GB", { weekday: "short" });
}

function city(timezone: string) {
  return timezone.split("/").at(-1)?.replaceAll("_", " ") ?? timezone;
}

/** How far `to` runs from `from` at `instant`, e.g. "7 h ahead". */
function offsetLabel(instant: string, from: string, to: string) {
  const hours = (zoned(instant, to).offsetNanoseconds - zoned(instant, from).offsetNanoseconds) / 3.6e12;
  return hours === 0 ? "the same time" : `${Math.abs(hours)} h ${hours > 0 ? "ahead" : "behind"}`;
}

/** Google Calendar names zones in words, e.g. "Central European Summer Time". */
function zoneName(instant: string, timezone: string) {
  return (
    new Intl.DateTimeFormat("en-GB", { timeZone: timezone, timeZoneName: "long" })
      .formatToParts(new Date(instant))
      .find((part) => part.type === "timeZoneName")?.value ?? timezone
  );
}

function isOpen(window: { start: string; end: string }, target: BookableItemOption) {
  return coversInterval(target, target.timezone, window);
}

function Frame({ title, source, children }: { title: string; source: string; children: React.ReactNode }) {
  return (
    <main className="mx-auto max-w-5xl space-y-4 p-6">
      <div className="space-y-1">
        <Heading level={1}>{title}</Heading>
        <p className="text-sm text-muted-foreground">
          Borrowed from {source}. Viewer in {VIEWER}, instrument in {tokyo.timezone}.
        </p>
      </div>
      {children}
    </main>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 1. Google Calendar: the zone is named beside the time, and a "Time zone" link opens a dialog to change it.
 * ---------------------------------------------------------------------------------------------- */

function TimeZoneDialog({ zone, at, onApply }: { zone: string; at: string; onApply: (zone: string) => void }) {
  const id = React.useId();
  const [open, setOpen] = React.useState(false);
  const [choice, setChoice] = React.useState(zone);
  const option = (value: string, slug: string, label: string, detail: string) => (
    <div className="flex items-start gap-2">
      <RadioGroupItem value={value} id={`${id}-${slug}`} className="mt-0.5" />
      <Label htmlFor={`${id}-${slug}`} className="flex flex-col items-start gap-0.5 font-normal">
        <span className="font-medium">{label}</span>
        <span className="text-muted-foreground">{detail}</span>
      </Label>
    </div>
  );
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (next) setChoice(zone);
        setOpen(next);
      }}
    >
      <DialogTrigger render={<Button type="button" variant="link" size="sm" className="h-auto p-0" />}>
        Time zone
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Time zone for the start and end</DialogTitle>
          <DialogDescription>
            Changing it keeps the booking at the same moment; only the clock changes.
          </DialogDescription>
        </DialogHeader>
        <RadioGroup aria-label="Time zone" value={choice} onValueChange={(value) => setChoice(String(value))}>
          {option(VIEWER, "viewer", "Your time zone", `${zoneName(at, VIEWER)} (${VIEWER})`)}
          {option(
            tokyo.timezone,
            "instrument",
            "The instrument's time zone",
            `${zoneName(at, tokyo.timezone)} (${tokyo.timezone}), ${offsetLabel(at, VIEWER, tokyo.timezone)} of you`,
          )}
        </RadioGroup>
        <DialogFooter>
          <DialogClose render={<Button type="button" variant="outline" />}>Cancel</DialogClose>
          <Button
            type="button"
            onClick={() => {
              onApply(choice);
              setOpen(false);
            }}
          >
            Use this time zone
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function TimeZoneLinkPrototype({ mode }: { mode: "add" | "edit" }) {
  const [zone, setZone] = React.useState(VIEWER);
  const [draft, setDraft] = React.useState<BookingWindowDraft>(
    mode === "edit" ? wallClockDraftFromInstants(editBooking.start, editBooking.end, VIEWER) : INITIAL_WINDOW,
  );
  const [adjustment, setAdjustment] = React.useState<BookingWindowDraft>();
  const window = resolveBookingWindow(draft, zone).window;
  const at = window?.start ?? zonedDayBounds(DATE, zone).start;
  const other = zone === VIEWER ? tokyo.timezone : VIEWER;
  const apply = (next: string) => {
    if (next === zone) return;
    // Same instants, new wall clock.
    if (window) setAdjustment(wallClockDraftFromInstants(window.start, window.end, next));
    setZone(next);
  };
  const zoneLine = (
    <div className="space-y-1 text-sm">
      <div className="flex flex-wrap items-center gap-x-2">
        <GlobeIcon className="size-4" aria-hidden />
        <span>
          Times in {zoneName(at, zone)} ({zone})
        </span>
        <TimeZoneDialog zone={zone} at={at} onApply={apply} />
      </div>
      {window ? (
        <p className="text-muted-foreground">
          {clock(window.start, other)} to {clock(window.end, other)} {weekday(window.start, other)}{" "}
          {other === VIEWER ? "your time" : `in ${city(other)}, where the instrument is`}
        </p>
      ) : null}
    </div>
  );
  const shared = {
    ...formProps,
    displayTimezone: zone,
    windowAdjustment: adjustment,
    onStateChange: (state: BookingFormState) => setDraft(state.draft),
    afterWindowFields: zoneLine,
  };
  return (
    <Frame title="Time zone link beside the times" source="Google Calendar's event time zone link and dialog">
      <div className="max-w-2xl">
        {mode === "add" ? (
          <BookingForm
            {...shared}
            mode="add"
            eventKind="BOOKING"
            initialTarget={tokyo}
            initialWindow={INITIAL_WINDOW}
            lockTarget
          />
        ) : (
          <BookingForm {...shared} mode="edit" booking={editBooking} configuration={tokyo} />
        )}
      </div>
    </Frame>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 2. Labelled time zone columns (Google Calendar's up-to-three labelled columns, Outlook's extra Day-view zones).
 * ---------------------------------------------------------------------------------------------- */

const GUTTER_COLUMNS = { gridTemplateColumns: "3.5rem 3.5rem 1fr" };

function ZoneColumns({ draft, onMove }: { draft: BookingWindowDraft; onMove: (next: BookingWindowDraft) => void }) {
  const date = isPlainDate(draft.startDate) ? draft.startDate : DATE;
  const bounds = zonedDayBounds(date, VIEWER);
  const dayStart = Temporal.Instant.from(bounds.start);
  const hours = Array.from({ length: Math.ceil(bounds.elapsedMinutes / 60) }, (_, index) =>
    dayStart.add({ hours: index }),
  );
  const window = resolveBookingWindow(draft, VIEWER).window;
  const duration = window
    ? Temporal.Instant.from(window.end).since(window.start)
    : Temporal.Duration.from({ hours: 1 });
  const percent = (instant: string) =>
    Math.min(
      100,
      Math.max(0, (Temporal.Instant.from(instant).since(dayStart).total("minutes") / bounds.elapsedMinutes) * 100),
    );
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          {zoned(bounds.start, VIEWER).toLocaleString("en-GB", { dateStyle: "full" })}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <div className="grid text-xs font-semibold" style={GUTTER_COLUMNS}>
          <abbr title={`You, ${VIEWER}`}>BER</abbr>
          <abbr title={`Instrument, ${tokyo.timezone}`}>TYO</abbr>
        </div>
        <div className="relative grid text-xs" style={GUTTER_COLUMNS}>
          {hours.map((hour) => {
            const start = hour.toString();
            const open = isOpen({ start, end: hour.add({ hours: 1 }).toString() }, tokyo);
            const midnightThere = hour.toZonedDateTimeISO(tokyo.timezone).hour === 0;
            return (
              <React.Fragment key={start}>
                <span className="h-7 border-t border-border tabular-nums">{clock(start, VIEWER)}</span>
                <span className={cn("h-7 border-t border-border tabular-nums", midnightThere && "font-semibold")}>
                  {midnightThere ? weekday(start, tokyo.timezone) : clock(start, tokyo.timezone)}
                </span>
                <button
                  type="button"
                  className={cn(
                    "relative h-7 border-t border-l border-border outline-none focus-visible:z-10 focus-visible:ring-3 focus-visible:ring-ring/30",
                    open ? "bg-background hover:bg-accent" : "bg-muted",
                  )}
                  aria-label={`Start at ${clock(start, VIEWER)} your time, ${clock(start, tokyo.timezone)} in ${city(tokyo.timezone)}. Instrument ${open ? "open" : "closed"}.`}
                  onClick={() => onMove(wallClockDraftFromInstants(start, hour.add(duration).toString(), VIEWER))}
                />
              </React.Fragment>
            );
          })}
          {window ? (
            <div
              aria-hidden
              className="pointer-events-none absolute right-1 overflow-hidden rounded-sm bg-primary px-1 text-primary-foreground"
              style={{
                left: "7.25rem",
                top: `${percent(window.start)}%`,
                height: `${percent(window.end) - percent(window.start)}%`,
              }}
            >
              {clock(window.start, VIEWER)} to {clock(window.end, VIEWER)}
            </div>
          ) : null}
        </div>
        <p className="text-sm text-muted-foreground">
          Grey rows are when the instrument is closed. Pick a row to start the booking there.
        </p>
      </CardContent>
    </Card>
  );
}

function ZoneColumnsPrototype() {
  const [draft, setDraft] = React.useState<BookingWindowDraft>(INITIAL_WINDOW);
  const [adjustment, setAdjustment] = React.useState<BookingWindowDraft>();
  return (
    <Frame
      title="Labelled time zone columns beside the form"
      source="Google Calendar's labelled time zone columns and Outlook's extra time zones in Day view"
    >
      <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
        <div className="min-w-0 flex-1">
          <BookingForm
            {...formProps}
            mode="add"
            eventKind="BOOKING"
            initialTarget={tokyo}
            initialWindow={INITIAL_WINDOW}
            displayTimezone={VIEWER}
            lockTarget
            windowAdjustment={adjustment}
            onStateChange={(state: BookingFormState) => setDraft(state.draft)}
          />
        </div>
        <div className="lg:w-80 lg:shrink-0">
          <ZoneColumns draft={draft} onMove={setAdjustment} />
        </div>
      </div>
    </Frame>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 3. Time dropdowns that annotate each option (Google Calendar's end list shows the duration). Here every option
 *    also carries the instrument's clock, and closed times cannot be picked. Mock fields: the production fields
 *    are time inputs, not option lists.
 * ---------------------------------------------------------------------------------------------- */

function durationLabel(minutes: number) {
  if (minutes < 60) return `${minutes} mins`;
  return `${minutes / 60} ${minutes === 60 ? "hr" : "hrs"}`;
}

function AnnotatedTimeOptionsPrototype() {
  const id = React.useId();
  const step = tokyo.slotGranularityMinutes;
  const [start, setStart] = React.useState(() => displayInterval(DATE, VIEWER, "15:00", "16:00").start);
  const [minutes, setMinutes] = React.useState(60);
  const date = zoned(start, VIEWER).toPlainDate().toString();
  const bounds = zonedDayBounds(date, VIEWER);
  const after = (from: string, length: number) => Temporal.Instant.from(from).add({ minutes: length }).toString();
  const starts = Array.from({ length: bounds.elapsedMinutes / step }, (_, index) => after(bounds.start, index * step));
  const lengths = Array.from({ length: tokyo.maxBookingDurationMinutes / step }, (_, index) => (index + 1) * step);
  const there = (instant: string) => `${clock(instant, tokyo.timezone)} ${city(tokyo.timezone)}`;
  const windows = openingWindowsOnDate(date, VIEWER, tokyo);
  const inside = isOpen({ start, end: after(start, minutes) }, tokyo);
  const changeDate = (next: string) => {
    if (!isPlainDate(next)) return;
    const time = zoned(start, VIEWER).toPlainTime();
    setStart(
      Temporal.PlainDate.from(next).toZonedDateTime({ timeZone: VIEWER, plainTime: time }).toInstant().toString(),
    );
  };
  return (
    <Frame title="Every time option shows the instrument's clock" source="Google Calendar's annotated time dropdowns">
      <div className="max-w-2xl space-y-6">
        <div className="space-y-1">
          <FieldLabel htmlFor={`${id}-date`}>Date</FieldLabel>
          <Input
            id={`${id}-date`}
            type="date"
            value={date}
            onChange={(event) => changeDate(event.currentTarget.value)}
          />
          <FieldDescription>
            Times in {VIEWER}. Instrument open {windows.map((open) => `${open.start} to ${open.end}`).join(" and ")}{" "}
            your time; closed times are greyed out.
          </FieldDescription>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <FieldLabel htmlFor={`${id}-start`}>Start</FieldLabel>
            <select
              id={`${id}-start`}
              className={SELECT_CLASS_NAME}
              value={start}
              aria-invalid={inside ? undefined : true}
              onChange={(event) => setStart(event.currentTarget.value)}
            >
              {starts.map((option) => {
                const open = isOpen({ start: option, end: after(option, step) }, tokyo);
                return (
                  <option key={option} value={option} disabled={!open}>
                    {`${clock(option, VIEWER)} · ${there(option)}${open ? "" : ", closed"}`}
                  </option>
                );
              })}
            </select>
          </div>
          <div className="space-y-1">
            <FieldLabel htmlFor={`${id}-end`}>End</FieldLabel>
            <select
              id={`${id}-end`}
              className={SELECT_CLASS_NAME}
              value={minutes}
              aria-invalid={inside ? undefined : true}
              onChange={(event) => setMinutes(Number(event.currentTarget.value))}
            >
              {lengths.map((length) => {
                const end = after(start, length);
                const open = isOpen({ start, end }, tokyo);
                return (
                  <option key={length} value={length} disabled={!open}>
                    {`${clock(end, VIEWER)} (${durationLabel(length)}) · ${there(end)}${open ? "" : ", closed"}`}
                  </option>
                );
              })}
            </select>
          </div>
        </div>
        {inside ? null : (
          <FieldError>
            {clock(start, VIEWER)} to {clock(after(start, minutes), VIEWER)} is {there(start)} to{" "}
            {there(after(start, minutes))}, outside the instrument's opening hours.
          </FieldError>
        )}
      </div>
    </Frame>
  );
}

/* ------------------------------------------------------------------------------------------------
 * 4. Outlook's "Room is in a different time zone" infobar, shown the moment the item is chosen, before any time.
 * ---------------------------------------------------------------------------------------------- */

function DifferentZoneInfobarPrototype() {
  const id = React.useId();
  const [target, setTarget] = React.useState(tokyo);
  const at = zonedDayBounds(DATE, VIEWER).start;
  const windows = openingWindowsOnDate(DATE, VIEWER, target);
  return (
    <Frame
      title="Infobar when the item keeps different time"
      source={`Outlook's "Room is in a different time zone" infobar`}
    >
      <div className="max-w-2xl space-y-4">
        <div className="space-y-1">
          <FieldLabel htmlFor={`${id}-item`}>Bookable item</FieldLabel>
          <select
            id={`${id}-item`}
            className={SELECT_CLASS_NAME}
            value={target.globalId}
            onChange={(event) => setTarget(event.currentTarget.value === tokyo.globalId ? tokyo : berlin)}
          >
            <option value={tokyo.globalId}>{tokyo.name}</option>
            <option value={berlin.globalId}>{berlin.name}</option>
          </select>
          <FieldDescription>Stands in for the item picker, which needs a server.</FieldDescription>
        </div>
        {target.timezone === VIEWER ? null : (
          <Alert role="status">
            <InfoIcon aria-hidden />
            <AlertTitle>This instrument is in a different time zone</AlertTitle>
            <AlertDescription>
              {city(target.timezone)} is {offsetLabel(at, VIEWER, target.timezone)} of you. The instrument opens{" "}
              {target.openingStart} to {target.openingEnd} local time, which is{" "}
              {windows.map((open) => `${open.start} to ${open.end}`).join(" and ")} your time. Enter times below in your
              own time zone.
            </AlertDescription>
          </Alert>
        )}
        <BookingForm
          key={target.globalId}
          {...formProps}
          mode="add"
          eventKind="BOOKING"
          initialTarget={target}
          initialWindow={INITIAL_WINDOW}
          displayTimezone={VIEWER}
          lockTarget
        />
      </div>
    </Frame>
  );
}

function BookingFormTimezoneCalendarAppsPrototype({
  story,
}: {
  story: "linkAdd" | "linkEdit" | "columns" | "options" | "infobar";
}) {
  if (story === "linkAdd") return <TimeZoneLinkPrototype mode="add" />;
  if (story === "linkEdit") return <TimeZoneLinkPrototype mode="edit" />;
  if (story === "columns") return <ZoneColumnsPrototype />;
  if (story === "options") return <AnnotatedTimeOptionsPrototype />;
  return <DifferentZoneInfobarPrototype />;
}

const meta = {
  title: "Booking/Prototypes/Booking Form Timezones (Outlook and Google)",
  component: BookingFormTimezoneCalendarAppsPrototype,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <I18nRoot namespaces={["booking", "common"]}>
        <Story />
      </I18nRoot>
    ),
  ],
} satisfies Meta<typeof BookingFormTimezoneCalendarAppsPrototype>;

export default meta;
type Story = StoryObj<typeof meta>;

export const TimeZoneLinkAdd: Story = { args: { story: "linkAdd" } };
export const TimeZoneLinkEdit: Story = { args: { story: "linkEdit" } };
export const LabelledZoneColumns: Story = { args: { story: "columns" } };
export const AnnotatedTimeOptions: Story = { args: { story: "options" } };
export const DifferentZoneInfobar: Story = { args: { story: "infobar" } };
