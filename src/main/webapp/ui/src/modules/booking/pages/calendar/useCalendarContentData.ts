import { useLocation, useNavigate, useSearch } from "@tanstack/react-router";
import { parseAsString, useQueryState } from "nuqs";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig, SearchSelector } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/api-v2/filterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/api-v2/queries";
import { useTableList } from "@/modules/common/table-list/hooks/state";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { FilterExpression } from "@/modules/common/table-list/types";
import { type BookingConfiguration, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { type CalendarLayout, type CalendarView, calendarDates } from "./BookingEventsCalendar";
import type { CalendarFilterPanelKind } from "./CalendarFilterPanels";
import { calendarEventSourceConfig } from "./calendarEventSourceConfig";
import { useCalendarEvents } from "./calendarEvents";

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

function targetFilter(target: string | undefined) {
  if (!target) return null;
  return {
    kind: "comparison",
    field: "target",
    operator: "equals",
    value: target,
  } as const;
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
  const { t } = useTranslation("booking");
  const { date, target } = useSearch({ from: "/booking/calendar/" });
  const navigate = useNavigate({ from: "/booking/calendar/" });
  const location = useLocation();
  const [calendarSearch, setCalendarSearch] = useQueryState("calendar-resources.q", calendarSearchParser);
  const [itemWhere, setItemWhere] = useQueryState("calendar-resources.where", calendarWhereParser);
  const [eventWhere, setEventWhere] = useQueryState("calendar-events.where", calendarWhereParser);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const [view, setView] = React.useState<CalendarView>("day");
  const [layout, setLayout] = React.useState<CalendarLayout>("resources");
  const [resettingControls, setResettingControls] = React.useState(false);
  const [mineOnly, setMineOnly] = React.useState(false);
  const [myItemsOnly, setMyItemsOnly] = React.useState(false);
  const preferences = useBookingDisplayPreferences();
  const beginCreation = useBookingCreationStore((state) => state.beginCreation);
  const creationActive = useBookingCreationStore((state) => state.activeCreation !== null);
  const selectedDate = date ?? todayInTimeZone(preferences.timeZone);
  const dates = calendarDates(selectedDate, view);
  const start = zonedDayBounds(dates[0], preferences.timeZone).start;
  const end = zonedDayBounds(dates.at(-1) ?? dates[0], preferences.timeZone).end;
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
      localFields: calendarEventSourceConfig.fields
        .filter((field) => field.name !== "target")
        .map((field) => field.name),
      translate: (key) => String(t(key as never)),
    });
    return resolveCollectionConfig({
      ...enriched,
      fields: enriched.fields
        .filter((field) => {
          const name = String(field.name);
          if (name === "id") return true;
          if (name === "requesterId") return false;
          return field.capabilities.filterOperators.length > 0;
        })
        .map((field) =>
          field.name === "id" ? { ...field, capabilities: { ...field.capabilities, filterOperators: [] } } : field,
        ),
      relationshipSources: bookingRelationshipSources,
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
  const itemIssue = filterIssue({
    kind: "items",
    raw: itemWhere,
    expression: itemExpression,
    runtimeFields: itemRuntimeFields,
    onReset: () => void setItemWhere(null),
  });
  const eventIssue = filterIssue({
    kind: "events",
    raw: eventWhere,
    expression: eventExpression,
    runtimeFields: eventRuntimeFields,
    onReset: () => void setEventWhere(null),
  });
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
  const targetItemExpression = React.useMemo(() => targetFilter(target), [target]);
  const targetEventExpression = React.useMemo(() => targetFilter(target), [target]);
  const calendarEventExpression = React.useMemo(
    () =>
      andFilters<BookingListDocument>([
        scopedEventExpression,
        targetEventExpression,
        itemExpression === null ? null : retypeFilter<BookingConfiguration, BookingListDocument>(itemExpression),
      ]),
    [itemExpression, scopedEventExpression, targetEventExpression],
  );
  const resourceItemExpression = React.useMemo(
    () => andFilters([itemExpression, targetItemExpression]),
    [itemExpression, targetItemExpression],
  );
  const itemWhereParameter = resourceItemExpression ? serializeRsqlExpression(resourceItemExpression) : undefined;
  const eventWhereParameter = scopedEventExpression ? serializeRsqlExpression(scopedEventExpression) : undefined;
  const calendarEventWhereParameter = calendarEventExpression
    ? serializeRsqlExpression(calendarEventExpression)
    : undefined;
  const filterScopeSignature = [
    calendarSearch,
    target ?? "",
    itemWhereParameter ?? "",
    eventWhereParameter ?? "",
    start,
    end,
    mineOnly,
    myItemsOnly,
  ].join("|");
  const previousFilterScope = React.useRef(filterScopeSignature);
  const resourceScopeReady = previousFilterScope.current === filterScopeSignature;
  const calendarQueryReady = !resettingControls && !filtersBlocked && resourceScopeReady;
  const resourceTable = useTableList<BookingConfiguration>({
    config: itemConfig,
    dataSource: {
      type: "remote",
      queryKey: (state) => [
        "api-v2",
        "bookings",
        "booking-catalogue",
        "calendar",
        token,
        currentUser.id,
        state,
        start,
        end,
        itemWhereParameter,
        eventWhereParameter,
        calendarSearch,
        myItemsOnly,
        target,
        filtersBlocked,
      ],
      keepPreviousData: true,
      dataScope: currentUser.id,
      enabled: calendarQueryReady && token.length > 0,
      fetch: async (state, { signal }) => {
        const result = await fetchBookingCatalogue(
          {
            q: calendarSearch,
            where: itemWhereParameter,
            start,
            end,
            eventWhere: eventWhereParameter,
            mine: myItemsOnly,
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
  const resourceTableReady =
    resourceTable.tableProps.status !== "loading" && resourceTable.tableProps.status !== "refreshing";
  const events = useCalendarEvents({
    firstDate: dates[0],
    lastDate: dates.at(-1) ?? dates[0],
    timezone: preferences.timeZone,
    token,
    targetGlobalIds: layout === "resources" ? resourceTargetIds : undefined,
    enabled: calendarQueryReady && (layout !== "resources" || resourceTableReady),
    authScope: currentUser.id,
    scope: { where: calendarEventWhereParameter, q: calendarSearch, mine: myItemsOnly },
  });
  // Display filters must not change the events used to propose a free booking window.
  // Without an event filter/Search this uses the display query's cache, avoiding a second request.
  const blockingEvents = useCalendarEvents({
    firstDate: dates[0],
    lastDate: dates.at(-1) ?? dates[0],
    timezone: preferences.timeZone,
    token,
    targetGlobalIds: resourceTargetIds,
    enabled: layout === "resources" && view === "day" && calendarQueryReady && resourceTableReady,
    authScope: currentUser.id,
    scope: { where: itemWhereParameter, q: "" },
  });
  const displayReady = calendarQueryReady;
  const resourceTargets = resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target] : []));
  const onEventFiltersChange = (expression: FilterExpression<BookingListDocument> | null) => {
    void setEventWhere(expression ? serializeRsqlExpression(expression) : null);
  };
  const onSearchChange = (search: string) => {
    void setCalendarSearch(search || null);
  };

  return {
    navigate,
    calendarSearch,
    setCalendarSearch,
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
    eventExpression,
    eventIssue,
    eventRuntimeFields,
    filtersBlocked,
    itemExpression,
    itemIssue,
    itemRuntimeFields,
    mineOnly,
    onEventFiltersChange,
    onSearchChange,
    setEventWhere,
    setItemWhere,
    setMineOnly,
    location,
    myItemsOnly,
    setMyItemsOnly,
    targetItemExpression,
  };
}
