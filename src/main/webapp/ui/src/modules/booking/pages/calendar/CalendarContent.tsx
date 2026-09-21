import { useNavigate, useSearch } from "@tanstack/react-router";
import { parseAsString, useQueryState } from "nuqs";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingCreationButtonGroup } from "@/modules/booking/creation/BookingCreationButtonGroup";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { dayMinuteToZonedTime, wallClockDraftFromInstants, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig, SearchSelector } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/adapters/apiV2/apiV2FilterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/adapters/apiV2/useApiV2RuntimeFields";
import {
  parseRsqlExpression,
  rsqlSelectors,
  serializeRsqlExpression,
} from "@/modules/common/table-list/rsql/rsqlCodec";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { type BookingConfiguration, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { BookingEventsCalendar, type CalendarLayout, type CalendarView, calendarDates } from "./BookingEventsCalendar";
import type { CalendarFilterPanelKind } from "./CalendarFilterPanels";
import { useCalendarEvents } from "./calendarEvents";

const calendarResourceSourceConfig = {
  ...bookingConfigurationConfig,
  slug: "calendar-resources",
  defaultColumns: ["target"],
  pagination: { defaultLimit: 20, limits: [10, 20, 30, 40, 50] },
} satisfies CollectionConfig<BookingConfiguration>;

const calendarEventSourceConfig = {
  slug: "booking-events-calendar",
  idField: "id",
  useAsTitle: "purpose",
  defaultColumns: ["purpose"],
  listSearchableFields: ["purpose"],
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
    {
      name: "requesterId",
      type: "number",
      labelKey: "booking:calendar.fields.requester",
      list: false,
      form: false,
      capabilities: { filterOperators: [] },
    },
    {
      name: "purpose",
      type: "text",
      maximumLength: 1_000,
      labelKey: "booking:calendar.fields.purpose",
      capabilities: {
        filterOperators: ["equals", "notEquals", "contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "bookedBy",
      type: "text",
      maximumLength: 255,
      labelKey: "booking:calendar.fields.bookedBy",
      capabilities: {
        filterOperators: ["contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "privacy",
      type: "select",
      options: ["full", "busy"],
      labelKey: "booking:calendar.fields.privacy",
      capabilities: { filterOperators: ["equals", "notEquals", "in", "notIn", "exists"] },
    },
    {
      name: "timezone",
      type: "text",
      labelKey: "booking:calendar.fields.timezone",
      capabilities: {
        filterOperators: ["equals", "notEquals", "contains", "matches", "exists"],
        supportsWildcards: true,
      },
    },
    {
      name: "start",
      type: "dateTime",
      labelKey: "booking:calendar.fields.start",
      capabilities: {
        filterOperators: [
          "equals",
          "notEquals",
          "greaterThan",
          "greaterThanOrEqual",
          "lessThan",
          "lessThanOrEqual",
          "exists",
        ],
      },
    },
    {
      name: "end",
      type: "dateTime",
      labelKey: "booking:calendar.fields.end",
      capabilities: {
        filterOperators: [
          "equals",
          "notEquals",
          "greaterThan",
          "greaterThanOrEqual",
          "lessThan",
          "lessThanOrEqual",
          "exists",
        ],
      },
    },
    {
      name: "kind",
      type: "select",
      options: ["BOOKING", "MAINTENANCE"],
      labelKey: "booking:bookableItemDetails.events.kind",
      capabilities: { filterOperators: ["equals", "notEquals", "in", "notIn", "exists"] },
      list: false,
      form: false,
    },
    {
      name: "canEdit",
      type: "boolean",
      labelKey: "booking:calendar.fields.editable",
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
    {
      name: "createdAt",
      type: "dateTime",
      labelKey: "booking:calendar.fields.createdAt",
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
    {
      name: "updatedAt",
      type: "dateTime",
      labelKey: "booking:calendar.fields.updatedAt",
      capabilities: { filterOperators: [] },
      list: false,
      form: false,
    },
  ],
} satisfies CollectionConfig<BookingListDocument>;

const calendarSearchParser = parseAsString.withDefault("").withOptions({ history: "replace", clearOnDefault: true });
const calendarWhereParser = parseAsString.withOptions({ history: "replace", clearOnDefault: true });

function andFilters<TDocument>(
  filters: readonly (FilterExpression<TDocument> | null | undefined)[],
): FilterExpression<TDocument> | null {
  const children = filters.flatMap((filter) =>
    filter === null || filter === undefined ? [] : filter.kind === "and" ? filter.children : [filter],
  );
  if (children.length === 0) return null;
  if (children.length === 1) return children[0];
  return { kind: "and", children };
}

function retypeFilter<TFrom, TTo>(expression: FilterExpression<TFrom>): FilterExpression<TTo> {
  if (expression.kind === "comparison") {
    return { ...expression, field: String(expression.field) as SearchSelector<TTo> };
  }
  return {
    kind: expression.kind,
    children: expression.children.map((child) => retypeFilter<TFrom, TTo>(child)),
  };
}

function filterIssue<TDocument>(
  kind: CalendarFilterPanelKind,
  raw: string | null,
  expression: FilterExpression<TDocument> | null,
  pending: boolean,
  error: unknown,
  missing: readonly string[],
  retry: () => Promise<unknown>,
  onReset: () => void,
) {
  if (pending) return undefined;
  if (error !== null) {
    return {
      kind,
      encoded: raw ?? "",
      network: true,
      onRetry: () => void retry(),
      onReset,
    } as const;
  }
  if (missing.length > 0 || (raw !== null && raw.trim() !== "" && expression === null)) {
    return { kind, encoded: raw ?? "", network: false, onReset } as const;
  }
  return undefined;
}

export function CalendarContent() {
  const { t } = useTranslation("booking");
  const { date } = useSearch({ from: "/booking/calendar" });
  const navigate = useNavigate({ from: "/booking/calendar" });
  const [calendarSearch, setCalendarSearch] = useQueryState("calendar-resources.q", calendarSearchParser);
  const [itemWhere, setItemWhere] = useQueryState("calendar-resources.where", calendarWhereParser);
  const [eventWhere, setEventWhere] = useQueryState("calendar-events.where", calendarWhereParser);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const [view, setView] = React.useState<CalendarView>("day");
  const [layout, setLayout] = React.useState<CalendarLayout>("resources");
  const [resettingControls, setResettingControls] = React.useState(false);
  const [mineOnly, setMineOnly] = React.useState(false);
  const preferences = useBookingDisplayPreferences();
  const beginCreation = useBookingCreationStore((state) => state.beginCreation);
  const creationActive = useBookingCreationStore((state) => state.activeCreation !== null);
  const selectedDate = date ?? todayInTimeZone(preferences.timeZone);
  const dates = calendarDates(selectedDate, view);
  const calendarStart = zonedDayBounds(dates[0], preferences.timeZone).start;
  const calendarEnd = zonedDayBounds(dates.at(-1) ?? dates[0], preferences.timeZone).end;
  const itemRuntimeFields = useApiV2RuntimeFields<BookingConfiguration>({
    resourceName: "booking-configurations",
    selectors: itemWhere ? rsqlSelectors(itemWhere) : [],
    request: { token, authScope: currentUser.id },
  });
  const eventRuntimeFields = useApiV2RuntimeFields<BookingListDocument>({
    resourceName: "bookings",
    selectors: eventWhere ? rsqlSelectors(eventWhere) : [],
    request: { token, authScope: currentUser.id },
  });
  const itemConfig = React.useMemo(() => {
    const enriched = enrichApiV2FilterConfig({
      config: calendarResourceSourceConfig,
      metadata: itemRuntimeFields.metadata,
      runtimeFields: itemRuntimeFields.runtimeFields,
      translate: (key) => String(t(key as never)),
    });
    return resolveCollectionConfig({
      ...enriched,
      fields: enriched.fields
        .filter((field) => field.name === "id" || field.name === "target" || String(field.name).startsWith("target."))
        .map((field) =>
          field.name === "id" ? { ...field, capabilities: { ...field.capabilities, filterOperators: [] } } : field,
        ),
    } as CollectionConfig<BookingConfiguration>);
  }, [itemRuntimeFields.metadata, itemRuntimeFields.runtimeFields, t]);
  const eventConfig = React.useMemo(() => {
    const enriched = enrichApiV2FilterConfig({
      config: calendarEventSourceConfig,
      metadata: eventRuntimeFields.metadata,
      runtimeFields: eventRuntimeFields.runtimeFields,
      localFields: [
        "id",
        "target",
        "requesterId",
        "purpose",
        "bookedBy",
        "privacy",
        "timezone",
        "start",
        "end",
        "kind",
        "canEdit",
        "createdAt",
        "updatedAt",
      ],
      translate: (key) => String(t(key as never)),
    });
    return resolveCollectionConfig({
      ...enriched,
      // Event target identity and properties belong to the Bookable items group. Search still
      // handles target.name/globalId through the source relationship.
      fields: enriched.fields
        .filter((field) => {
          const name = String(field.name);
          if (name === "id") return true;
          if (name === "target" || name.startsWith("target.")) return false;
          if (field.origin?.kind === "runtimeField") return false;
          if (name === "requesterId") return false;
          return field.capabilities.filterOperators.length > 0;
        })
        .map((field) =>
          field.name === "id" ? { ...field, capabilities: { ...field.capabilities, filterOperators: [] } } : field,
        ),
      runtimeSources: [],
      listSearchableFields: ["purpose"],
    } as CollectionConfig<BookingListDocument>);
  }, [eventRuntimeFields.metadata, eventRuntimeFields.runtimeFields, t]);
  const itemExpression = React.useMemo(
    () => (itemWhere && itemWhere.trim() !== "" ? parseRsqlExpression(itemWhere, itemConfig) : null),
    [itemConfig, itemWhere],
  );
  const eventExpression = React.useMemo(
    () => (eventWhere && eventWhere.trim() !== "" ? parseRsqlExpression(eventWhere, eventConfig) : null),
    [eventConfig, eventWhere],
  );
  const itemIssue = filterIssue(
    "items",
    itemWhere,
    itemExpression,
    itemRuntimeFields.pending,
    itemRuntimeFields.error,
    itemRuntimeFields.missing,
    itemRuntimeFields.retry,
    () => void setItemWhere(null),
  );
  const eventIssue = filterIssue(
    "events",
    eventWhere,
    eventExpression,
    eventRuntimeFields.pending,
    eventRuntimeFields.error,
    eventRuntimeFields.missing,
    eventRuntimeFields.retry,
    () => void setEventWhere(null),
  );
  const filtersBlocked =
    itemIssue !== undefined || eventIssue !== undefined || itemRuntimeFields.pending || eventRuntimeFields.pending;
  const requesterFilter = mineOnly
    ? ({
        kind: "comparison",
        field: "requesterId",
        operator: "equals",
        value: currentUser.id,
      } satisfies FilterExpression<BookingListDocument>)
    : null;
  const scopedEventExpression = React.useMemo(
    () => andFilters([eventExpression, requesterFilter]),
    [eventExpression, requesterFilter],
  );
  const calendarEventExpression = React.useMemo(
    () =>
      andFilters<BookingListDocument>([
        scopedEventExpression,
        itemExpression === null ? null : retypeFilter<BookingConfiguration, BookingListDocument>(itemExpression),
      ]),
    [itemExpression, scopedEventExpression],
  );
  const itemWhereParameter = itemExpression ? serializeRsqlExpression(itemExpression) : undefined;
  const eventWhereParameter = scopedEventExpression ? serializeRsqlExpression(scopedEventExpression) : undefined;
  const calendarEventWhereParameter = calendarEventExpression
    ? serializeRsqlExpression(calendarEventExpression)
    : undefined;
  const filterScopeSignature = [
    calendarSearch,
    itemWhereParameter ?? "",
    eventWhereParameter ?? "",
    calendarStart,
    calendarEnd,
    mineOnly,
  ].join("|");
  const previousFilterScope = React.useRef(filterScopeSignature);
  const resourceScopeReady = previousFilterScope.current === filterScopeSignature;
  const resourceTable = useTableList<BookingConfiguration>({
    config: itemConfig,
    dataSource: {
      type: "remote",
      queryKey: (state) => [
        "api-v2",
        "booking-catalogue",
        "calendar",
        token,
        currentUser.id,
        state,
        calendarStart,
        calendarEnd,
        itemWhereParameter,
        eventWhereParameter,
        calendarSearch,
        filtersBlocked,
      ],
      keepPreviousData: true,
      dataScope: currentUser.id,
      enabled: !resettingControls && !filtersBlocked && resourceScopeReady && token.length > 0,
      fetch: async (state, { signal }) => {
        const result = await fetchBookingCatalogue(
          {
            q: calendarSearch,
            where: itemWhereParameter,
            calendarStart,
            calendarEnd,
            eventWhere: eventWhereParameter,
            page: state.page.pageIndex + 1,
            pageSize: state.page.pageSize,
          },
          token,
          signal,
        );
        return {
          rows: result.items.map(catalogueItemAsConfiguration),
          rowCount: result.total,
        };
      },
    },
    initialState: { filters: { search: calendarSearch, expression: itemExpression }, visibleFields: ["target"] },
    features: { sorting: false, columns: false },
    queryString: false,
  });
  React.useEffect(() => {
    if (previousFilterScope.current === filterScopeSignature) return;
    previousFilterScope.current = filterScopeSignature;
    resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
  }, [filterScopeSignature, resourceTable.setPage, resourceTable.state.page]);
  const resourceConfigurations = React.useMemo(
    () =>
      resourceTable.tableProps.rows.flatMap((row) => {
        if (!row.capabilities.canCreateBooking && !row.capabilities.canEditConfiguration) return [];
        const option = bookableItemOption(row);
        return option ? [option] : [];
      }),
    [resourceTable.tableProps.rows],
  );
  const resourceTargetIds = React.useMemo(
    () => resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target.globalId] : [])),
    [resourceTable.tableProps.rows],
  );
  const events = useCalendarEvents(
    dates[0],
    dates.at(-1) ?? dates[0],
    preferences.timeZone,
    token,
    layout === "resources" ? resourceTargetIds : undefined,
    !resettingControls &&
      !filtersBlocked &&
      resourceScopeReady &&
      (layout !== "resources" ||
        (resourceTable.tableProps.status !== "loading" && resourceTable.tableProps.status !== "refreshing")),
    currentUser.id,
    { where: calendarEventWhereParameter, q: calendarSearch },
  );
  // Display filters must not change the events used to propose a free booking window.
  // Without an event filter/Search this uses the display query's cache, avoiding a second request.
  const blockingEvents = useCalendarEvents(
    dates[0],
    dates.at(-1) ?? dates[0],
    preferences.timeZone,
    token,
    resourceTargetIds,
    layout === "resources" &&
      view === "day" &&
      !resettingControls &&
      !filtersBlocked &&
      resourceScopeReady &&
      resourceTable.tableProps.status !== "loading" &&
      resourceTable.tableProps.status !== "refreshing",
    currentUser.id,
    { where: itemWhereParameter, q: "" },
  );
  const displayReady = !filtersBlocked && resourceScopeReady && !resettingControls;
  const resourceTargets = resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target] : []));
  const onItemFiltersChange = (expression: FilterExpression<BookingConfiguration> | null) => {
    void setItemWhere(expression ? serializeRsqlExpression(expression) : null);
  };
  const onEventFiltersChange = (expression: FilterExpression<BookingListDocument> | null) => {
    void setEventWhere(expression ? serializeRsqlExpression(expression) : null);
  };
  const onSearchChange = (search: string) => {
    void setCalendarSearch(search || null);
  };

  return (
    <BookingEventsCalendar
      date={selectedDate}
      view={view}
      layout={layout}
      timezone={preferences.timeZone}
      availabilityStartMinute={preferences.availabilityWindow.startMinute}
      availabilityEndMinute={preferences.availabilityWindow.endMinute}
      events={displayReady ? (events.data ?? []) : []}
      blockingEvents={blockingEvents.data ?? []}
      availabilityError={layout === "resources" && view === "day" && blockingEvents.isError}
      onRetryAvailability={() => void blockingEvents.refetch()}
      resources={filtersBlocked ? [] : resourceTargets}
      resourceConfigurations={resourceConfigurations}
      resourceTableProps={
        filtersBlocked ? { ...resourceTable.tableProps, rows: [], status: "idle" } : resourceTable.tableProps
      }
      itemFilterConfig={itemConfig}
      eventFilterConfig={eventConfig}
      itemFilterExpression={itemExpression}
      eventFilterExpression={eventExpression}
      itemFilterIssue={itemIssue}
      eventFilterIssue={eventIssue}
      itemRuntimeFieldDefinitions={itemRuntimeFields.runtimeFields}
      eventRuntimeFieldDefinitions={eventRuntimeFields.runtimeFields}
      itemRuntimeFieldAuthScope={itemRuntimeFields.scope}
      eventRuntimeFieldAuthScope={eventRuntimeFields.scope}
      onSelectItemRuntimeField={itemRuntimeFields.selectRuntimeField}
      onSelectEventRuntimeField={eventRuntimeFields.selectRuntimeField}
      onItemFilterChange={onItemFiltersChange}
      onEventFilterChange={onEventFiltersChange}
      searchControl={{
        value: calendarSearch,
        onChange: onSearchChange,
      }}
      currentUserId={currentUser.id}
      mineOnly={mineOnly}
      onMineChange={(next) => {
        setMineOnly(next);
        resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
      }}
      isLoading={
        itemRuntimeFields.pending ||
        eventRuntimeFields.pending ||
        (displayReady && events.isPending && resourceTable.tableProps.status !== "error")
      }
      isError={events.isError || (layout === "resources" && resourceTable.tableProps.status === "error")}
      onRetry={() => {
        if (layout === "resources" && resourceTable.tableProps.status === "error") {
          void resourceTable.refetch();
        } else {
          void events.refetch();
        }
      }}
      onDateChange={(nextDate) =>
        void navigate({ search: (current) => ({ ...current, date: nextDate }), replace: true })
      }
      onControlsReset={async () => {
        // Date navigation is asynchronous; avoid fetching an intermediate date/period combination.
        setResettingControls(true);
        try {
          await navigate({ search: (current) => ({ ...current, date: undefined }), replace: true });
          await Promise.all([setCalendarSearch(null), setItemWhere(null), setEventWhere(null)]);
          setMineOnly(false);
          resourceTable.setPage({ ...resourceTable.state.page, pageIndex: 0 });
          React.startTransition(() => {
            setView("day");
            setLayout("resources");
          });
        } finally {
          React.startTransition(() => setResettingControls(false));
        }
      }}
      onViewChange={setView}
      onLayoutChange={setLayout}
      creationAction={
        <BookingCreationButtonGroup ownerId="calendar-toolbar" initialDate={selectedDate} size="default" />
      }
      creationDisabled={
        creationActive || !displayReady || (view === "day" && (blockingEvents.isPending || blockingEvents.isError))
      }
      onResourceRangeSelect={(resource, range, trigger) => {
        const window = wallClockDraftFromInstants(
          dayMinuteToZonedTime(selectedDate, preferences.timeZone, range.startMinute).toInstant().toString(),
          dayMinuteToZonedTime(selectedDate, preferences.timeZone, range.endMinute).toInstant().toString(),
          preferences.timeZone,
        );
        const ownerId = `calendar-resource-${resource.configurationId}`;
        const triggerId = `${ownerId}-${selectedDate}`;
        trigger.id = triggerId;
        beginCreation({
          ownerId,
          triggerId,
          eventKind: "BOOKING",
          target: resource,
          initialDate: selectedDate,
          window,
          lockTarget: true,
          timelineAdjustable: trigger.matches('[data-testid="day-timeline-scroller"]'),
        });
      }}
    />
  );
}
