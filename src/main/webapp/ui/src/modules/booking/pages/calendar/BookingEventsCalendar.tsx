import { Link } from "@tanstack/react-router";
import { CalendarCheck2Icon, ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import {
  DayTimeline,
  type DayTimelineEvent,
  DayTimelineEventCard,
  type DayTimelineViewState,
} from "@/modules/booking/components/DayTimeline";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { sliceAcrossWallClockDay, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { TableList } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Input } from "@/modules/common/ui/input";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { cn } from "@/modules/common/utils/cn";
import { addCalendarDays, localToday } from "../all-bookable-items/calendarDate";

export const calendarLayouts = ["time-grid", "resources", "agenda"] as const;
export type CalendarLayout = (typeof calendarLayouts)[number];
export const calendarViews = ["day", "week", "month"] as const;
export type CalendarView = (typeof calendarViews)[number];

const bookingEventListConfig = resolveCollectionConfig<BookingListDocument>({
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["target.name", "purpose", "bookedBy"],
  labels: {
    singularKey: "booking:calendar.event",
    pluralKey: "booking:calendar.title",
    descriptionKey: "booking:calendar.description",
  },
  fields: [
    { name: "id", type: "number", labelKey: "booking:calendar.fields.id", list: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "instruments",
      hasMany: false,
      labelKey: "booking:calendar.fields.target",
      capabilities: { filterOperators: [] },
    },
    { name: "requesterId", type: "number", labelKey: "booking:calendar.fields.requester", list: false },
    { name: "purpose", type: "text", maximumLength: 1_000, labelKey: "booking:calendar.fields.purpose" },
    { name: "bookedBy", type: "text", maximumLength: 255, labelKey: "booking:calendar.fields.bookedBy" },
    {
      name: "privacy",
      type: "select",
      options: ["full", "busy"],
      labelKey: "booking:calendar.fields.privacy",
    },
    { name: "timezone", type: "text", labelKey: "booking:calendar.fields.timezone" },
    { name: "start", type: "dateTime", labelKey: "booking:calendar.fields.start" },
    { name: "end", type: "dateTime", labelKey: "booking:calendar.fields.end" },
    { name: "state", type: "select", options: ["CONFIRMED", "CANCELLED"], labelKey: "booking:calendar.fields.state" },
    { name: "canEdit", type: "boolean", labelKey: "booking:calendar.fields.editable", list: false },
    { name: "createdAt", type: "dateTime", labelKey: "booking:calendar.fields.createdAt", list: false },
    { name: "updatedAt", type: "dateTime", labelKey: "booking:calendar.fields.updatedAt", list: false },
  ],
});

function utcDate(date: string): Date {
  return new Date(`${date}T12:00:00Z`);
}

function firstOfMonth(date: string): string {
  return `${date.slice(0, 8)}01`;
}

function startOfWeek(date: string): string {
  return addCalendarDays(date, -((utcDate(date).getUTCDay() + 6) % 7));
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

export function calendarDates(date: string, view: CalendarView): readonly string[] {
  if (view === "day") return [date];
  if (view === "week") return datesFrom(startOfWeek(date), 7);
  return datesFrom(monthGridStart(date), 42);
}

function periodDates(date: string, view: CalendarView): readonly string[] {
  if (view !== "month") return calendarDates(date, view);
  return datesFrom(firstOfMonth(date), monthDayCount(date));
}

function shiftDate(date: string, view: CalendarView, delta: number): string {
  if (view === "day") return addCalendarDays(date, delta);
  if (view === "week") return addCalendarDays(date, delta * 7);
  const value = utcDate(date);
  return new Date(Date.UTC(value.getUTCFullYear(), value.getUTCMonth() + delta, 1)).toISOString().slice(0, 10);
}

function occursOn(event: BookingListDocument, date: string, timezone: string): boolean {
  const bounds = zonedDayBounds(date, timezone);
  return Date.parse(event.start) < Date.parse(bounds.end) && Date.parse(event.end) > Date.parse(bounds.start);
}

function eventsOn(events: readonly BookingListDocument[], date: string, timezone: string) {
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

function useScrollToToday(date: string, view: CalendarView) {
  const scrollRegionRef = React.useRef<HTMLElement>(null);
  React.useLayoutEffect(() => {
    if (view === "day") return;
    const scrollRegion = scrollRegionRef.current;
    const today = scrollRegion?.querySelector<HTMLElement>(`[data-calendar-date="${localToday()}"]`);
    if (!scrollRegion || !today) return;
    scrollRegion.scrollTo({
      left: today.offsetLeft - (scrollRegion.clientWidth - today.clientWidth) / 2,
      top: today.offsetTop - (scrollRegion.clientHeight - today.clientHeight) / 2,
    });
  }, [date, view]);
  return scrollRegionRef;
}

function toTimelineEvent(event: BookingListDocument, date: string, timezone: string): DayTimelineEvent {
  const slice = sliceAcrossWallClockDay(event.start, event.end, date, timezone);
  if (event.privacy === "busy") return { id: String(event.id), kind: "booking", privacy: "busy", ...slice };
  return {
    id: String(event.id),
    kind: "booking",
    privacy: "full",
    bookedBy: `${event.target.value.name} · ${event.bookedBy ?? ""}`,
    notes: event.purpose ?? undefined,
    canEdit: event.canEdit,
    ...slice,
  };
}

function EventCard({
  event,
  date,
  timezone,
  compact = false,
  overlay = false,
  alignExpandedEnd = false,
}: {
  event: BookingListDocument;
  date: string;
  timezone: string;
  compact?: boolean;
  overlay?: boolean;
  alignExpandedEnd?: boolean;
}) {
  const { t } = useTranslation("booking");
  const card = (
    <DayTimelineEventCard
      event={toTimelineEvent(event, date, timezone)}
      compactCards={compact}
      variant={overlay ? "timeline" : "flow"}
      alignExpandedEnd={alignExpandedEnd}
      renderEventActions={() => (
        <Link
          className={buttonVariants({ variant: "link", size: "xs" })}
          to="/booking/calendar/bookings/$id"
          params={{ id: String(event.id) }}
          search={{ date, target: event.target.globalId }}
        >
          {t("calendar.actions.edit")}
        </Link>
      )}
    />
  );
  return overlay ? <div className="relative min-h-12">{card}</div> : card;
}

function CalendarControls({
  date,
  view,
  layout,
  timezone,
  onDateChange,
  onViewChange,
  onLayoutChange,
}: {
  date: string;
  view: CalendarView;
  layout: CalendarLayout;
  timezone: string;
  onDateChange: (date: string) => void;
  onViewChange: (view: CalendarView) => void;
  onLayoutChange: (layout: CalendarLayout) => void;
}) {
  const { t } = useTranslation("booking");
  const periodLabel = t(`calendar.period.${view}`).toLocaleLowerCase();
  return (
    <div className="flex flex-col gap-4 border-b bg-muted/20 p-3 lg:flex-row lg:items-end lg:justify-between">
      <div className="flex flex-wrap items-end gap-2">
        <div className="space-y-1.5">
          <Label htmlFor="booking-events-calendar-date">{t("calendar.date")}</Label>
          <Input
            id="booking-events-calendar-date"
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
          aria-label={t("calendar.previousPeriod", { period: periodLabel })}
          onClick={() => onDateChange(shiftDate(date, view, -1))}
        >
          <ChevronLeftIcon />
        </Button>
        <Button type="button" variant="outline" onClick={() => onDateChange(localToday())}>
          {t("calendar.today")}
        </Button>
        <Button
          type="button"
          size="icon"
          variant="outline"
          aria-label={t("calendar.nextPeriod", { period: periodLabel })}
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
          <legend className="mb-1.5 text-sm font-medium">{t("calendar.layout.legend")}</legend>
          <div className="flex w-fit rounded-sm border bg-background p-1">
            {calendarLayouts.map((option) => (
              <Button
                key={option}
                type="button"
                size="sm"
                variant={layout === option ? "secondary" : "ghost"}
                aria-pressed={layout === option}
                onClick={() => onLayoutChange(option)}
              >
                {t(`calendar.layout.${option}`)}
              </Button>
            ))}
          </div>
        </fieldset>
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium">{t("calendar.period.legend")}</legend>
          <div className="flex w-fit rounded-sm border bg-background p-1">
            {calendarViews.map((option) => (
              <Button
                key={option}
                type="button"
                size="sm"
                variant={view === option ? "secondary" : "ghost"}
                aria-pressed={view === option}
                onClick={() => onViewChange(option)}
              >
                {t(`calendar.period.${option}`)}
              </Button>
            ))}
          </div>
        </fieldset>
      </div>
    </div>
  );
}

function TimeGrid({
  date,
  view,
  events,
  timezone,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
}) {
  const { t } = useTranslation("booking");
  const dates = calendarDates(date, view);
  const month = firstOfMonth(date).slice(0, 7);
  const calendarRef = useScrollToToday(date, view);
  return (
    <section aria-label={t("calendar.layout.time-grid")} className="p-3">
      {view === "day" ? (
        <DayTimeline
          date={date}
          timezone={timezone}
          events={eventsOn(events, date, timezone).map((event) => toTimelineEvent(event, date, timezone))}
          startWindow={7 * 60}
          endWindow={19 * 60}
          showZoomControls={false}
        />
      ) : (
        <section
          ref={calendarRef}
          className="overflow-x-auto rounded-sm border bg-card"
          // biome-ignore lint/a11y/noNoninteractiveTabindex: This named scroll region supports arrow-key navigation.
          tabIndex={0}
          aria-label={t("calendar.grid")}
          onKeyDown={scrollCalendarWithArrowKeys}
        >
          <div className="grid min-w-225 grid-cols-7">
            {dates.map((day, dayIndex) => (
              <section
                key={day}
                data-calendar-date={day}
                aria-label={formatDate(day, { dateStyle: "full" })}
                className={cn(
                  "min-h-44 border-r border-b p-1.5 last:border-r-0",
                  view === "month" && !day.startsWith(month) && "bg-muted/40 text-muted-foreground",
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
            ))}
          </div>
        </section>
      )}
    </section>
  );
}

function ResourceSchedule({
  date,
  view,
  events,
  timezone,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
}) {
  const { t } = useTranslation("booking");
  const [timelineViewState, setTimelineViewState] = React.useState<DayTimelineViewState>({
    zoom: 1,
    centerMinute: 13 * 60,
  });
  const dates = periodDates(date, view);
  const eventsByDate = new Map(dates.map((day) => [day, eventsOn(events, day, timezone)]));
  const calendarRef = useScrollToToday(date, view);
  const resources = [...new Map(events.map((event) => [event.target.globalId, event.target])).values()].toSorted(
    (left, right) => left.value.name.localeCompare(right.value.name),
  );
  return (
    <section aria-label={t("calendar.layout.resources")} className="p-3">
      <section
        ref={calendarRef}
        className="overflow-x-auto rounded-sm border bg-card"
        // biome-ignore lint/a11y/noNoninteractiveTabindex: This named scroll region supports arrow-key navigation.
        tabIndex={0}
        aria-label={t("calendar.resourceSchedule")}
        onKeyDown={scrollCalendarWithArrowKeys}
      >
        {view === "day" ? (
          <div className="min-w-225 divide-y">
            {resources.map((resource, index) => {
              const resourceEvents = events.filter((event) => event.target.globalId === resource.globalId);
              return (
                <section key={resource.globalId} className="grid grid-cols-[12rem_minmax(0,1fr)]">
                  <header className="border-r bg-muted/30 p-1">
                    <InventoryItem name={resource.value.name} globalId={resource.globalId} size="xs">
                      <InventoryLocationLink
                        name={resource.value.parentContainerName}
                        globalId={resource.value.parentContainerGlobalId}
                      />
                    </InventoryItem>
                  </header>
                  <DayTimeline
                    date={date}
                    timezone={timezone}
                    events={resourceEvents.map((event) => toTimelineEvent(event, date, timezone))}
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
            <div className="sticky left-0 z-20 border-r border-b bg-muted p-2 text-xs font-semibold">
              {t("calendar.item")}
            </div>
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
                  <InventoryItem name={resource.value.name} globalId={resource.globalId} size="xs">
                    <InventoryLocationLink
                      name={resource.value.parentContainerName}
                      globalId={resource.value.parentContainerGlobalId}
                    />
                  </InventoryItem>
                </div>
                {dates.map((day, dayIndex) => (
                  <div key={day} className="min-h-20 space-y-1 border-r border-b p-1">
                    {(eventsByDate.get(day) ?? [])
                      .filter((event) => event.target.globalId === resource.globalId)
                      .map((event) => (
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
                ))}
              </React.Fragment>
            ))}
          </div>
        )}
      </section>
    </section>
  );
}

function Agenda({
  date,
  view,
  events,
  timezone,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
}) {
  const { t } = useTranslation("booking");
  const dates = periodDates(date, view);
  const dateRailRef = useScrollToToday(date, view);
  return (
    <section aria-label={t("calendar.layout.agenda")} className="p-3">
      <div className="grid gap-4 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside
          ref={dateRailRef}
          className="max-h-160 overflow-y-auto rounded-sm border bg-muted/20 p-2"
          aria-label={t("calendar.datesInRange")}
          // biome-ignore lint/a11y/noNoninteractiveTabindex: Safari needs the named scroll region in the tab order.
          tabIndex={0}
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
        <section className="space-y-5" aria-label={t("calendar.agenda")}>
          {dates.flatMap((day) => {
            const dayEvents = eventsOn(events, day, timezone);
            if (dayEvents.length === 0) return [];
            return [
              <section key={day} className="space-y-2">
                <h2 className="sticky top-0 z-10 border-b bg-background/95 py-2 font-semibold backdrop-blur">
                  <time dateTime={day}>{formatDate(day, { dateStyle: "full" })}</time>
                </h2>
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

export function BookingEventsCalendar({
  date,
  view,
  layout,
  timezone,
  events,
  currentUserId,
  isLoading,
  isError,
  onRetry,
  onDateChange,
  onViewChange,
  onLayoutChange,
}: {
  date: string;
  view: CalendarView;
  layout: CalendarLayout;
  timezone: string;
  events: readonly BookingListDocument[];
  currentUserId: number;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onDateChange: (date: string) => void;
  onViewChange: (view: CalendarView) => void;
  onLayoutChange: (layout: CalendarLayout) => void;
}) {
  const { t } = useTranslation("booking");
  const [mineOnly, setMineOnly] = React.useState(false);
  const mine = events.filter((event) => event.requesterId === currentUserId);
  const table = useTableList({
    config: bookingEventListConfig,
    dataSource: { type: "client", rows: mineOnly ? mine : events },
    features: { sorting: false, pagination: false, columns: false },
    queryString: false,
  });
  return (
    <main className="min-h-screen w-full min-w-0 space-y-5 overflow-hidden bg-background p-4 sm:p-8">
      {isLoading && <p role="status">{t("calendar.loading")}</p>}
      {isError && (
        <div role="alert" className="flex items-center gap-3">
          <span>{t("calendar.unavailable")}</span>
          <Button type="button" variant="outline" onClick={onRetry}>
            {t("calendar.retry")}
          </Button>
        </div>
      )}
      <div className="overflow-hidden rounded-sm border bg-card">
        <CalendarControls
          date={date}
          view={view}
          layout={layout}
          timezone={timezone}
          onDateChange={onDateChange}
          onViewChange={onViewChange}
          onLayoutChange={onLayoutChange}
        />
      </div>
      {!isLoading && !isError && (
        <TableList
          {...table.tableProps}
          filterButtons={{
            legend: t("calendar.quickFilters.legend"),
            buttons: [
              {
                id: "mine",
                label: t("calendar.quickFilters.mine"),
                icon: <CalendarCheck2Icon aria-hidden="true" />,
                pressed: mineOnly,
                count: mine.length,
                onClick: () => setMineOnly((current) => !current),
              },
            ],
            onReset: () => setMineOnly(false),
          }}
          renderRows={(filteredEvents) => (
            <>
              {layout === "time-grid" && (
                <TimeGrid date={date} view={view} events={filteredEvents} timezone={timezone} />
              )}
              {layout === "resources" && (
                <ResourceSchedule date={date} view={view} events={filteredEvents} timezone={timezone} />
              )}
              {layout === "agenda" && <Agenda date={date} view={view} events={filteredEvents} timezone={timezone} />}
            </>
          )}
        />
      )}
    </main>
  );
}
