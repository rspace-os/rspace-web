import { Link } from "@tanstack/react-router";
import { PencilIcon, PlusIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { DayTimeline, type DayTimelineViewState } from "@/modules/booking/components/DayTimeline";
import { useDayTimelineScrollSync } from "@/modules/booking/components/useDayTimelineScrollSync";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { cn } from "@/modules/common/utils/cn";
import { CalendarEventCard } from "./CalendarEventCard";
import {
  actionsFor,
  type BookingCalendarResource,
  blockoutActionsFor,
  type CalendarView,
  formatDate,
  nextFreeRange,
  occursOn,
  periodDates,
  scrollCalendarWithArrowKeys,
  toTimelineEvent,
  useScrollToToday,
} from "./calendarLayoutUtils";

function ResourceDateGrid({
  dates,
  view,
  children,
}: {
  dates: readonly string[];
  view: CalendarView;
  children: React.ReactNode;
}) {
  const { t } = useTranslation("booking");
  return (
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
      {children}
    </div>
  );
}

export function CalendarResourceSchedule({
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
  includeEventResources = false,
  onResourceRangeSelect,
  isLoading = false,
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
  includeEventResources?: boolean;
  isLoading?: boolean;
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
  const timelineScrollSync = useDayTimelineScrollSync();
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
          (includeEventResources
            ? [...(resources ?? []), ...events.map((event) => event.target)]
            : (resources ?? events.map((event) => event.target))
          ).map((resource) => [resource.globalId, resource]),
        ).values(),
      ].toSorted((left, right) => left.value.name.localeCompare(right.value.name)),
    [events, includeEventResources, resources],
  );
  const configurationByTarget = React.useMemo(
    () => new Map(resourceConfigurations?.map((configuration) => [configuration.globalId, configuration])),
    [resourceConfigurations],
  );
  const editConfigurationLink = (
    resource: BookingCalendarResource,
    configuration?: BookableItemOption,
    size: "icon-xs" | "icon-sm" = "icon-xs",
  ) => {
    if (configuration?.capabilities?.canEditConfiguration !== true) return null;
    const label = t("bookableItemDetails.edit");
    return (
      <Tooltip>
        <TooltipTrigger
          render={
            <Link
              aria-label={label}
              className={cn(buttonVariants({ variant: "outline", size }))}
              data-slot="button"
              to="/booking/bookable-items/$globalId/{-$tab}"
              params={{ globalId: resource.globalId, tab: "details" }}
              search={{ edit: true }}
            />
          }
        >
          <PencilIcon aria-hidden="true" />
        </TooltipTrigger>
        <TooltipContent role="tooltip" side="left">
          {label}
        </TooltipContent>
      </Tooltip>
    );
  };
  return (
    <section aria-label={t("calendar.layout.resources")} className="p-3" aria-busy={isLoading}>
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
              const canCreateBooking = configuration?.capabilities?.canCreateBooking === true;
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
                    <InventoryItem
                      locationPlacement="below"
                      name={resource.value.name}
                      globalId={resource.globalId}
                      href={`/globalId/${resource.globalId}`}
                      idLinkLabel={t("dayTimeline.expanded.openItem", { globalId: resource.globalId })}
                      size="xs"
                    >
                      <InventoryLocationLink
                        name={resource.value.parentContainerName}
                        globalId={resource.value.parentContainerGlobalId}
                      />
                    </InventoryItem>
                  </header>
                  <div className="relative grid min-h-32 min-w-0">
                    <div className="grid min-w-0" aria-hidden={isLoading || undefined} inert={isLoading}>
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
                        scrollSync={timelineScrollSync}
                        showScrollbar={index === resourceRows.length - 1}
                        renderEventActions={actionsFor(resourceEvents, timezone, date)}
                        renderBlockoutActions={blockoutActionsFor(resourceEvents, timezone, date)}
                        snapIncrementMinutes={configuration?.slotGranularityMinutes}
                        creationDisabled={isLoading || creationDisabled || !canCreateBooking}
                        onRangeSelect={
                          canCreateBooking && configuration && onResourceRangeSelect
                            ? (range, trigger) => onResourceRangeSelect(configuration, range, trigger)
                            : undefined
                        }
                      />
                    </div>
                    {isLoading && <Skeleton aria-hidden="true" className="absolute inset-0 h-full w-full" />}
                  </div>
                  <div className="flex flex-col items-center justify-center gap-1 border-l p-2">
                    <Button
                      type="button"
                      size="icon-sm"
                      variant="outline"
                      disabled={
                        isLoading || creationDisabled || !canCreateBooking || !proposedRange || !onResourceRangeSelect
                      }
                      aria-label={t("calendar.actions.addForItem", { item: resource.value.name })}
                      onClick={(event) => {
                        if (!canCreateBooking || !configuration || !proposedRange || !onResourceRangeSelect) return;
                        onResourceRangeSelect(configuration, proposedRange, event.currentTarget);
                      }}
                    >
                      <PlusIcon aria-hidden="true" />
                    </Button>
                    {editConfigurationLink(resource, configuration, "icon-sm")}
                  </div>
                </section>
              );
            })}
          </div>
        ) : (
          <ResourceDateGrid dates={dates} view={view}>
            {resourceRows.map((resource) => (
              <React.Fragment key={resource.globalId}>
                <div className="sticky left-0 z-10 border-r border-b bg-background p-1">
                  <div className="space-y-1">
                    <InventoryItem
                      locationPlacement="below"
                      name={resource.value.name}
                      globalId={resource.globalId}
                      href={`/globalId/${resource.globalId}`}
                      idLinkLabel={t("dayTimeline.expanded.openItem", { globalId: resource.globalId })}
                      size="xs"
                    >
                      <InventoryLocationLink
                        name={resource.value.parentContainerName}
                        globalId={resource.value.parentContainerGlobalId}
                      />
                    </InventoryItem>
                    {editConfigurationLink(resource, configurationByTarget.get(resource.globalId))}
                  </div>
                </div>
                {dates.map((day) => (
                  <div key={day} className="min-h-20 space-y-1 border-r border-b p-1">
                    {isLoading ? (
                      <Skeleton aria-hidden="true" className="h-16 w-full" />
                    ) : (
                      (eventsByResourceAndDate.get(`${resource.globalId}|${day}`) ?? []).map((event) => (
                        <CalendarEventCard
                          key={event.id}
                          event={event}
                          date={day}
                          timezone={timezone}
                          compact
                          overlay
                        />
                      ))
                    )}
                  </div>
                ))}
              </React.Fragment>
            ))}
          </ResourceDateGrid>
        )}
      </section>
    </section>
  );
}
export function ResourceScheduleSkeleton({ period }: { period?: { date: string; view: CalendarView } } = {}) {
  const { t } = useTranslation("common");
  const dates = period && period.view !== "day" ? periodDates(period.date, period.view) : undefined;
  return (
    <section className="p-3" aria-label={t("loading")} aria-busy="true">
      <div aria-hidden="true" className="overflow-hidden rounded-sm border">
        {dates && period ? (
          <ResourceDateGrid dates={dates} view={period.view}>
            {[0, 1, 2, 3].map((row) => (
              <React.Fragment key={row}>
                <div className="border-r border-b p-1">
                  <Skeleton className="h-16 w-full" />
                  {row === 0 && <Skeleton className="mt-1 h-6 w-full" />}
                </div>
                {dates.map((day) => (
                  <div key={day} className="min-h-20 border-r border-b p-1">
                    <Skeleton className="h-16 w-full" />
                  </div>
                ))}
              </React.Fragment>
            ))}
          </ResourceDateGrid>
        ) : (
          [0, 1, 2, 3].map((row) => (
            <div key={row} className="grid min-w-225 grid-cols-[12rem_minmax(0,1fr)_auto] border-b">
              <div className="border-r p-1">
                <Skeleton className="h-16 w-full" />
              </div>
              <Skeleton className="min-h-32 w-full" />
              <div className="border-l p-2">
                <Skeleton className="h-8 w-8" />
              </div>
            </div>
          ))
        )}
      </div>
    </section>
  );
}
