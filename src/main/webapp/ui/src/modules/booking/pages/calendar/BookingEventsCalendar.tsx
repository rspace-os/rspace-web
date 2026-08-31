import { Link } from "@tanstack/react-router";
import { CalendarCheck2Icon, PlusIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import {
  BookingDateControls,
  BookingTimeZoneBadge,
  bookingToolbarClassName,
} from "@/modules/booking/components/BookingToolbar";
import {
  DayTimeline,
  type DayTimelineEvent,
  DayTimelineEventCard,
  type DayTimelineViewState,
} from "@/modules/booking/components/DayTimeline";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { addCalendarDays, sliceAcrossWallClockDay, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import i18n from "@/modules/common/i18n";
import { TableList, type TableListProps } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { cn } from "@/modules/common/utils/cn";
import type { BookingConfigurationRow } from "../bookable-items/bookingConfiguration";

export const calendarLayouts = ["time-grid", "resources", "agenda"] as const;
export type CalendarLayout = (typeof calendarLayouts)[number];
export const calendarViews = ["day", "week", "month"] as const;
export type CalendarView = (typeof calendarViews)[number];
export type BookingCalendarResource = BookingListDocument["target"];

const bookingEventListConfig = resolveCollectionConfig<BookingListDocument>({
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["target.name", "purpose", "bookedBy"],
  labels: {
    singularKey: "booking:calendar.event",
    pluralKey: "booking:calendar.title",
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

function nextFreeRange(
  events: readonly BookingListDocument[],
  date: string,
  timezone: string,
  startWindow: number,
  endWindow: number,
  increment: number,
): { startMinute: number; endMinute: number } | undefined {
  const duration = Math.min(60, endWindow - startWindow);
  const firstStart = Math.ceil(startWindow / increment) * increment;
  const occupied = events.map((event) => sliceAcrossWallClockDay(event.start, event.end, date, timezone));
  for (let startMinute = firstStart; startMinute + duration <= endWindow; startMinute += increment) {
    const endMinute = startMinute + duration;
    if (!occupied.some((event) => event.startMinute < endMinute && event.endMinute > startMinute)) {
      return { startMinute, endMinute };
    }
  }
  return undefined;
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

function useScrollToToday(date: string, view: CalendarView, today: string) {
  const scrollRegionRef = React.useRef<HTMLElement>(null);
  React.useLayoutEffect(() => {
    if (view === "day") return;
    const scrollRegion = scrollRegionRef.current;
    const todayElement = scrollRegion?.querySelector<HTMLElement>(`[data-calendar-date="${today}"]`);
    if (!scrollRegion || !todayElement) return;
    scrollRegion.scrollTo({
      left: todayElement.offsetLeft - (scrollRegion.clientWidth - todayElement.clientWidth) / 2,
      top: todayElement.offsetTop - (scrollRegion.clientHeight - todayElement.clientHeight) / 2,
    });
  }, [date, today, view]);
  return scrollRegionRef;
}

function toTimelineEvent(event: BookingListDocument, date: string, timezone: string): DayTimelineEvent {
  const slice = sliceAcrossWallClockDay(event.start, event.end, date, timezone);
  if (event.privacy === "busy") return { id: String(event.id), kind: "booking", privacy: "busy", ...slice };
  const location =
    event.target.value.parentContainerName != null && event.target.value.parentContainerGlobalId != null
      ? {
          name: event.target.value.parentContainerName,
          globalId: event.target.value.parentContainerGlobalId,
        }
      : undefined;
  if (event.kind === "MAINTENANCE") {
    return {
      id: String(event.id),
      kind: "blockout",
      title: i18n.t("booking:bookings.maintenanceLabel"),
      item: { name: event.target.value.name, globalId: event.target.globalId, location },
      createdBy: event.createdBy ?? undefined,
      notes: event.purpose ?? undefined,
      ...slice,
    };
  }
  return {
    id: String(event.id),
    kind: "booking",
    privacy: "full",
    title: event.target.value.name,
    bookedBy: event.bookedBy ?? "",
    item: {
      name: event.target.value.name,
      globalId: event.target.globalId,
      location,
    },
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
}: {
  event: BookingListDocument;
  date: string;
  timezone: string;
  compact?: boolean;
  overlay?: boolean;
}) {
  const card = (
    <DayTimelineEventCard
      event={toTimelineEvent(event, date, timezone)}
      date={date}
      compactCards={compact}
      variant={overlay ? "timeline" : "flow"}
      renderEventActions={() => <BookingActions event={event} date={date} />}
      renderBlockoutActions={() => <BookingActions event={event} date={date} />}
    />
  );
  return overlay ? <div className={cn("relative", compact ? "min-h-18" : "min-h-14")}>{card}</div> : card;
}

function BookingActions({ event, date }: { event: BookingListDocument; date: string }) {
  const { t } = useTranslation("booking");
  return (
    <div
      className={cn(
        "grid border-border border-t text-xs",
        event.canEdit ? "grid-cols-2 divide-x divide-border" : "grid-cols-1",
      )}
    >
      <Link
        className={cn(buttonVariants({ variant: "link", size: "xs" }), "h-auto rounded-none py-2")}
        to="/booking/bookable-items/$globalId"
        params={{ globalId: event.target.globalId }}
      >
        {t("calendar.actions.viewDetails")}
      </Link>
      {event.canEdit ? (
        <Link
          className={cn(buttonVariants({ variant: "link", size: "xs" }), "h-auto rounded-none py-2")}
          to="/booking/calendar/bookings/$id"
          params={{ id: String(event.id) }}
          search={{ date, target: event.target.globalId }}
        >
          {t("calendar.actions.edit")}
        </Link>
      ) : null}
    </div>
  );
}

function actionsFor(events: readonly BookingListDocument[], date: string) {
  return (timelineEvent: Extract<DayTimelineEvent, { kind: "booking" }>) => {
    const event = events.find(({ id }) => String(id) === timelineEvent.id);
    return event ? <BookingActions event={event} date={date} /> : null;
  };
}

function blockoutActionsFor(events: readonly BookingListDocument[], date: string) {
  return (timelineEvent: Extract<DayTimelineEvent, { kind: "blockout" }>) => {
    const event = events.find(({ id }) => String(id) === timelineEvent.id);
    return event ? <BookingActions event={event} date={date} /> : null;
  };
}

function SegmentedControl<Option extends string>({
  legend,
  options,
  value,
  isDisabled,
  optionLabel,
  onChange,
}: {
  legend: string;
  options: readonly Option[];
  value: Option;
  isDisabled?: (option: Option) => boolean;
  optionLabel: (option: Option) => string;
  onChange: (option: Option) => void;
}) {
  return (
    <fieldset>
      <legend className="sr-only">{legend}</legend>
      <div className="flex w-fit rounded-sm border bg-background p-1">
        {options.map((option) => (
          <Button
            key={option}
            type="button"
            size="sm"
            variant={value === option ? "secondary" : "ghost"}
            aria-pressed={value === option}
            disabled={isDisabled?.(option)}
            onClick={() => onChange(option)}
          >
            {optionLabel(option)}
          </Button>
        ))}
      </div>
    </fieldset>
  );
}

export function CalendarTopBar({
  date,
  view,
  layout,
  timezone,
  today,
  onDateChange,
  onViewChange,
  onLayoutChange,
}: {
  date: string;
  view: CalendarView;
  layout: CalendarLayout;
  timezone: string;
  today: string;
  onDateChange: (date: string) => void;
  onViewChange: (view: CalendarView) => void;
  onLayoutChange: (layout: CalendarLayout) => void;
}) {
  const { t } = useTranslation("booking");
  const periodLabel = t(`calendar.period.${view}`).toLocaleLowerCase();
  return (
    <div role="toolbar" aria-label={t("calendar.toolbar")} className={bookingToolbarClassName}>
      <BookingDateControls
        date={date}
        today={today}
        timeZone={timezone}
        controlsLabel={t("calendar.dateControls")}
        navigationLabel={t("calendar.periodNavigation")}
        previousLabel={t("calendar.previousPeriod", { period: periodLabel })}
        todayLabel={t("calendar.today")}
        nextLabel={t("calendar.nextPeriod", { period: periodLabel })}
        jumpToDateLabel={t("calendar.jumpToDate")}
        onPrevious={() => onDateChange(shiftDate(date, view, -1))}
        onNext={() => onDateChange(shiftDate(date, view, 1))}
        onDateChange={onDateChange}
      />
      <fieldset className="min-w-0 overflow-x-auto xl:ml-auto xl:shrink-0 xl:overflow-visible">
        <legend className="sr-only">{t("calendar.displayControls")}</legend>
        <div className="flex w-max items-center gap-2">
          <BookingTimeZoneBadge timeZone={timezone} label={t("availabilityBar.timezone", { timezone })} />
          <SegmentedControl
            legend={t("calendar.layout.legend")}
            options={calendarLayouts}
            value={layout}
            optionLabel={(option) => t(`calendar.layout.${option}`)}
            onChange={(option) => {
              if (option === "resources" && view === "month") onViewChange("week");
              onLayoutChange(option);
            }}
          />
          <SegmentedControl
            legend={t("calendar.period.legend")}
            options={calendarViews}
            value={view}
            isDisabled={(option) => layout === "resources" && option === "month"}
            optionLabel={(option) => t(`calendar.period.${option}`)}
            onChange={onViewChange}
          />
        </div>
      </fieldset>
    </div>
  );
}

function TimeGrid({
  date,
  view,
  events,
  timezone,
  today,
  availabilityStartMinute,
  availabilityEndMinute,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
  today: string;
  availabilityStartMinute: number;
  availabilityEndMinute: number;
}) {
  const { t } = useTranslation("booking");
  const dates = calendarDates(date, view);
  const month = firstOfMonth(date).slice(0, 7);
  const calendarRef = useScrollToToday(date, view, today);
  return (
    <section aria-label={t("calendar.layout.time-grid")} className="p-3">
      {view === "day" ? (
        <DayTimeline
          date={date}
          timezone={timezone}
          events={eventsOn(events, date, timezone).map((event) => toTimelineEvent(event, date, timezone))}
          startWindow={availabilityStartMinute}
          endWindow={availabilityEndMinute}
          showZoomControls={false}
          renderEventActions={actionsFor(events, date)}
          renderBlockoutActions={blockoutActionsFor(events, date)}
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
            {dates.map((day) => (
              <section
                key={day}
                data-calendar-date={day}
                aria-label={formatDate(day, { dateStyle: "full" })}
                className={cn(
                  "min-h-44 border-r border-b p-1.5 last:border-r-0",
                  view === "month" && !day.startsWith(month) && "bg-muted/40 text-foreground",
                )}
              >
                <h2 className="mb-2 text-xs font-semibold">
                  <time dateTime={day}>{formatDate(day, { weekday: "short", day: "numeric" })}</time>
                </h2>
                <div className="space-y-1">
                  {eventsOn(events, day, timezone).map((event) => (
                    <EventCard
                      key={`${day}-${event.id}`}
                      event={event}
                      date={day}
                      timezone={timezone}
                      compact={view === "month"}
                      overlay
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
  resources,
  timezone,
  today,
  availabilityStartMinute,
  availabilityEndMinute,
  resourceConfigurations,
  creationDisabled,
  onResourceRangeSelect,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  resources?: readonly BookingCalendarResource[];
  timezone: string;
  today: string;
  availabilityStartMinute: number;
  availabilityEndMinute: number;
  resourceConfigurations?: readonly BookableItemOption[];
  creationDisabled: boolean;
  onResourceRangeSelect?: (
    resource: BookableItemOption,
    range: { startMinute: number; endMinute: number },
    trigger: HTMLElement,
  ) => void;
}) {
  const { t } = useTranslation("booking");
  const [timelineViewState, setTimelineViewState] = React.useState<DayTimelineViewState>({
    zoom: 1,
    centerMinute: 13 * 60,
  });
  const dates = React.useMemo(() => periodDates(date, view), [date, view]);
  const eventsByResourceAndDate = React.useMemo(() => {
    const index = new Map<string, BookingListDocument[]>();
    for (const event of events) {
      for (const day of dates) {
        if (!occursOn(event, day, timezone)) continue;
        const key = `${event.target.globalId}|${day}`;
        const rows = index.get(key) ?? [];
        rows.push(event);
        index.set(key, rows);
      }
    }
    for (const rows of index.values()) rows.sort((left, right) => left.start.localeCompare(right.start));
    return index;
  }, [dates, events, timezone]);
  const calendarRef = useScrollToToday(date, view, today);
  const resourceRows = React.useMemo(
    () =>
      [
        ...new Map(
          (resources ?? events.map((event) => event.target)).map((resource) => [resource.globalId, resource]),
        ).values(),
      ].toSorted((left, right) => left.value.name.localeCompare(right.value.name)),
    [events, resources],
  );
  const configurationByTarget = React.useMemo(
    () => new Map(resourceConfigurations?.map((configuration) => [configuration.globalId, configuration])),
    [resourceConfigurations],
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
            {resourceRows.map((resource, index) => {
              const resourceEvents = eventsByResourceAndDate.get(`${resource.globalId}|${date}`) ?? [];
              const configuration = configurationByTarget.get(resource.globalId);
              const proposedRange = configuration
                ? nextFreeRange(
                    resourceEvents,
                    date,
                    timezone,
                    availabilityStartMinute,
                    availabilityEndMinute,
                    configuration.slotGranularityMinutes,
                  )
                : undefined;
              return (
                <section key={resource.globalId} className="grid grid-cols-[12rem_minmax(0,1fr)_auto]">
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
                    startWindow={availabilityStartMinute}
                    endWindow={availabilityEndMinute}
                    showZoomControls={false}
                    variant="table-row"
                    itemName={resource.value.name}
                    viewState={timelineViewState}
                    onViewStateChange={setTimelineViewState}
                    showScrollbar={index === resourceRows.length - 1}
                    renderEventActions={actionsFor(resourceEvents, date)}
                    renderBlockoutActions={blockoutActionsFor(resourceEvents, date)}
                    snapIncrementMinutes={configuration?.slotGranularityMinutes}
                    creationDisabled={creationDisabled || !configuration}
                    onRangeSelect={
                      configuration && onResourceRangeSelect
                        ? (range, trigger) => onResourceRangeSelect(configuration, range, trigger)
                        : undefined
                    }
                  />
                  <div className="flex items-center border-l p-2">
                    <Button
                      type="button"
                      size="icon-sm"
                      variant="outline"
                      disabled={creationDisabled || !configuration || !proposedRange || !onResourceRangeSelect}
                      aria-label={t("calendar.actions.addForItem", { item: resource.value.name })}
                      onClick={(event) => {
                        if (!configuration || !proposedRange || !onResourceRangeSelect) return;
                        onResourceRangeSelect(configuration, proposedRange, event.currentTarget);
                      }}
                    >
                      <PlusIcon aria-hidden="true" />
                    </Button>
                  </div>
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
            {resourceRows.map((resource) => (
              <React.Fragment key={resource.globalId}>
                <div className="sticky left-0 z-10 border-r border-b bg-background p-1">
                  <InventoryItem name={resource.value.name} globalId={resource.globalId} size="xs">
                    <InventoryLocationLink
                      name={resource.value.parentContainerName}
                      globalId={resource.value.parentContainerGlobalId}
                    />
                  </InventoryItem>
                </div>
                {dates.map((day) => (
                  <div key={day} className="min-h-20 space-y-1 border-r border-b p-1">
                    {(eventsByResourceAndDate.get(`${resource.globalId}|${day}`) ?? []).map((event) => (
                      <EventCard key={event.id} event={event} date={day} timezone={timezone} compact overlay />
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
  today,
}: {
  date: string;
  view: CalendarView;
  events: readonly BookingListDocument[];
  timezone: string;
  today: string;
}) {
  const { t } = useTranslation("booking");
  const dates = periodDates(date, view);
  const dateRailRef = useScrollToToday(date, view, today);
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
  availabilityStartMinute = 7 * 60,
  availabilityEndMinute = 19 * 60,
  events,
  resources,
  currentUserId,
  isLoading,
  isError,
  onRetry,
  onDateChange,
  onViewChange,
  onLayoutChange,
  controls,
  creationAction,
  resourceConfigurations,
  resourceTableProps,
  creationDisabled = false,
  onResourceRangeSelect,
}: {
  date: string;
  view: CalendarView;
  layout: CalendarLayout;
  timezone: string;
  availabilityStartMinute?: number;
  availabilityEndMinute?: number;
  events: readonly BookingListDocument[];
  /** Enabled configurations to render as resource rows, including resources with no events. */
  resources?: readonly BookingCalendarResource[];
  currentUserId: number;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onDateChange: (date: string) => void;
  onViewChange: (view: CalendarView) => void;
  onLayoutChange: (layout: CalendarLayout) => void;
  /** Replaces the default top bar. Used by the top-bar prototypes; production leaves it unset. */
  controls?: React.ReactNode;
  /** Production creation action rendered without replacing the calendar controls. */
  creationAction?: React.ReactNode;
  resourceConfigurations?: readonly BookableItemOption[];
  resourceTableProps?: TableListProps<BookingConfigurationRow>;
  creationDisabled?: boolean;
  onResourceRangeSelect?: (
    resource: BookableItemOption,
    range: { startMinute: number; endMinute: number },
    trigger: HTMLElement,
  ) => void;
}) {
  const { t } = useTranslation("booking");
  const todayValue = todayInTimeZone(timezone);
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
      {!isLoading && !isError && (
        <TableList
          {...table.tableProps}
          createAction={creationAction}
          headerContent={
            <div className="overflow-hidden rounded-sm border bg-card">
              {controls ?? (
                <CalendarTopBar
                  date={date}
                  view={view}
                  layout={layout}
                  timezone={timezone}
                  today={todayValue}
                  onDateChange={onDateChange}
                  onViewChange={onViewChange}
                  onLayoutChange={onLayoutChange}
                />
              )}
            </div>
          }
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
                <TimeGrid
                  date={date}
                  view={view}
                  events={filteredEvents}
                  timezone={timezone}
                  today={todayValue}
                  availabilityStartMinute={availabilityStartMinute}
                  availabilityEndMinute={availabilityEndMinute}
                />
              )}
              {layout === "resources" &&
                (resourceTableProps ? (
                  <TableList
                    {...resourceTableProps}
                    hideHeader
                    variant="transparent"
                    renderRows={() => (
                      <ResourceSchedule
                        date={date}
                        view={view}
                        events={filteredEvents}
                        resources={resources}
                        timezone={timezone}
                        today={todayValue}
                        availabilityStartMinute={availabilityStartMinute}
                        availabilityEndMinute={availabilityEndMinute}
                        resourceConfigurations={resourceConfigurations}
                        creationDisabled={creationDisabled}
                        onResourceRangeSelect={onResourceRangeSelect}
                      />
                    )}
                  />
                ) : (
                  <ResourceSchedule
                    date={date}
                    view={view}
                    events={filteredEvents}
                    resources={resources}
                    timezone={timezone}
                    today={todayValue}
                    availabilityStartMinute={availabilityStartMinute}
                    availabilityEndMinute={availabilityEndMinute}
                    resourceConfigurations={resourceConfigurations}
                    creationDisabled={creationDisabled}
                    onResourceRangeSelect={onResourceRangeSelect}
                  />
                ))}
              {layout === "agenda" && (
                <Agenda date={date} view={view} events={filteredEvents} timezone={timezone} today={todayValue} />
              )}
            </>
          )}
        />
      )}
    </main>
  );
}
