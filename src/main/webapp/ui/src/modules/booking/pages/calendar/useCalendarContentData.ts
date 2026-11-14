import { useQueryClient } from "@tanstack/react-query";
import { useLocation, useNavigate, useSearch } from "@tanstack/react-router";
import { parseAsString, parseAsStringLiteral, useQueryState } from "nuqs";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { bookableItemOption } from "@/modules/booking/creation/bookableItemOption";
import { useBookingCreationStore } from "@/modules/booking/creation/bookingCreationStore";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { calendarBookingCatalogueQuery, catalogueItemAsConfiguration } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import {
  bookingInstrumentSource,
  bookingRelationshipSources,
} from "@/modules/booking/domain/bookingRelationshipSource";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import type { CollectionConfig, FieldName, SearchSelector } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useSelectedRelationshipOptions } from "@/modules/common/relationship-picker/queries";
import { enrichApiV2FilterConfig } from "@/modules/common/table-list/api-v2/filterFields";
import { useApiV2RuntimeFields } from "@/modules/common/table-list/api-v2/queries";
import { useTableList } from "@/modules/common/table-list/hooks/state";
import { dropInvalidMeRules, hasPreset, togglePreset, withoutPresets } from "@/modules/common/table-list/presets";
import { parseRsqlExpression, rsqlSelectors, serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import type { CollectionQueryState, FilterExpression } from "@/modules/common/table-list/types";
import { type BookingConfiguration, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { ownerPreset, ownerPresetField, requesterPreset } from "../ownerPreset";
import {
  type AllBookableItem,
  availabilityPresets,
  serverAvailabilityFilter,
} from "./availability/availabilityQuickFilters";
import { useCalendarAvailabilityLayout } from "./availability/useCalendarAvailabilityLayout";
import type { CalendarFilterPanelKind } from "./CalendarFilterPanels";
import { calendarEventSourceConfig } from "./calendarEventSourceConfig";
import { useCalendarEvents } from "./calendarEvents";
import { type CalendarView, calendarDates, calendarLayouts, calendarViews } from "./calendarLayoutUtils";

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
// The view and layout live in the query string, so a reload or a shared link reopens the same calendar.
const calendarViewParser = parseAsStringLiteral(calendarViews).withDefault("day").withOptions(calendarSearchOptions);
const calendarLayoutParser = parseAsStringLiteral(calendarLayouts)
  .withDefault("resources")
  .withOptions(calendarSearchOptions);

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
  const queryClient = useQueryClient();
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { date, target } = useSearch({ from: "/booking/calendar/" });
  const navigate = useNavigate({ from: "/booking/calendar/" });
  const location = useLocation();
  const replaceSearch = (key: string | ((search: URLSearchParams) => void), value?: string) => {
    const search = new URLSearchParams(location.searchStr);
    if (typeof key === "function") key(search);
    else if (value === undefined) search.delete(key);
    else search.set(key, value);
    const query = search.toString();
    void navigate({ to: query ? `${location.pathname}?${query}` : location.pathname, replace: true });
  };
  const [calendarSearch, setCalendarSearch] = useQueryState("calendar-resources.q", calendarSearchParser);
  const [itemWhere, setItemWhere] = useQueryState("calendar-resources.where", calendarWhereParser);
  const [eventWhere, setEventWhere] = useQueryState("calendar-events.where", calendarWhereParser);
  const { data: currentUser } = useCurrentUserQuery();
  const [requestedView, setView] = useQueryState("view", calendarViewParser);
  const [layout] = useQueryState("layout", calendarLayoutParser);
  // Month is not offered in Resources, so a link asking for it opens the week, as switching layouts does.
  const view: CalendarView =
    layout === "availability" ? "day" : layout === "resources" && requestedView === "month" ? "week" : requestedView;
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
    const availabilityFields =
      layout === "availability"
        ? [
            {
              name: "availability" as FieldName<BookingConfiguration>,
              type: "select" as const,
              labelKey: "booking:calendar.availability",
              list: false as const,
              form: false as const,
              options: [
                { value: "available-now", label: t("availabilityLayout.quickFilters.availableNow") },
                { value: "free-later-today", label: t("availabilityLayout.quickFilters.freeLaterToday") },
              ],
              capabilities: { sortable: false, filterOperators: ["equals" as const] },
            },
          ]
        : [];
    const enriched = enrichApiV2FilterConfig({
      config: {
        ...calendarResourceSourceConfig,
        fields: [...calendarResourceSourceConfig.fields, ownerPresetField, ...availabilityFields],
      },
      localFields: [ownerPresetField.name, "availability" as FieldName<BookingConfiguration>],
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
            // A filter-only relationship: in the collection metadata, not the configuration type.
            String(field.name) === "location" ||
            String(field.name) === "targetOwner" ||
            String(field.name) === "availability" ||
            String(field.name).startsWith("target."),
        )
        .map((field) =>
          field.name === "id" ? { ...field, capabilities: { ...field.capabilities, filterOperators: [] } } : field,
        ),
    } as CollectionConfig<BookingConfiguration>);
  }, [layout, itemRuntimeFields.metadata, itemRuntimeFields.runtimeFields, t]);
  const eventConfig = React.useMemo(() => {
    const enriched = enrichApiV2FilterConfig({
      config: { ...calendarEventSourceConfig, fields: [...calendarEventSourceConfig.fields, eventOwnerField] },
      metadata: eventRuntimeFields.metadata,
      runtimeFields: eventRuntimeFields.runtimeFields,
      localFields: [
        ...calendarEventSourceConfig.fields.filter((field) => field.name !== "target").map((field) => field.name),
        eventOwnerField.name,
      ],
    });
    return resolveCollectionConfig({
      ...enriched,
      fields: enriched.fields
        .filter((field) => {
          const name = String(field.name);
          if (name === "id") return true;
          return field.capabilities.filterOperators.length > 0;
        })
        .map((field) =>
          field.name === "id" ? { ...field, capabilities: { ...field.capabilities, filterOperators: [] } } : field,
        ),
      relationshipSources: bookingRelationshipSources,
      listSearchableFields: ["purpose"],
    } as CollectionConfig<BookingListDocument>);
  }, [eventRuntimeFields.metadata, eventRuntimeFields.runtimeFields]);
  const parsedItems = React.useMemo(() => {
    const staleFields: string[] = [];
    const parsed = itemWhere
      ? parseRsqlExpression(itemWhere, itemConfig, {
          isStale: (field) => field === "availability" && layout !== "availability",
          onDropped: (field) => staleFields.push(field),
        })
      : null;
    const recovered = dropInvalidMeRules(parsed, ["targetOwner"]);
    return { expression: recovered.expression, dropped: [...staleFields, ...recovered.dropped] };
  }, [itemConfig, itemWhere, layout]);
  const parsedEvents = React.useMemo(
    () =>
      dropInvalidMeRules(eventWhere ? parseRsqlExpression(eventWhere, eventConfig) : null, [
        "requester.value",
        "targetOwner",
      ]),
    [eventConfig, eventWhere],
  );
  const itemExpression = parsedItems.expression;
  const setLayout = async (next: typeof layout) => {
    replaceSearch((search) => {
      search.set("layout", next);
      if (next === "availability") search.set("view", "day");
      else if (next === "resources" && view === "month") search.set("view", "week");
      if (layout === "availability" && next !== "availability") {
        const remaining = withoutPresets(
          itemExpression,
          availabilityPresets
            .map((rule) => retypeFilter<AllBookableItem, BookingConfiguration>(rule))
            .filter((rule) => rule.kind === "comparison"),
        );
        if (remaining) search.set("calendar-resources.where", serializeRsqlExpression(remaining));
        else search.delete("calendar-resources.where");
      }
    });
  };
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
  const itemIssue =
    layout === "availability" && !serverAvailabilityFilter(itemExpression).supported
      ? {
          kind: "items" as const,
          encoded: itemWhere ?? "",
          network: false,
          onReset: () => void setItemWhere(null),
        }
      : filterIssue({
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
    itemIssue !== undefined ||
    (layout !== "availability" && eventIssue !== undefined) ||
    itemRuntimeFields.pending ||
    (layout !== "availability" && eventRuntimeFields.pending);
  const scopedEventExpression = eventExpression;
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
  const calendarCatalogueOptions = (state: CollectionQueryState<BookingConfiguration>) =>
    calendarBookingCatalogueQuery(undefined, state, {
      q: calendarSearch,
      totals: true,
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
      enabled: layout !== "availability" && calendarQueryReady,
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
      // Read-only rows are kept for closure shading; consumers check the capabilities before offering actions.
      resourceTable.tableProps.rows.flatMap((row) => {
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
    enabled: layout !== "availability" && calendarQueryReady && (layout !== "resources" || resourceTableReady),
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
  const availabilityLayout = useCalendarAvailabilityLayout({
    expression: resourceItemExpression,
    q: calendarSearch,
    date: selectedDate,

    subjectId: currentUser.id,
    enabled: layout === "availability" && calendarQueryReady,
  });
  const displayReady = calendarQueryReady;
  const resourceTargets = resourceTable.tableProps.rows.flatMap((row) => (row.target ? [row.target] : []));
  const targetOptionLabels = React.useMemo(
    () => ({
      unavailableLabel: (value: string) => commonT("relationshipPicker.unavailable", { value }),
      failedLabel: () => commonT("relationshipPicker.restoreFailed"),
    }),
    [commonT],
  );
  // Search and the filters can hide the item from the loaded rows and events, so resolve its name on its own,
  // as the Location filter restores its chips.
  const [targetOption] = useSelectedRelationshipOptions({
    sources: [bookingInstrumentSource],
    values: target ? [target] : [],

    authScope: currentUser.id,
    labels: targetOptionLabels,
  });
  const loadedTargetName = target
    ? (resourceTargets.find((resource) => resource.globalId === target)?.value.name ??
      events.data?.find((event) => event.target?.globalId === target)?.target?.value.name)
    : undefined;
  const targetName = targetOption?.restoreStatus === undefined ? targetOption?.label : loadedTargetName;
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
    availabilityLayout,
    itemConfig,
    droppedFilterFields,
    navigate,
    calendarSearch,
    setCalendarSearch,
    setResettingControls,
    view,
    setView,
    layout,
    setLayout,
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
    itemExpression,
    itemIssue,
    itemRuntimeFields,
    mineOnly,
    myItemsOnly,
    setMyItemsOnly,
    onEventFiltersChange,
    onSearchChange,
    setEventWhere,
    setItemWhere,
    setMineOnly,
    target,
    targetName,
    replaceSearch,
    targetOption,
  };
}
