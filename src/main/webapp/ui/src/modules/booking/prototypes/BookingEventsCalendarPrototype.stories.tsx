// PROTOTYPE — three booking-event calendar concepts isolated to Storybook.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CalendarCheck2Icon, ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import * as React from "react";
import { expect, fireEvent, userEvent, waitFor, within } from "storybook/test";
import {
  DayTimeline,
  type DayTimelineEvent,
  DayTimelineEventCard,
  type DayTimelineViewState,
} from "@/modules/booking/components/DayTimeline";
import { sliceAcrossWallClockDay, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { TableList } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Input } from "@/modules/common/ui/input";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { cn } from "@/modules/common/utils/cn";
import { addCalendarDays, localToday } from "../pages/all-bookable-items/calendarDate";

const bookingCalendarPrototypeVariants = ["a", "b", "c"] as const;
type BookingCalendarPrototypeVariant = (typeof bookingCalendarPrototypeVariants)[number];
const bookingCalendarViews = ["day", "week", "month"] as const;
type BookingCalendarView = (typeof bookingCalendarViews)[number];
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

type BookingCalendarEvent = {
  id: number;
  target: { globalId: string; value: { name: string } };
  start: string;
  end: string;
  purpose: string | null;
  bookedBy: string | null;
  privacy: "full" | "busy";
  canEdit: boolean;
};

const bookingEventListConfig = resolveCollectionConfig<BookingCalendarEvent>({
  slug: "booking-events-prototype",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["target.name", "purpose", "bookedBy"],
  labels: {
    singularKey: "Booking event",
    pluralKey: "Booking events calendar",
    descriptionKey: "All layouts share the same date range, search, and personal-calendar filter.",
  },
  fields: [
    { name: "id", type: "number", labelKey: "ID", list: false },
    { name: "target", type: "relationship", relationTo: "instruments", hasMany: false, labelKey: "Resource" },
    { name: "purpose", type: "text", maximumLength: 1_000, labelKey: "Purpose" },
    { name: "bookedBy", type: "text", maximumLength: 255, labelKey: "Booked by" },
    { name: "privacy", type: "select", options: ["full", "busy"], labelKey: "Privacy" },
    { name: "start", type: "dateTime", labelKey: "Starts" },
    { name: "end", type: "dateTime", labelKey: "Ends" },
    { name: "canEdit", type: "boolean", labelKey: "Editable", list: false },
  ],
});

const storyEvents: readonly BookingCalendarEvent[] = [
  {
    id: 41,
    target: { globalId: "IN123", value: { name: "Confocal microscope" } },
    start: "2026-08-17T08:00:00Z",
    end: "2026-08-17T10:00:00Z",
    purpose: "Cell imaging",
    bookedBy: "Ada Lovelace (ada)",
    privacy: "full",
    canEdit: true,
  },
  {
    id: 42,
    target: { globalId: "IN124", value: { name: "Electron microscope" } },
    start: "2026-08-17T12:00:00Z",
    end: "2026-08-17T13:30:00Z",
    purpose: "Cryo-grid screening",
    bookedBy: "Grace Hopper (grace)",
    privacy: "full",
    canEdit: false,
  },
  {
    id: 43,
    target: { globalId: "IN124", value: { name: "Electron microscope" } },
    start: "2026-08-19T09:00:00Z",
    end: "2026-08-19T11:00:00Z",
    purpose: null,
    bookedBy: null,
    privacy: "busy",
    canEdit: false,
  },
  {
    id: 44,
    target: { globalId: "IN125", value: { name: "Flow cytometer" } },
    start: "2026-08-21T13:00:00Z",
    end: "2026-08-21T15:00:00Z",
    purpose: "Cell sorting",
    bookedBy: "Ada Lovelace (ada)",
    privacy: "full",
    canEdit: true,
  },
];

function isMine(event: BookingCalendarEvent): boolean {
  return event.bookedBy?.endsWith("(ada)") === true;
}

const variantNames: Record<BookingCalendarPrototypeVariant, string> = {
  a: "Time grid",
  b: "Resources",
  c: "Agenda",
};

const viewNames: Record<BookingCalendarView, string> = {
  day: "Day",
  week: "Week",
  month: "Month",
};

function utcDate(date: string): Date {
  return new Date(`${date}T12:00:00Z`);
}

function firstOfMonth(date: string): string {
  return `${date.slice(0, 8)}01`;
}

function startOfWeek(date: string): string {
  const mondayOffset = (utcDate(date).getUTCDay() + 6) % 7;
  return addCalendarDays(date, -mondayOffset);
}

function monthGridStart(date: string): string {
  return startOfWeek(firstOfMonth(date));
}

function monthDayCount(date: string): number {
  const value = utcDate(firstOfMonth(date));
  return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth() + 1, 0)).getUTCDate();
}

function datesFrom(start: string, count: number): readonly string[] {
  return Array.from({ length: count }, (_, index) => addCalendarDays(start, index));
}

function calendarDates(date: string, view: BookingCalendarView): readonly string[] {
  if (view === "day") return [date];
  if (view === "week") return datesFrom(startOfWeek(date), 7);
  return datesFrom(monthGridStart(date), 42);
}

function periodDates(date: string, view: BookingCalendarView): readonly string[] {
  if (view !== "month") return calendarDates(date, view);
  return datesFrom(firstOfMonth(date), monthDayCount(date));
}

function shiftDate(date: string, view: BookingCalendarView, delta: number): string {
  if (view === "day") return addCalendarDays(date, delta);
  if (view === "week") return addCalendarDays(date, delta * 7);
  const value = utcDate(date);
  return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth() + delta, 1)).toISOString().slice(0, 10);
}

function occursOn(event: BookingCalendarEvent, date: string, timezone: string): boolean {
  const bounds = zonedDayBounds(date, timezone);
  return Date.parse(event.start) < Date.parse(bounds.end) && Date.parse(event.end) > Date.parse(bounds.start);
}

function eventsOn(events: readonly BookingCalendarEvent[], date: string, timezone: string) {
  return events
    .filter((event) => occursOn(event, date, timezone))
    .toSorted((left, right) => left.start.localeCompare(right.start));
}

function formatDate(date: string, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(undefined, { ...options, timeZone: "UTC" }).format(utcDate(date));
}

function scrollCalendarWithArrowKeys(event: React.KeyboardEvent<HTMLElement>) {
  if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
  event.preventDefault();
  event.currentTarget.scrollBy({ left: event.key === "ArrowLeft" ? -240 : 240, behavior: "smooth" });
}

function useScrollToToday(active: boolean, date: string, view: BookingCalendarView) {
  const scrollRegionRef = React.useRef<HTMLElement>(null);
  React.useLayoutEffect(() => {
    if (!active || view === "day") return;
    const scrollRegion = scrollRegionRef.current;
    const today = scrollRegion?.querySelector<HTMLElement>(`[data-calendar-date="${localToday()}"]`);
    if (!scrollRegion || !today) return;
    scrollRegion.scrollTo({
      left: today.offsetLeft - (scrollRegion.clientWidth - today.clientWidth) / 2,
      top: today.offsetTop - (scrollRegion.clientHeight - today.clientHeight) / 2,
    });
  }, [active, date, view]);
  return scrollRegionRef;
}

function EventCard({
  event,
  date,
  timezone,
  compact = false,
  overlay = false,
  alignExpandedEnd = false,
}: {
  event: BookingCalendarEvent;
  date: string;
  timezone: string;
  compact?: boolean;
  overlay?: boolean;
  alignExpandedEnd?: boolean;
}) {
  const card = (
    <DayTimelineEventCard
      event={toTimelineEvent(event, date, timezone)}
      compactCards={compact}
      variant={overlay ? "timeline" : "flow"}
      alignExpandedEnd={alignExpandedEnd}
    />
  );
  return overlay ? <div className="relative min-h-12">{card}</div> : card;
}

function toTimelineEvent(event: BookingCalendarEvent, date: string, timezone: string): DayTimelineEvent {
  const slice = sliceAcrossWallClockDay(event.start, event.end, date, timezone);
  if (event.privacy === "busy") return { id: String(event.id), kind: "booking", privacy: "busy", ...slice };
  return {
    id: String(event.id),
    kind: "booking",
    privacy: "full",
    bookedBy: `${event.target.value.name} · ${event.bookedBy ?? "Booking"}`,
    notes: event.purpose ?? undefined,
    canEdit: event.canEdit,
    ...slice,
  };
}

function toTimelineEvents(events: readonly BookingCalendarEvent[], date: string, timezone: string) {
  return events.map((event) => toTimelineEvent(event, date, timezone));
}

function CalendarControls({
  date,
  view,
  variant,
  timezone,
  onDateChange,
  onViewChange,
  onVariantChange,
}: {
  date: string;
  view: BookingCalendarView;
  variant: BookingCalendarPrototypeVariant;
  timezone: string;
  onDateChange: (date: string) => void;
  onViewChange: (view: BookingCalendarView) => void;
  onVariantChange: (variant: BookingCalendarPrototypeVariant) => void;
}) {
  return (
    <div className="flex flex-col gap-4 border-b bg-muted/20 p-3 lg:flex-row lg:items-end lg:justify-between">
      <div className="flex flex-wrap items-end gap-2">
        <div className="space-y-1.5">
          <Label htmlFor="booking-events-prototype-date">Calendar date</Label>
          <Input
            id="booking-events-prototype-date"
            className="w-auto bg-background"
            type="date"
            value={date}
            onChange={(event) => onDateChange(event.currentTarget.value)}
          />
        </div>
        <Button
          type="button"
          size="icon"
          variant="outline"
          aria-label={`Previous ${view}`}
          onClick={() => onDateChange(shiftDate(date, view, -1))}
        >
          <ChevronLeftIcon />
        </Button>
        <Button type="button" variant="outline" onClick={() => onDateChange(localToday())}>
          Today
        </Button>
        <Button
          type="button"
          size="icon"
          variant="outline"
          aria-label={`Next ${view}`}
          onClick={() => onDateChange(shiftDate(date, view, 1))}
        >
          <ChevronRightIcon />
        </Button>
        <Badge variant="outline" className="h-8 bg-background px-2">
          {timezone}
        </Badge>
      </div>
      <div className="flex flex-wrap gap-3">
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium">Layout</legend>
          <div className="flex w-fit rounded-sm border bg-background p-1">
            {bookingCalendarPrototypeVariants.map((option) => (
              <Button
                key={option}
                type="button"
                size="sm"
                variant={variant === option ? "secondary" : "ghost"}
                aria-pressed={variant === option}
                onClick={() => onVariantChange(option)}
              >
                {variantNames[option]}
              </Button>
            ))}
          </div>
        </fieldset>
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium">Period</legend>
          <div className="flex w-fit rounded-sm border bg-background p-1">
            {bookingCalendarViews.map((option) => (
              <Button
                key={option}
                type="button"
                size="sm"
                variant={view === option ? "secondary" : "ghost"}
                aria-pressed={view === option}
                onClick={() => onViewChange(option)}
              >
                {viewNames[option]}
              </Button>
            ))}
          </div>
        </fieldset>
      </div>
    </div>
  );
}

function TimeGridVariant({
  date,
  view,
  active,
  events,
  timezone,
}: {
  date: string;
  view: BookingCalendarView;
  active: boolean;
  events: readonly BookingCalendarEvent[];
  timezone: string;
}) {
  const dates = calendarDates(date, view);
  const month = firstOfMonth(date).slice(0, 7);
  const calendarRef = useScrollToToday(active && view === "week", date, view);
  return (
    <section aria-label="Variant A: time grid" className="p-3">
      {view === "day" ? (
        <DayTimeline
          date={date}
          timezone={timezone}
          events={toTimelineEvents(eventsOn(events, date, timezone), date, timezone)}
          startWindow={7 * 60}
          endWindow={19 * 60}
          showZoomControls={false}
        />
      ) : (
        <section
          ref={calendarRef}
          className="overflow-x-auto rounded-sm border bg-card"
          // biome-ignore lint/a11y/noNoninteractiveTabindex: This named scroll region handles arrow-key navigation.
          tabIndex={0}
          aria-label="Calendar grid"
          onKeyDown={scrollCalendarWithArrowKeys}
        >
          <div className="grid min-w-225 grid-cols-7">
            {dates.map((day, dayIndex) => {
              const outsideMonth = view === "month" && !day.startsWith(month);
              return (
                <section
                  key={day}
                  data-calendar-date={day}
                  aria-label={formatDate(day, { dateStyle: "full" })}
                  className={cn(
                    "min-h-44 border-r border-b p-1.5 last:border-r-0",
                    outsideMonth && "bg-muted/40 text-muted-foreground",
                  )}
                >
                  <h3 className="mb-2 text-xs font-semibold">
                    <time dateTime={day}>{formatDate(day, { weekday: "short", day: "numeric" })}</time>
                  </h3>
                  <div className="space-y-1">
                    {eventsOn(events, day, timezone).map((event) => (
                      <EventCard
                        key={`${day}-${event.id}`}
                        event={event}
                        date={day}
                        timezone={timezone}
                        compact={view === "month"}
                        overlay
                        alignExpandedEnd={dayIndex % 7 >= 4}
                      />
                    ))}
                  </div>
                </section>
              );
            })}
          </div>
        </section>
      )}
    </section>
  );
}

function ResourceScheduleVariant({
  date,
  view,
  active,
  events,
  timezone,
}: {
  date: string;
  view: BookingCalendarView;
  active: boolean;
  events: readonly BookingCalendarEvent[];
  timezone: string;
}) {
  const [timelineViewState, setTimelineViewState] = React.useState<DayTimelineViewState>({
    zoom: 1,
    centerMinute: 13 * 60,
  });
  const dates = periodDates(date, view);
  const calendarRef = useScrollToToday(active, date, view);
  const resources = [...new Map(events.map((event) => [event.target.globalId, event.target])).values()].toSorted(
    (left, right) => left.value.name.localeCompare(right.value.name),
  );
  return (
    <section aria-label="Variant B: resource schedule" className="p-3">
      <section
        ref={calendarRef}
        className="overflow-x-auto rounded-sm border bg-card"
        // biome-ignore lint/a11y/noNoninteractiveTabindex: This named scroll region handles arrow-key navigation.
        tabIndex={0}
        aria-label="Resource booking schedule"
        onKeyDown={scrollCalendarWithArrowKeys}
      >
        {view === "day" ? (
          <div className="min-w-225 divide-y">
            {resources.map((resource, index) => {
              const resourceEvents = events.filter((event) => event.target.globalId === resource.globalId);
              return (
                <section key={resource.globalId} className="grid grid-cols-[12rem_minmax(0,1fr)]">
                  <header className="border-r bg-muted/30 p-1">
                    <InventoryItem name={resource.value.name} globalId={resource.globalId} size="xs" />
                  </header>
                  <DayTimeline
                    date={date}
                    timezone={timezone}
                    events={toTimelineEvents(resourceEvents, date, timezone)}
                    startWindow={7 * 60}
                    endWindow={19 * 60}
                    showZoomControls={false}
                    variant="table-row"
                    itemName={resource.value.name}
                    viewState={timelineViewState}
                    onViewStateChange={setTimelineViewState}
                    showScrollbar={index === resources.length - 1}
                  />
                </section>
              );
            })}
          </div>
        ) : (
          <div
            className="grid min-w-max"
            style={{
              gridTemplateColumns: `12rem repeat(${dates.length}, minmax(${view === "month" ? 54 : 128}px, 1fr))`,
            }}
          >
            <div className="sticky left-0 z-20 border-r border-b bg-muted p-2 text-xs font-semibold">Bookable item</div>
            {dates.map((day) => (
              <div
                key={day}
                data-calendar-date={day}
                className="border-r border-b bg-muted/60 p-2 text-center text-[11px] font-semibold"
              >
                <time dateTime={day}>{formatDate(day, { month: "short", day: "numeric" })}</time>
              </div>
            ))}
            {resources.map((resource) => (
              <React.Fragment key={resource.globalId}>
                <div className="sticky left-0 z-10 border-r border-b bg-background p-1">
                  <InventoryItem name={resource.value.name} globalId={resource.globalId} size="xs" />
                </div>
                {dates.map((day, dayIndex) => {
                  const matches = eventsOn(events, day, timezone).filter(
                    (event) => event.target.globalId === resource.globalId,
                  );
                  return (
                    <div key={day} className="min-h-20 space-y-1 border-r border-b p-1">
                      {matches.map((event) => (
                        <EventCard
                          key={event.id}
                          event={event}
                          date={day}
                          timezone={timezone}
                          compact
                          overlay
                          alignExpandedEnd={dayIndex >= dates.length / 2}
                        />
                      ))}
                    </div>
                  );
                })}
              </React.Fragment>
            ))}
          </div>
        )}
      </section>
    </section>
  );
}

function AgendaNavigatorVariant({
  date,
  view,
  active,
  events,
  timezone,
}: {
  date: string;
  view: BookingCalendarView;
  active: boolean;
  events: readonly BookingCalendarEvent[];
  timezone: string;
}) {
  const dates = periodDates(date, view);
  const dateRailRef = useScrollToToday(active, date, view);
  return (
    <section aria-label="Variant C: agenda navigator" className="p-3">
      <div className="grid gap-4 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside
          ref={dateRailRef}
          className="max-h-160 overflow-y-auto rounded-sm border bg-muted/20 p-2"
          aria-label="Dates in range"
        >
          <ol className="space-y-1">
            {dates.map((day) => {
              const count = eventsOn(events, day, timezone).length;
              return (
                <li
                  key={day}
                  data-calendar-date={day}
                  className={cn(
                    "flex items-center justify-between gap-3 rounded-sm px-2 py-2",
                    count && "bg-background shadow-sm",
                  )}
                >
                  <time dateTime={day} className="text-sm font-medium">
                    {formatDate(day, { weekday: "short", month: "short", day: "numeric" })}
                  </time>
                  <Badge variant={count ? "default" : "outline"}>{count}</Badge>
                </li>
              );
            })}
          </ol>
        </aside>
        <section className="space-y-5" aria-label="Booking agenda">
          {dates.flatMap((day) => {
            const dayEvents = eventsOn(events, day, timezone);
            if (dayEvents.length === 0) return [];
            return [
              <section key={day} className="space-y-2">
                <h3 className="sticky top-0 z-10 border-b bg-background/95 py-2 font-semibold backdrop-blur">
                  <time dateTime={day}>{formatDate(day, { dateStyle: "full" })}</time>
                </h3>
                <ol className="space-y-2">
                  {dayEvents.map((event) => (
                    <li key={event.id}>
                      <EventCard event={event} date={day} timezone={timezone} />
                    </li>
                  ))}
                </ol>
              </section>,
            ];
          })}
        </section>
      </div>
    </section>
  );
}

function CalendarBody({
  date,
  view,
  variant,
  timezone,
  events,
}: {
  date: string;
  view: BookingCalendarView;
  variant: BookingCalendarPrototypeVariant;
  timezone: string;
  events: readonly BookingCalendarEvent[];
}) {
  return (
    <>
      <div hidden={variant !== "a"}>
        <TimeGridVariant active={variant === "a"} date={date} view={view} events={events} timezone={timezone} />
      </div>
      <div hidden={variant !== "b"}>
        <ResourceScheduleVariant active={variant === "b"} date={date} view={view} events={events} timezone={timezone} />
      </div>
      <div hidden={variant !== "c"}>
        <AgendaNavigatorVariant active={variant === "c"} date={date} view={view} events={events} timezone={timezone} />
      </div>
    </>
  );
}

function BookingEventsCalendarPrototype() {
  const [date, setDate] = React.useState("2026-08-17");
  const [view, setView] = React.useState<BookingCalendarView>("week");
  const [variant, setVariant] = React.useState<BookingCalendarPrototypeVariant>("a");
  const [mineOnly, setMineOnly] = React.useState(false);
  const timezone = "Europe/Berlin";
  const table = useTableList({
    config: bookingEventListConfig,
    dataSource: { type: "client", rows: mineOnly ? storyEvents.filter(isMine) : storyEvents },
    features: { sorting: false, pagination: false, columns: false },
    queryString: false,
  });

  return (
    <main className="min-h-screen w-full min-w-0 space-y-5 overflow-hidden bg-background p-4 sm:p-8">
      <TableList
        {...table.tableProps}
        filterButtons={{
          legend: "Booking event quick filters",
          buttons: [
            {
              id: "mine",
              label: "My calendar",
              icon: <CalendarCheck2Icon aria-hidden="true" />,
              pressed: mineOnly,
              count: storyEvents.filter(isMine).length,
              onClick: () => setMineOnly((current) => !current),
            },
          ],
          onReset: () => setMineOnly(false),
        }}
        renderRows={(events) => (
          <>
            <CalendarControls
              date={date}
              view={view}
              variant={variant}
              timezone={timezone}
              onDateChange={setDate}
              onViewChange={setView}
              onVariantChange={setVariant}
            />
            <CalendarBody date={date} view={view} variant={variant} timezone={timezone} events={events} />
          </>
        )}
      />
    </main>
  );
}

const meta = {
  title: "Booking/Prototypes/Booking Events Calendar",
  component: BookingEventsCalendarPrototype,
  parameters: { layout: "fullscreen" },
  decorators: [
    (Story) => (
      <QueryClientProvider client={queryClient}>
        <I18nRoot namespaces={["booking", "common"]}>
          <Story />
        </I18nRoot>
      </QueryClientProvider>
    ),
  ],
} satisfies Meta<typeof BookingEventsCalendarPrototype>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Interactive: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const search = await canvas.findByRole("textbox", { name: "Search Booking events calendar" });
    await userEvent.type(search, "Grace");
    expect(canvas.queryByRole("button", { name: /Show details for Confocal microscope/ })).not.toBeInTheDocument();
    expect(canvas.getByRole("button", { name: /Show details for Electron microscope/ })).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Clear search" }));
    await userEvent.click(canvas.getByRole("button", { name: /^Filters/ }));
    expect(canvas.getByRole("heading", { name: "Filter records" })).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Close filters" }));
    const timeGridVariant = await canvas.findByRole("region", { name: "Variant A: time grid" });
    const timeGrid = within(timeGridVariant).getByRole("region", { name: "Calendar grid" });
    const timeGridBody = timeGrid.firstElementChild;
    if (!(timeGridBody instanceof HTMLElement)) throw new Error("Time grid body is missing");
    const timeGridHeight = timeGridBody.getBoundingClientRect().height;
    const timeGridDetails = within(timeGridVariant).getByRole("button", {
      name: /Show details for Confocal microscope/,
    });
    await userEvent.click(timeGridDetails);
    expect(timeGridDetails).toHaveAttribute("aria-expanded", "true");
    expect(timeGridBody.getBoundingClientRect().height).toBeCloseTo(timeGridHeight, 0);
    await userEvent.click(timeGridDetails);
    await userEvent.click(canvas.getByRole("button", { name: "Resources" }));
    const resourceVariant = canvas.getByRole("region", { name: "Variant B: resource schedule" });
    const resourceSchedule = within(resourceVariant).getByRole("region", { name: "Resource booking schedule" });
    const resourceGrid = resourceSchedule.firstElementChild;
    if (!(resourceGrid instanceof HTMLElement)) throw new Error("Resource grid is missing");
    const resourceGridHeight = resourceGrid.getBoundingClientRect().height;
    expect(resourceVariant).toBeVisible();
    expect(resourceVariant.querySelectorAll("[data-inventory-item]")).toHaveLength(3);
    expect(canvas.queryByRole("heading", { name: /^[ABC] ·/ })).not.toBeInTheDocument();
    const details = within(resourceVariant).getByRole("button", { name: /Show details for Confocal microscope/ });
    await userEvent.click(details);
    expect(details).toHaveAttribute("aria-expanded", "true");
    expect(resourceGrid.getBoundingClientRect().height).toBeCloseTo(resourceGridHeight, 0);
    expect(within(resourceVariant).getByText("Cell imaging")).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Day" }));
    const timelines = within(resourceVariant).getAllByTestId("day-timeline-scroller");
    expect(timelines).toHaveLength(3);
    expect(getComputedStyle(timelines[0]).scrollbarWidth).toBe("none");
    expect(getComputedStyle(timelines[1]).scrollbarWidth).toBe("none");
    expect(getComputedStyle(timelines[2]).scrollbarWidth).not.toBe("none");
    timelines[0].scrollLeft += 300;
    fireEvent.scroll(timelines[0]);
    await waitFor(() => {
      expect(timelines[1].scrollLeft).toBeCloseTo(timelines[0].scrollLeft, 0);
      expect(timelines[2].scrollLeft).toBeCloseTo(timelines[0].scrollLeft, 0);
    });
    await userEvent.click(canvas.getByRole("button", { name: "Week" }));
    await userEvent.click(canvas.getByRole("button", { name: "Time grid" }));
    await userEvent.click(canvas.getByRole("button", { name: "Today" }));
    resourceSchedule.style.width = "400px";
    await userEvent.click(canvas.getByRole("button", { name: "Resources" }));
    await waitFor(() => expect(resourceSchedule.scrollLeft).toBeGreaterThan(0));
    resourceSchedule.style.removeProperty("width");
    await userEvent.click(canvas.getByRole("button", { name: "Month" }));
    expect(canvas.queryByText("No events")).not.toBeInTheDocument();
    expect(resourceSchedule.style.width).toBe("");
    expect(canvas.queryByRole("navigation", { name: "Booking calendar prototype variants" })).not.toBeInTheDocument();
  },
};
