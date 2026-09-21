import { CalendarCheck2Icon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import type { BookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { todayInTimeZone } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { ResolvedCollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import type { RuntimeFieldDefinition } from "@/modules/common/table-list/adapters/apiV2/runtimeFieldCatalog";
import { TableList, type TableListProps } from "@/modules/common/table-list/TableList";
import type { FilterExpression, FilterState } from "@/modules/common/table-list/tableListState";
import { Button } from "@/modules/common/ui/button";
import type { BookingConfiguration } from "../bookable-items/bookingConfiguration";
import { CalendarAgenda } from "./CalendarAgenda";
import { CalendarFilterControls } from "./CalendarFilterControls";
import {
  CalendarFilterButtons,
  CalendarFilterIssue,
  type CalendarFilterIssueState,
  CalendarFilterPanel,
  type CalendarFilterPanelKind,
} from "./CalendarFilterPanels";
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

// This fallback keeps the presentational calendar usable from stories and small callers that do
// not need the API metadata-backed filter panels. Production passes the enriched event config.
const calendarEventFallbackConfig = resolveCollectionConfig<BookingListDocument>({
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["target.name", "target.globalId", "purpose"],
  labels: {
    singularKey: "booking:calendar.event",
    pluralKey: "booking:calendar.title",
  },
  fields: [
    { name: "id", type: "number", labelKey: "booking:calendar.fields.id", list: false, form: false },
    {
      name: "target",
      type: "relationship",
      relationTo: "booking-instruments",
      hasMany: false,
      labelKey: "booking:calendar.fields.target",
      capabilities: { filterOperators: [] },
    },
    { name: "purpose", type: "text", maximumLength: 1_000, labelKey: "booking:calendar.fields.purpose" },
    {
      name: "bookedBy",
      type: "text",
      maximumLength: 255,
      labelKey: "booking:calendar.fields.bookedBy",
      capabilities: { filterOperators: [] },
    },
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
  itemFilterConfig,
  eventFilterConfig,
  itemFilterExpression = null,
  eventFilterExpression = null,
  itemFilterIssue,
  eventFilterIssue,
  itemRuntimeFieldDefinitions,
  eventRuntimeFieldDefinitions,
  itemRuntimeFieldAuthScope,
  eventRuntimeFieldAuthScope,
  onSelectItemRuntimeField,
  onSelectEventRuntimeField,
  onItemFilterChange,
  onEventFilterChange,
  mineOnly = false,
  onMineChange,
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
  itemFilterConfig?: ResolvedCollectionConfig<BookingConfiguration>;
  eventFilterConfig?: ResolvedCollectionConfig<BookingListDocument>;
  itemFilterExpression?: FilterExpression<BookingConfiguration> | null;
  eventFilterExpression?: FilterExpression<BookingListDocument> | null;
  itemFilterIssue?: CalendarFilterIssueState;
  eventFilterIssue?: CalendarFilterIssueState;
  itemRuntimeFieldDefinitions?: readonly {
    namespace: string;
    definitions: readonly RuntimeFieldDefinition[];
  }[];
  eventRuntimeFieldDefinitions?: readonly {
    namespace: string;
    definitions: readonly RuntimeFieldDefinition[];
  }[];
  itemRuntimeFieldAuthScope?: string | number;
  eventRuntimeFieldAuthScope?: string | number;
  onSelectItemRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  onSelectEventRuntimeField?: (namespace: string, definition: RuntimeFieldDefinition) => void;
  onItemFilterChange?: (expression: FilterExpression<BookingConfiguration> | null) => void;
  onEventFilterChange?: (expression: FilterExpression<BookingListDocument> | null) => void;
  mineOnly?: boolean;
  onMineChange?: (mineOnly: boolean) => void;
  creationDisabled?: boolean;
  onResourceRangeSelect?: (
    resource: BookableItemOption,
    range: { startMinute: number; endMinute: number },
    trigger: HTMLElement,
  ) => void;
}) {
  const { t } = useTranslation("booking");
  const todayValue = todayInTimeZone(timezone);
  const [activeFilterPanel, setActiveFilterPanel] = React.useState<CalendarFilterPanelKind | null>(null);
  const eventConfig = eventFilterConfig ?? calendarEventFallbackConfig;
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
    (eventFiltering !== false &&
      (eventFiltering.value.search.trim() !== "" || eventFiltering.value.expression !== null));
  const changeMine = (next: boolean) => onMineChange?.(next);
  const filterControls = (
    <fieldset className="flex min-w-0 flex-wrap items-center gap-2">
      <legend className="sr-only">{t("calendar.filterGroups.legend")}</legend>
      {itemFilterConfig ? (
        <CalendarFilterButtons
          kind="items"
          expression={itemFilterExpression}
          active={activeFilterPanel === "items"}
          onClick={() => setActiveFilterPanel((current) => (current === "items" ? null : "items"))}
        />
      ) : null}
      {eventFilterConfig ? (
        <CalendarFilterButtons
          kind="events"
          expression={eventFilterExpression}
          active={activeFilterPanel === "events"}
          onClick={() => setActiveFilterPanel((current) => (current === "events" ? null : "events"))}
        />
      ) : null}
    </fieldset>
  );
  const filterPanel =
    activeFilterPanel === "items" && itemFilterConfig ? (
      <CalendarFilterPanel<BookingConfiguration>
        key={`items-${JSON.stringify(itemFilterExpression)}`}
        kind="items"
        config={itemFilterConfig}
        expression={itemFilterExpression}
        onApply={(next) => {
          onItemFilterChange?.(next);
          setActiveFilterPanel(null);
        }}
        onSelectRuntimeField={onSelectItemRuntimeField}
        runtimeFieldDefinitions={itemRuntimeFieldDefinitions}
        runtimeFieldAuthScope={itemRuntimeFieldAuthScope}
        onClose={() => setActiveFilterPanel(null)}
      />
    ) : activeFilterPanel === "events" && eventFilterConfig ? (
      <CalendarFilterPanel<BookingListDocument>
        key={`events-${JSON.stringify(eventFilterExpression)}`}
        kind="events"
        config={eventFilterConfig}
        expression={eventFilterExpression}
        onApply={(next) => {
          onEventFilterChange?.(next);
          setActiveFilterPanel(null);
        }}
        onSelectRuntimeField={onSelectEventRuntimeField}
        runtimeFieldDefinitions={eventRuntimeFieldDefinitions}
        runtimeFieldAuthScope={eventRuntimeFieldAuthScope}
        onClose={() => setActiveFilterPanel(null)}
      />
    ) : null;
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
        config={eventConfig}
        rows={events}
        getRowId={(event) => String(event.id)}
        clientSide={false}
        status={isLoading ? "refreshing" : "idle"}
        features={calendarFeatures}
        queryString={false}
        debounceSearch={true}
        hideFilterPanel
        headingClassName="text-2xl font-semibold"
        createAction={creationAction}
        filterButtons={{
          legend: t("calendar.quickFilters.legend"),
          controls: (
            <>
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
              {filterControls}
            </>
          ),
          hasChanges:
            date !== todayValue ||
            view !== "day" ||
            layout !== "resources" ||
            itemFilterExpression !== null ||
            eventFilterExpression !== null,
          buttons: [
            {
              id: "mine",
              label: t("calendar.quickFilters.mine"),
              icon: <CalendarCheck2Icon aria-hidden="true" />,
              pressed: mineOnly,
              onClick: () => changeMine(!mineOnly),
            },
          ],
          onReset: () => {
            setActiveFilterPanel(null);
            changeMine(false);
            onControlsReset();
          },
        }}
        renderRowsWhenEmpty
        renderRows={(calendarEvents) => (
          <>
            {itemFilterIssue ? <CalendarFilterIssue {...itemFilterIssue} /> : null}
            {eventFilterIssue ? <CalendarFilterIssue {...eventFilterIssue} /> : null}
            {filterPanel}
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
                    return (
                      <CalendarResourceSchedule
                        date={date}
                        view={view}
                        events={calendarEvents}
                        blockingEvents={blockingEvents}
                        resources={resources}
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
                  }}
                />
              ) : (
                <CalendarResourceSchedule
                  date={date}
                  view={view}
                  events={calendarEvents}
                  blockingEvents={blockingEvents}
                  resources={resources}
                  timezone={timezone}
                  today={todayValue}
                  availabilityStartMinute={availabilityStartMinute}
                  availabilityEndMinute={availabilityEndMinute}
                  resourceConfigurations={resourceConfigurations}
                  creationDisabled={creationDisabled}
                  isLoading={isLoading}
                  onResourceRangeSelect={onResourceRangeSelect}
                />
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
          </>
        )}
      />
    </main>
  );
}
