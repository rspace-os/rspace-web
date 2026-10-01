import { CalendarCheck2Icon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { TableList, type TableListProps } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Button } from "@/modules/common/ui/button";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import { CalendarAgenda } from "./CalendarAgenda";
import { CalendarFilterControls } from "./CalendarFilterControls";
import { CalendarResourceSchedule, ResourceScheduleSkeleton } from "./CalendarResourceSchedule";
import { CalendarTimeGrid } from "./CalendarTimeGrid";
import type { BookingCalendarResource, CalendarLayout, CalendarView } from "./calendarLayoutUtils";

export { ResourceScheduleSkeleton } from "./CalendarResourceSchedule";
export type {
  BookingCalendarResource,
  CalendarLayout,
  CalendarView,
} from "./calendarLayoutUtils";
export {
  calendarDates,
  calendarLayouts,
  calendarViews,
} from "./calendarLayoutUtils";

function additionalEventResourceCount(
  events: readonly BookingListDocument[],
  resources: readonly BookingCalendarResource[] | undefined,
): number {
  if (!resources) return 0;
  const pageResourceIds = new Set(resources.map((resource) => resource.globalId));
  const additionalResourceIds = new Set<string>();
  for (const event of events) {
    if (!pageResourceIds.has(event.target.globalId)) additionalResourceIds.add(event.target.globalId);
  }
  return additionalResourceIds.size;
}

const bookingEventListConfig = resolveCollectionConfig<BookingListDocument>({
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["target.name", "target.globalId", "purpose", "bookedBy"],
  labels: {
    singularKey: "booking:calendar.event",
    pluralKey: "booking:calendar.title",
  },
  fields: [
    { name: "id", type: "number", labelKey: "booking:calendar.fields.id", list: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "booking-instruments",
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
    {
      name: "kind",
      type: "select",
      options: ["BOOKING", "MAINTENANCE"],
      labelKey: "booking:bookableItemDetails.events.kind",
      list: false,
      form: false,
    },
    { name: "canEdit", type: "boolean", labelKey: "booking:calendar.fields.editable", list: false },
    { name: "createdAt", type: "dateTime", labelKey: "booking:calendar.fields.createdAt", list: false },
    { name: "updatedAt", type: "dateTime", labelKey: "booking:calendar.fields.updatedAt", list: false },
  ],
});
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
  onControlsReset,
  onViewChange,
  onLayoutChange,
  creationAction,
  resourceConfigurations,
  resourceTableProps,
  searchControl,
  onEventScopeChange,
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
  onControlsReset: () => void;
  onViewChange: (view: CalendarView) => void;
  onLayoutChange: (layout: CalendarLayout) => void;
  /** Production creation action rendered without replacing the calendar controls. */
  creationAction?: React.ReactNode;
  resourceConfigurations?: readonly BookableItemOption[];
  resourceTableProps?: TableListProps<BookingConfiguration>;
  searchControl?: { value: string; onChange: (search: string) => void };
  onEventScopeChange?: (isFiltered: boolean) => void;
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
  const eventFiltering = table.tableProps.features.filtering;
  const eventSearch = searchControl?.value ?? (eventFiltering !== false ? eventFiltering.value.search : "");
  const eventScopeIsFiltered =
    mineOnly || (eventFiltering !== false && (eventSearch.trim() !== "" || eventFiltering.value.expression !== null));
  React.useEffect(() => {
    onEventScopeChange?.(eventScopeIsFiltered);
  }, [eventScopeIsFiltered, onEventScopeChange]);
  const calendarFeatures =
    !searchControl || eventFiltering === false
      ? table.tableProps.features
      : {
          ...table.tableProps.features,
          filtering: {
            value: { ...eventFiltering.value, search: searchControl.value },
            onChange: (filters: typeof eventFiltering.value) => {
              eventFiltering.onChange(filters);
              searchControl.onChange(filters.search);
            },
          },
        };
  return (
    <main className="min-h-screen w-full min-w-0 space-y-5 overflow-hidden bg-background p-4 sm:p-8">
      {isLoading && !isError && (
        <p role="status" className="sr-only">
          {t("calendar.loading")}
        </p>
      )}
      {isError && (
        <div role="alert" className="flex items-center gap-3">
          <span>{t("calendar.unavailable")}</span>
          <Button type="button" variant="outline" onClick={onRetry}>
            {t("calendar.retry")}
          </Button>
        </div>
      )}
      {!isError && (
        <TableList
          {...table.tableProps}
          features={calendarFeatures}
          debounceSearch={resourceTableProps !== undefined}
          headingClassName="text-2xl font-semibold"
          createAction={creationAction}
          filterButtons={{
            legend: t("calendar.quickFilters.legend"),
            controls: (
              <CalendarFilterControls
                date={date}
                view={view}
                layout={layout}
                timezone={timezone}
                today={todayValue}
                onDateChange={onDateChange}
                onViewChange={onViewChange}
                onLayoutChange={onLayoutChange}
              />
            ),
            hasChanges: date !== todayValue || view !== "day" || layout !== "resources",
            buttons: [
              {
                id: "mine",
                label: t("calendar.quickFilters.mine"),
                icon: <CalendarCheck2Icon aria-hidden="true" />,
                pressed: mineOnly,
                count: isLoading ? undefined : mine.length,
                onClick: () => setMineOnly((current) => !current),
              },
            ],
            onReset: () => {
              setMineOnly(false);
              onControlsReset();
            },
          }}
          renderRowsWhenEmpty
          renderRows={(filteredEvents) => (
            <>
              {layout === "time-grid" && (
                <CalendarTimeGrid
                  date={date}
                  view={view}
                  events={filteredEvents}
                  timezone={timezone}
                  today={todayValue}
                  availabilityStartMinute={availabilityStartMinute}
                  availabilityEndMinute={availabilityEndMinute}
                  isLoading={isLoading}
                />
              )}
              {layout === "resources" &&
                (resourceTableProps ? (
                  <TableList
                    {...resourceTableProps}
                    features={{
                      ...resourceTableProps.features,
                      filtering: false,
                      pagination:
                        eventScopeIsFiltered && resourceTableProps.rows.length === 0
                          ? false
                          : resourceTableProps.features.pagination,
                    }}
                    status={resourceTableProps.status === "loading" ? "idle" : resourceTableProps.status}
                    renderRowsWhenEmpty={
                      resourceTableProps.status === "loading" || (eventScopeIsFiltered && filteredEvents.length > 0)
                    }
                    hideHeader
                    // The resource fetch honours only the search term, so a filter panel here
                    // would build an expression nothing reads.
                    hideFilterPanel
                    variant="transparent"
                    renderRows={() => {
                      if (resourceTableProps.status === "loading") {
                        return <ResourceScheduleSkeleton period={{ date, view }} />;
                      }
                      const extraEventResourceCount = additionalEventResourceCount(filteredEvents, resources);
                      return (
                        <>
                          {extraEventResourceCount > 0 && (
                            <p className="border-b px-3 py-2 text-sm text-muted-foreground">
                              {t("calendar.additionalEventResources", { count: extraEventResourceCount })}
                            </p>
                          )}
                          <CalendarResourceSchedule
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
                            includeEventResources={eventScopeIsFiltered}
                            isLoading={isLoading}
                            onResourceRangeSelect={onResourceRangeSelect}
                          />
                        </>
                      );
                    }}
                  />
                ) : (
                  <CalendarResourceSchedule
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
                    includeEventResources={eventScopeIsFiltered}
                    isLoading={isLoading}
                    onResourceRangeSelect={onResourceRangeSelect}
                  />
                ))}
              {layout === "agenda" && (
                <CalendarAgenda
                  date={date}
                  view={view}
                  events={filteredEvents}
                  timezone={timezone}
                  today={todayValue}
                  isLoading={isLoading}
                />
              )}
            </>
          )}
        />
      )}
    </main>
  );
}
