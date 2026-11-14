import { useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { parseAsString, useQueryState } from "nuqs";
import * as React from "react";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { calendarBookingCatalogueQuery, catalogueItemAsConfiguration } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import type { CollectionConfig, FieldName, SearchSelector } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/api-v2/filterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/api-v2/queries";
import { useTableList } from "@/modules/common/table-list/hooks/state";
import { dropInvalidMeRules, hasPreset, togglePreset, withoutPresets } from "@/modules/common/table-list/presets";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { CollectionQueryState, FilterExpression } from "@/modules/common/table-list/types";
import { type BookingConfiguration, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { ownerPreset, ownerPresetField, requesterPreset } from "../ownerPreset";
import { type CalendarLayout, type CalendarView, calendarDates } from "./BookingEventsCalendar";
import type { CalendarFilterPanelKind } from "./CalendarFilterPanels";
import { calendarEventSourceConfig } from "./calendarEventSourceConfig";
import { useCalendarEvents } from "./calendarEvents";

const eventOwnerPreset = { ...ownerPreset, field: "targetOwner" as SearchSelector<BookingListDocument> };
const eventOwnerField = { ...ownerPresetField, name: "targetOwner" as FieldName<BookingListDocument> };

const calendarResourceSourceConfig = {
  ...bookingConfigurationConfig,
  slug: "calendar-resources",
  defaultColumns: ["target"],
  pagination: { defaultLimit: 20, limits: [10, 20, 30, 40, 50] },
} satisfies CollectionConfig<BookingConfiguration>;

const calendarSearchOptions = { history: "replace", clearOnDefault: true } as const;
const calendarSearchParser = parseAsString.withDefault("").withOptions(calendarSearchOptions);
const calendarWhereParser = parseAsString.withOptions(calendarSearchOptions);

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

function filterIssue<TDocument>({
  kind,
  raw,
  expression,
  runtimeFields,
  onReset,
}: {
  kind: CalendarFilterPanelKind;
  raw: string | null;
  expression: FilterExpression<TDocument> | null;
  runtimeFields: {
    pending: boolean;
    error: unknown;
    missing: readonly string[];
    retry: () => Promise<unknown>;
  };
  onReset: () => void;
}) {
  const { pending, error, missing, retry } = runtimeFields;
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

export function useCalendarContentData() {
  const queryClient = useQueryClient();
  const { date } = useSearch({ from: "/booking/calendar/" });
  const navigate = useNavigate({ from: "/booking/calendar/" });
  const [calendarSearch, setCalendarSearch] = useQueryState("calendar-resources.q", calendarSearchParser);
  const [itemWhere, setItemWhere] = useQueryState("calendar-resources.where", calendarWhereParser);
  const [eventWhere, setEventWhere] = useQueryState("calendar-events.where", calendarWhereParser);
  const { data: currentUser } = useCurrentUserQuery();
  const [view, setView] = React.useState<CalendarView>("day");
  const [layout, setLayout] = React.useState<CalendarLayout>("resources");
  const [resettingControls, setResettingControls] = React.useState(false);
  const preferences = useBookingDisplayPreferences();
  const beginCreation = useBookingCreationStore((state) => state.beginCreation);
  const creationActive = useBookingCreationStore((state) => state.activeCreation !== null);
  const selectedDate = date ?? todayInTimeZone(preferences.timeZone);
  const dates = calendarDates(selectedDate, view);
  const start = zonedDayBounds(dates[0], preferences.timeZone).start;
  const end = zonedDayBounds(dates.at(-1) ?? dates[0], preferences.timeZone).end;
  const itemRuntimeFields = useApiV2RuntimeFields<
    BookingConfiguration,
    (typeof apiV2Resources)["booking-configurations"]
  >({
    resource: apiV2Resources["booking-configurations"],
    selectors: itemWhere ? rsqlSelectors(itemWhere) : [],
    request: { authScope: currentUser.id },
  });
  const eventRuntimeFields = useApiV2RuntimeFields<BookingListDocument, typeof apiV2Resources.bookings>({
    resource: apiV2Resources.bookings,
    selectors: eventWhere ? rsqlSelectors(eventWhere) : [],
    request: { authScope: currentUser.id },
  });
  const itemConfig = React.useMemo(() => {
    const enriched = enrichApiV2FilterConfig({
      config: { ...calendarResourceSourceConfig, fields: [...calendarResourceSourceConfig.fields, ownerPresetField] },
      localFields: [ownerPresetField.name],
      metadata: itemRuntimeFields.metadata,
      runtimeFields: itemRuntimeFields.runtimeFields,
    });
    return resolveCollectionConfig({
      ...enriched,
      fields: enriched.fields
        .filter(
          (field) =>
            field.name === "id" ||
            field.name === "target" ||
            String(field.name) === "targetOwner" ||
            String(field.name).startsWith("target."),
        )
        .map((field) =>
          field.name === "id" ? { ...field, capabilities: { ...field.capabilities, filterOperators: [] } } : field,
        ),
    } as CollectionConfig<BookingConfiguration>);
  }, [itemRuntimeFields.metadata, itemRuntimeFields.runtimeFields]);
  const eventConfig = React.useMemo(() => {
    const enriched = enrichApiV2FilterConfig({
      config: { ...calendarEventSourceConfig, fields: [...calendarEventSourceConfig.fields, eventOwnerField] },
      metadata: eventRuntimeFields.metadata,
      runtimeFields: eventRuntimeFields.runtimeFields,
      localFields: [...calendarEventSourceConfig.fields.map((field) => field.name), eventOwnerField.name],
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

          return field.capabilities.filterOperators.length > 0;
        })
        .map((field) =>
          field.name === "id" ? { ...field, capabilities: { ...field.capabilities, filterOperators: [] } } : field,
        ),
      runtimeSources: [],
      listSearchableFields: ["purpose"],
    } as CollectionConfig<BookingListDocument>);
  }, [eventRuntimeFields.metadata, eventRuntimeFields.runtimeFields]);
  const parsedItems = React.useMemo(
    () => dropInvalidMeRules(itemWhere ? parseRsqlExpression(itemWhere, itemConfig) : null, ["targetOwner"]),
    [itemConfig, itemWhere],
  );
  const parsedEvents = React.useMemo(
    () =>
      dropInvalidMeRules(eventWhere ? parseRsqlExpression(eventWhere, eventConfig) : null, [
        "requester.value",
        "targetOwner",
      ]),
    [eventConfig, eventWhere],
  );
  const itemExpression = parsedItems.expression;
  const eventExpression = parsedEvents.expression;
  const eventPanelExpression = andFilters<BookingListDocument>([
    eventExpression,
    hasPreset(itemExpression, ownerPreset) ? eventOwnerPreset : null,
  ]);
  const droppedFilterFields = [...parsedItems.dropped, ...parsedEvents.dropped];
  const mineOnly = hasPreset(eventExpression, requesterPreset);
  const myItemsOnly = hasPreset(itemExpression, ownerPreset);
  const setMineOnly = (next: boolean) => {
    if (next === mineOnly) return;
    const expression = togglePreset(eventExpression, requesterPreset);
    void setEventWhere(expression ? serializeRsqlExpression(expression) : null);
  };
  const setMyItemsOnly = (next: boolean) => {
    if (next === myItemsOnly) return;
    const expression = togglePreset(itemExpression, ownerPreset);
    void setItemWhere(expression ? serializeRsqlExpression(expression) : null);
  };
  const itemIssue = filterIssue({
    kind: "items",
    raw: parsedItems.dropped.length > 0 ? null : itemWhere,
    expression: itemExpression,
    runtimeFields: itemRuntimeFields,
    onReset: () => void setItemWhere(null),
  });
  const eventIssue = filterIssue({
    kind: "events",
    raw: parsedEvents.dropped.length > 0 ? null : eventWhere,
    expression: eventExpression,
    runtimeFields: eventRuntimeFields,
    onReset: () => void setEventWhere(null),
  });
  const filtersBlocked =
    itemIssue !== undefined || eventIssue !== undefined || itemRuntimeFields.pending || eventRuntimeFields.pending;
  const scopedEventExpression = eventExpression;
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
    start,
    end,
    mineOnly,
  ].join("|");
  const previousFilterScope = React.useRef(filterScopeSignature);
  const resourceScopeReady = previousFilterScope.current === filterScopeSignature;
  const calendarQueryReady = !resettingControls && !filtersBlocked && resourceScopeReady;
  const calendarCatalogueOptions = (state: CollectionQueryState<BookingConfiguration>) =>
    calendarBookingCatalogueQuery(undefined, state, {
      q: calendarSearch,
      where: itemWhereParameter,
      start,
      end,
      eventWhere: eventWhereParameter,
      page: state.page.pageIndex + 1,
      pageSize: state.page.pageSize,
    });
  const resourceTable = useTableList<BookingConfiguration>({
    config: itemConfig,
    dataSource: {
      type: "remote",
      queryKey: (state) => [...calendarCatalogueOptions(state).queryKey, "calendarResources"],
      keepPreviousData: true,
      dataScope: currentUser.id,
      enabled: calendarQueryReady,
      fetch: async (state, { signal }) => {
        const options = calendarCatalogueOptions(state);
        if (typeof options.queryFn !== "function" || !options.select)
          throw new Error("Calendar catalogue options require a query and mapper");
        const page = await options.queryFn({
          signal,
          client: queryClient,
          queryKey: options.queryKey,
          meta: options.meta,
        });
        const result = options.select(page);
        return { rows: result.items.map(catalogueItemAsConfiguration), rowCount: result.total };
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
  const resourceTableReady =
    resourceTable.tableProps.status !== "loading" && resourceTable.tableProps.status !== "refreshing";
  const events = useCalendarEvents({
    firstDate: dates[0],
    lastDate: dates.at(-1) ?? dates[0],
    timezone: preferences.timeZone,

    targetGlobalIds: layout === "resources" ? resourceTargetIds : undefined,
    enabled: calendarQueryReady && (layout !== "resources" || resourceTableReady),
    authScope: currentUser.id,
    scope: { where: calendarEventWhereParameter, q: calendarSearch },
  });
  // Display filters must not change the events used to propose a free booking window.
  // Without an event filter/Search this uses the display query's cache, avoiding a second request.
  const blockingEvents = useCalendarEvents({
    firstDate: dates[0],
    lastDate: dates.at(-1) ?? dates[0],
    timezone: preferences.timeZone,

    targetGlobalIds: resourceTargetIds,
    enabled: layout === "resources" && view === "day" && calendarQueryReady && resourceTableReady,
    authScope: currentUser.id,
    scope: { where: itemWhereParameter, q: "" },
  });
  const displayReady = calendarQueryReady;
  const resourceTargets = resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target] : []));
  const onItemFiltersChange = (expression: FilterExpression<BookingConfiguration> | null) => {
    void setItemWhere(expression ? serializeRsqlExpression(expression) : null);
  };
  const onEventFiltersChange = (expression: FilterExpression<BookingListDocument> | null) => {
    const events = withoutPresets(expression, [eventOwnerPreset]);
    const otherItems = withoutPresets(itemExpression, [ownerPreset]);
    const items = hasPreset(expression, eventOwnerPreset) ? togglePreset(otherItems, ownerPreset) : otherItems;
    void Promise.all([
      setEventWhere(events ? serializeRsqlExpression(events) : null),
      setItemWhere(items ? serializeRsqlExpression(items) : null),
    ]);
  };
  const onSearchChange = (search: string) => {
    void setCalendarSearch(search || null);
  };

  return {
    droppedFilterFields,
    navigate,
    calendarSearch,
    setCalendarSearch,
    currentUser,
    view,
    setView,
    layout,
    setLayout,
    setResettingControls,
    preferences,
    beginCreation,
    creationActive,
    selectedDate,
    resourceTable,
    resourceConfigurations,
    events,
    resourceTargets,
    blockingEvents,
    displayReady,
    eventConfig,
    eventExpression: eventPanelExpression,
    eventIssue,
    eventRuntimeFields,
    filtersBlocked,
    itemConfig,
    itemExpression,
    itemIssue,
    itemRuntimeFields,
    mineOnly,
    myItemsOnly,
    setMyItemsOnly,
    onEventFiltersChange,
    onItemFiltersChange,
    onSearchChange,
    setEventWhere,
    setItemWhere,
    setMineOnly,
  };
}
