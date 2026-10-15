import { CalendarCheck2Icon, PackageCheckIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/config";
import type { RuntimeFieldDefinition } from "@/modules/common/table-list/api-v2/runtimeFieldCatalog";
import { TableList, type TableListProps } from "@/modules/common/table-list/TableList";
import type { FilterExpression, FilterState } from "@/modules/common/table-list/types";
import { Button } from "@/modules/common/ui/button";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import { CalendarAgenda } from "./CalendarAgenda";
import { CalendarFilterControls } from "./CalendarFilterControls";
import { CalendarFilterIssue, type CalendarFilterIssueState } from "./CalendarFilterPanels";
import { CalendarResourceSchedule, ResourceScheduleSkeleton } from "./CalendarResourceSchedule";
import { CalendarTimeGrid } from "./CalendarTimeGrid";
import type { BookingCalendarResource, CalendarLayout, CalendarView } from "./calendarLayoutUtils";
import { BookingEditorsContext } from "./InlineBookingEditor";

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
    if (!event.target) continue;
    if (!pageResourceIds.has(event.target.globalId)) additionalResourceIds.add(event.target.globalId);
  }
  return additionalResourceIds.size;
}

export function BookingEventsCalendar({
  date,
  view,
  layout,
  timezone,
  availabilityStartMinute = 7 * 60,
  availabilityEndMinute = 19 * 60,
  events,
  blockingEvents,
  availabilityError,
  onRetryAvailability,
  resources,
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
  eventFilterConfig,
  itemFilterExpression = null,
  eventFilterExpression = null,
  itemFilterIssue,
  eventFilterIssue,
  eventRuntimeFieldDefinitions,
  eventRuntimeFieldAuthScope,
  onSelectEventRuntimeField,
  onEventFilterChange,
  mineOnly = false,
  onMineChange,
  myItemsOnly = false,
  onMyItemsChange,
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
  blockingEvents?: readonly BookingListDocument[];
  availabilityError?: boolean;
  onRetryAvailability?: () => void;
  /** Enabled configurations to render as resource rows, including resources with no events. */
  resources?: readonly BookingCalendarResource[];
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
  eventFilterConfig: ResolvedCollectionConfig<BookingListDocument>;
  itemFilterExpression?: FilterExpression<BookingConfiguration> | null;
  eventFilterExpression?: FilterExpression<BookingListDocument> | null;
  itemFilterIssue?: CalendarFilterIssueState;
  eventFilterIssue?: CalendarFilterIssueState;
  eventRuntimeFieldDefinitions?: readonly {
    namespace: string;
    definitions: readonly RuntimeFieldDefinition[];
  }[];
  eventRuntimeFieldAuthScope?: string | number;
  onSelectEventRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  onEventFilterChange?: (expression: FilterExpression<BookingListDocument> | null) => void;
  mineOnly?: boolean;
  onMineChange?: (mineOnly: boolean) => void;
  myItemsOnly?: boolean;
  onMyItemsChange?: (myItemsOnly: boolean) => void;
  creationDisabled?: boolean;
  onResourceRangeSelect?: (
    resource: BookableItemOption,
    range: { startMinute: number; endMinute: number },
    trigger: HTMLElement,
  ) => void;
}) {
  const { t } = useTranslation("booking");
  const todayValue = todayInTimeZone(timezone);
  const [editors, setEditors] = React.useState(() => new Map<string, BookingListDocument>());
  // Pin open cards by booking id so reconciliation can show new times without unmounting their drafts.
  const editorContext = React.useMemo(
    () => ({ setEditors, positions: new Map([...editors.values()].map((event) => [event.id, event])) }),
    [editors],
  );
  const displayedEvents = React.useMemo(() => {
    const byId = new Map(events.map((event) => [event.id, event]));
    for (const event of editors.values()) {
      if (!byId.has(event.id)) byId.set(event.id, event);
    }
    return [...byId.values()];
  }, [editors, events]);
  const eventFiltering:
    | false
    | { value: FilterState<BookingListDocument>; onChange: (value: FilterState<BookingListDocument>) => void } =
    searchControl || eventFilterConfig
      ? {
          value: { search: searchControl?.value ?? "", expression: eventFilterExpression },
          onChange: (next) => {
            searchControl?.onChange(next.search);
            onEventFilterChange?.(next.expression);
          },
        }
      : false;
  const calendarFeatures = {
    filtering: eventFiltering,
    sorting: false as const,
    pagination: false as const,
    columns: false as const,
  };
  const eventScopeIsFiltered =
    mineOnly ||
    myItemsOnly ||
    (eventFiltering !== false &&
      (eventFiltering.value.search.trim() !== "" || eventFiltering.value.expression !== null));
  const filterControls = (
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
  );
  return (
    <main className="min-h-screen w-full min-w-0 space-y-5 overflow-hidden bg-background p-4 sm:p-8">
      {isLoading && !isError && (
        <p role="status" className="sr-only">
          {t("calendar.loading")}
        </p>
      )}
      {availabilityError && !isError && (
        <div role="alert" className="flex items-center gap-3">
          <span>{t("calendar.rowAvailabilityUnavailable")}</span>
          <Button type="button" variant="outline" onClick={onRetryAvailability}>
            {t("calendar.retry")}
          </Button>
        </div>
      )}
      {isError && (
        <div role="alert" className="flex items-center gap-3">
          <span>{t("calendar.unavailable")}</span>
          <Button type="button" variant="outline" onClick={onRetry}>
            {t("calendar.retry")}
          </Button>
        </div>
      )}
      <TableList
        config={eventFilterConfig}
        rows={displayedEvents}
        getRowId={(event) => String(event.id)}
        clientSide={false}
        status={isLoading ? "refreshing" : "idle"}
        features={calendarFeatures}
        queryString={false}
        debounceSearch={true}
        headingClassName="text-2xl font-semibold"
        createAction={creationAction}
        onSelectRuntimeField={onSelectEventRuntimeField}
        runtimeFieldDefinitions={eventRuntimeFieldDefinitions}
        runtimeFieldAuthScope={eventRuntimeFieldAuthScope}
        filterButtons={{
          legend: t("calendar.quickFilters.legend"),
          controlsOnSeparateRow: true,
          controls: filterControls,
          hasChanges:
            date !== todayValue ||
            view !== "day" ||
            layout !== "resources" ||
            itemFilterExpression !== null ||
            eventFilterExpression !== null ||
            mineOnly ||
            myItemsOnly,
          buttons: [
            {
              id: "mine",
              label: t("calendar.quickFilters.mine"),
              icon: <CalendarCheck2Icon aria-hidden="true" />,
              pressed: mineOnly,
              onClick: () => onMineChange?.(!mineOnly),
            },
            {
              id: "my-items",
              label: t("calendar.quickFilters.myItems"),
              icon: <PackageCheckIcon aria-hidden="true" />,
              pressed: myItemsOnly,
              onClick: () => onMyItemsChange?.(!myItemsOnly),
            },
          ],
          onReset: () => {
            onMineChange?.(false);
            onMyItemsChange?.(false);
            onControlsReset();
          },
        }}
        renderRowsWhenEmpty
        renderRows={(calendarEvents) => {
          const extraEventResourceCount = additionalEventResourceCount(calendarEvents, resources);
          const resourceSchedule = (
            <CalendarResourceSchedule
              date={date}
              view={view}
              events={calendarEvents}
              blockingEvents={blockingEvents}
              resources={resources}
              includeEventResources={eventScopeIsFiltered}
              timezone={timezone}
              today={todayValue}
              availabilityStartMinute={availabilityStartMinute}
              availabilityEndMinute={availabilityEndMinute}
              resourceConfigurations={resourceConfigurations}
              creationDisabled={creationDisabled}
              isLoading={isLoading}
              onResourceRangeSelect={onResourceRangeSelect}
            />
          );
          return (
            <BookingEditorsContext.Provider value={editorContext}>
              {itemFilterIssue ? <CalendarFilterIssue {...itemFilterIssue} /> : null}
              {eventFilterIssue ? <CalendarFilterIssue {...eventFilterIssue} /> : null}
              {layout === "resources" && resourceTableProps?.status !== "loading" && extraEventResourceCount > 0 ? (
                <p className="border-b px-3 py-2 text-sm text-muted-foreground">
                  {t("calendar.additionalEventResources", { count: extraEventResourceCount })}
                </p>
              ) : null}
              {layout === "time-grid" && (
                <CalendarTimeGrid
                  date={date}
                  view={view}
                  events={calendarEvents}
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
                      resourceTableProps.status === "loading" || (eventScopeIsFiltered && calendarEvents.length > 0)
                    }
                    hideHeader
                    // The page owns both server-backed filter panels.
                    hideFilterPanel
                    variant="transparent"
                    renderRows={() => {
                      if (resourceTableProps.status === "loading") {
                        return <ResourceScheduleSkeleton period={{ date, view }} />;
                      }
                      return resourceSchedule;
                    }}
                  />
                ) : (
                  resourceSchedule
                ))}
              {layout === "agenda" && (
                <CalendarAgenda
                  date={date}
                  view={view}
                  events={calendarEvents}
                  timezone={timezone}
                  today={todayValue}
                  isLoading={isLoading}
                />
              )}
            </BookingEditorsContext.Provider>
          );
        }}
      />
    </main>
  );
}
