import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import {
  CalendarClockIcon,
  CalendarPlusIcon,
  Clock3Icon,
  EyeIcon,
  PackageCheckIcon,
  PlusIcon,
  SettingsIcon,
} from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingRelationshipSources } from "@/modules/booking/domain/bookingRelationshipSource";
import { addCalendarDays, displayInterval } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
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
import {
  TableList,
  type TableListFilterButtons,
  type TableListProps,
  type TableListRowActions,
} from "@/modules/common/table-list/TableList";
import type { FilterExpression, FilterState } from "@/modules/common/table-list/tableListState";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import { cn } from "@/modules/common/utils/cn";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import {
  type AllBookableItem,
  AvailabilityCandidateLimitError,
  type AvailabilityQuickFilter,
  deriveAvailabilityCandidateFilter,
  hasAvailabilityFilter,
  resolveAvailabilityFilters,
  useAvailabilityQuickFilterIndex,
  withAvailability,
} from "./availabilityQuickFilters";

function createAllBookableItemsConfig(
  availableNow: string,
  freeLaterToday: string,
  openRecordLabel: (globalId: string) => string,
) {
  return resolveCollectionConfig({
    slug: "all-bookable-items",
    idField: "id",
    useAsTitle: "target",
    labels: {
      singularKey: "booking:allBookableItems.singular",
      pluralKey: "booking:allBookableItems.plural",
    },
    defaultColumns: ["target"],
    listSearchableFields: ["target.name"],
    relationshipSources: bookingRelationshipSources,
    fields: [
      { name: "id", type: "number", labelKey: "booking:bookableItems.fields.id", list: false, form: false },
      {
        name: "target",
        type: "relationship",
        relationTo: "booking-instruments",
        hasMany: false,
        labelKey: "booking:bookableItems.fields.target",
        capabilities: { sortable: false, filterOperators: ["equals"], supportsWildcards: false },
        list: {
          width: 280,
          minWidth: 240,
          renderCell: ({ row }: { row: AllBookableItem }) =>
            row.target ? (
              <InventoryItem
                name={row.target.value.name}
                globalId={row.target.globalId}
                href={`/globalId/${row.target.globalId}`}
                idLinkLabel={openRecordLabel(row.target.globalId)}
                size="xs"
              >
                <InventoryLocationLink
                  name={row.target.value.parentContainerName}
                  globalId={row.target.value.parentContainerGlobalId}
                />
              </InventoryItem>
            ) : (
              <UnknownItem size="xs" />
            ),
        },
      },
      {
        name: "availability",
        type: "select",
        labelKey: "booking:calendar.availability",
        options: [
          { label: availableNow, value: "available-now" },
          { label: freeLaterToday, value: "free-later-today" },
        ],
        capabilities: { sortable: false, filterOperators: ["equals"], supportsWildcards: false },
        list: false,
        form: false,
      },
    ],
  } as const satisfies CollectionConfig<AllBookableItem>);
}

function comparisons(
  expression: FilterExpression<AllBookableItem> | null,
): readonly Extract<FilterExpression<AllBookableItem>, { kind: "comparison" }>[] {
  if (!expression) return [];
  if (expression.kind === "comparison") return [expression];
  if (expression.kind === "or") return [];
  return expression.children.flatMap(comparisons);
}

function filterValue(
  expression: FilterExpression<AllBookableItem> | null,
  field: "availability" | "target",
): string | undefined {
  const value = comparisons(expression).find(
    (comparison) => comparison.field === field && comparison.operator === "equals",
  )?.value;
  return typeof value === "string" ? value : undefined;
}

function availabilityMode(expression: FilterExpression<AllBookableItem> | null): AvailabilityQuickFilter | undefined {
  const values = new Set(
    comparisons(expression)
      .filter((rule) => rule.field === "availability")
      .map((rule) => rule.value),
  );
  if (values.size !== 1) return undefined;
  const value = filterValue(expression, "availability");
  return value === "available-now" || value === "free-later-today" ? value : undefined;
}

function filterExpression(
  target: string | undefined,
  availability: AvailabilityQuickFilter | undefined,
): FilterExpression<AllBookableItem> | null {
  const values: FilterExpression<AllBookableItem>[] = [];
  if (target) values.push({ kind: "comparison", field: "target", operator: "equals", value: target });
  if (availability) {
    values.push({ kind: "comparison", field: "availability", operator: "equals", value: availability });
  }
  return values.length === 0 ? null : values.length === 1 ? values[0] : { kind: "and", children: values };
}

const currentDate = () => new Date();

export type AllBookableItemsContentProps = {
  clock?: () => Date;
  /** @deprecated Display timezone comes from Booking preferences. */
  userTimeZone?: string;
};

export function AllBookableItemsContent({
  clock = currentDate,
  userTimeZone: _legacyUserTimeZone,
}: AllBookableItemsContentProps = {}) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const sourceConfig = useMemo(
    () =>
      createAllBookableItemsConfig(
        t("allBookableItems.quickFilters.availableNow"),
        t("allBookableItems.quickFilters.freeLaterToday"),
        (globalId) => commonT("tableList.filters.openRecord", { globalId }),
      ),
    [commonT, t],
  );
  const {
    date,
    availability: routeAvailability,
    target,
    mine = false,
    where,
    q,
    types,
    page = 1,
    pageSize = 20,
  } = useSearch({ from: "/booking/all-items" });
  const navigate = useNavigate({ from: "/booking/all-items" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const runtimeSelectors = useMemo(() => (where ? rsqlSelectors(where) : []), [where]);
  const runtimeFieldState = useApiV2RuntimeFields<AllBookableItem>({
    resourceName: "booking-configurations",
    selectors: runtimeSelectors,
    request: { token, authScope: currentUser.id },
  });
  const config = useMemo(
    () =>
      enrichApiV2FilterConfig({
        config: sourceConfig,
        metadata: runtimeFieldState.metadata,
        runtimeFields: runtimeFieldState.runtimeFields,
        localFields: ["availability"],
        translate: (key) => String(t(key as never)),
      }),
    [runtimeFieldState.metadata, runtimeFieldState.runtimeFields, sourceConfig, t],
  );
  const preferences = useBookingDisplayPreferences();
  const userToday = todayInTimeZone(preferences.timeZone, clock());
  const selectedDate = date ?? userToday;
  const filters = useMemo<FilterState<AllBookableItem>>(
    () => ({
      search: q ?? "",
      expression: where ? parseRsqlExpression(where, config) : filterExpression(target, routeAvailability),
    }),
    [config, q, routeAvailability, target, where],
  );
  const invalidFilter =
    !runtimeFieldState.pending && runtimeFieldState.error === null && Boolean(where && !filters.expression);
  const runtimeFilterBlocked =
    runtimeFieldState.pending ||
    runtimeFieldState.error !== null ||
    runtimeFieldState.missing.length > 0 ||
    invalidFilter;
  const candidateFilter = deriveAvailabilityCandidateFilter(filters.expression);
  const candidateWhere = candidateFilter ? serializeRsqlExpression(candidateFilter) : undefined;
  const usesAvailability = hasAvailabilityFilter(filters.expression);
  const quickMode = availabilityMode(filters.expression);
  const bounds = useMemo(
    () =>
      displayInterval(
        selectedDate,
        preferences.timeZone,
        preferences.availabilityWindow.start,
        preferences.availabilityWindow.end,
      ),
    [preferences.availabilityWindow.end, preferences.availabilityWindow.start, preferences.timeZone, selectedDate],
  );
  const quickIndex = useAvailabilityQuickFilterIndex(
    token,
    preferences.timeZone,
    preferences.availabilityWindow.start,
    preferences.availabilityWindow.end,
    clock,
    candidateWhere,
    !runtimeFilterBlocked,
    currentUser.id,
    { q, types, mine },
  );
  const quickFilterPending = usesAvailability && quickIndex.isPending;
  const quickFilterError = usesAvailability && quickIndex.isError;
  const serverFilter = resolveAvailabilityFilters(filters.expression, quickIndex.data);
  const serverWhere = serverFilter ? serializeRsqlExpression(serverFilter) : undefined;
  const catalogue = useQuery({
    queryKey: ["api-v2", "booking-catalogue", "all-items", token, q, types, mine, serverWhere, page, pageSize],
    queryFn: ({ signal }) =>
      fetchBookingCatalogue({ q, types, mine, where: serverWhere, page, pageSize }, token, signal),
    enabled: !runtimeFilterBlocked && (!usesAvailability || quickIndex.data !== undefined),
    staleTime: 30_000,
  });
  const rows = runtimeFilterBlocked ? [] : (catalogue.data?.items ?? []).map(catalogueItemAsConfiguration);
  const availabilityRows = rows.flatMap((row) => {
    if (!row.target) return [];
    const availabilityRow = calendarAvailabilityRow({ globalId: row.target.globalId, ...row });
    return availabilityRow ? [availabilityRow] : [];
  });
  const useQuickAvailability =
    selectedDate === userToday &&
    !quickIndex.isError &&
    (quickIndex.isPending || availabilityRows.every((row) => quickIndex.data?.has(row.globalId)));
  const availability = useCalendarAvailability(
    quickMode || useQuickAvailability ? [] : availabilityRows,
    bounds,
    token,
    currentUser.id,
  );

  const setDate = (nextDate: string) => {
    const remaining = withAvailability(filters.expression, undefined);
    void navigate({
      search: (current) => ({
        ...current,
        date: nextDate === userToday ? undefined : nextDate,
        availability: undefined,
        target: undefined,
        where: remaining ? serializeRsqlExpression(remaining) : undefined,
        page: undefined,
      }),
      replace: true,
    });
  };
  const resetView = () =>
    void navigate({
      search: (current) => ({
        ...current,
        date: undefined,
        availability: undefined,
        target: undefined,
        mine: undefined,
        where: undefined,
        q: undefined,
        types: undefined,
        page: undefined,
      }),
      replace: true,
    });
  const setFilters = (next: FilterState<AllBookableItem>) => {
    const nextWhere = next.expression ? serializeRsqlExpression(next.expression) : undefined;
    const nextSearch = next.search || undefined;
    if (nextSearch === q && nextWhere === where && !target && !routeAvailability && page === 1) {
      return;
    }
    void navigate({
      search: (current) => ({
        ...current,
        date: hasAvailabilityFilter(next.expression) ? undefined : current.date,
        availability: undefined,
        target: undefined,
        where: nextWhere,
        q: nextSearch,
        page: undefined,
      }),
      replace: true,
    });
  };
  const removeRestoredViewIssue = () =>
    void navigate({
      search: (current) => ({ ...current, where: undefined, page: undefined }),
      replace: true,
    });
  const restoredViewIssue = runtimeFieldState.error
    ? {
        kind: "network" as const,
        encoded: where ?? "",
        retry: () => void runtimeFieldState.retry(),
        remove: removeRestoredViewIssue,
      }
    : !runtimeFieldState.pending && (runtimeFieldState.missing.length > 0 || invalidFilter)
      ? { kind: "invalid" as const, encoded: where ?? "", remove: removeRestoredViewIssue }
      : undefined;
  const tableProps: TableListProps<AllBookableItem> = {
    config,
    rows,
    getRowId: (row) => String(row.id),
    clientSide: false,
    status:
      runtimeFieldState.error !== null ||
      runtimeFieldState.missing.length > 0 ||
      invalidFilter ||
      quickFilterError ||
      catalogue.isError
        ? "error"
        : runtimeFieldState.pending || quickFilterPending || catalogue.isPending
          ? "loading"
          : catalogue.isFetching
            ? "refreshing"
            : "idle",
    error: runtimeFieldState.error ?? (quickFilterError ? undefined : catalogue.error),
    restoredViewIssue,
    onSelectRuntimeField: runtimeFieldState.selectRuntimeField,
    runtimeFieldDefinitions: runtimeFieldState.runtimeFields,
    runtimeFieldAuthScope: currentUser.id,
    queryString: false,
    features: {
      filtering: {
        value: filters,
        onChange: setFilters,
      },
      sorting: false,
      columns: false,
      pagination: {
        value: { pageIndex: page - 1, pageSize },
        rowCount: catalogue.data?.total ?? 0,
        onChange: (nextPage) => {
          const nextPageNumber = nextPage.pageSize === pageSize ? nextPage.pageIndex + 1 : 1;
          if (nextPageNumber === page && nextPage.pageSize === pageSize) return;
          void navigate({
            search: (current) => ({
              ...current,
              page: nextPageNumber > 1 ? nextPageNumber : undefined,
              pageSize: nextPage.pageSize,
            }),
            replace: true,
          });
        },
      },
    },
  };

  const rowActions = useMemo<TableListRowActions<AllBookableItem>>(
    () => ({
      id: "actions",
      label: t("allBookableItems.fields.actions"),
      width: 176,
      minWidth: 120,
      renderCell: ({ row }) => {
        if (!row.target) return null;
        const rowDate = quickMode ? (quickIndex.data?.get(row.target.globalId)?.date ?? selectedDate) : selectedDate;
        const detailsLabel = t("allBookableItems.actions.viewDetails");
        const bookLabel = t("allBookableItems.actions.book");
        return (
          <div className="flex gap-1">
            <Tooltip>
              <TooltipTrigger
                render={
                  <Link
                    aria-label={detailsLabel}
                    className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                    data-slot="button"
                    to="/booking/bookable-items/$globalId/{-$tab}"
                    params={{ globalId: row.target.globalId, tab: undefined }}
                  />
                }
              >
                <EyeIcon aria-hidden="true" />
              </TooltipTrigger>
              <TooltipContent role="tooltip" className="rounded-sm">
                {detailsLabel}
              </TooltipContent>
            </Tooltip>
            {row.capabilities?.canCreateBooking ? (
              <Tooltip>
                <TooltipTrigger
                  render={
                    <Link
                      aria-label={bookLabel}
                      className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                      data-slot="button"
                      to="/booking/calendar/bookings/add"
                      search={{ date: rowDate, target: row.target.globalId }}
                    />
                  }
                >
                  <CalendarPlusIcon aria-hidden="true" />
                </TooltipTrigger>
                <TooltipContent role="tooltip" className="rounded-sm">
                  {bookLabel}
                </TooltipContent>
              </Tooltip>
            ) : null}
            {row.capabilities?.canEditConfiguration ? (
              <Tooltip>
                <TooltipTrigger
                  render={
                    <Link
                      aria-label={t("allBookableItems.actions.settings")}
                      className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                      data-slot="button"
                      to="/booking/bookable-items/$globalId/{-$tab}"
                      params={{ globalId: row.target.globalId, tab: "details" }}
                      search={{ edit: true }}
                    />
                  }
                >
                  <SettingsIcon aria-hidden="true" />
                </TooltipTrigger>
                <TooltipContent role="tooltip" className="rounded-sm">
                  {t("allBookableItems.actions.settings")}
                </TooltipContent>
              </Tooltip>
            ) : null}
          </div>
        );
      },
      renderInteraction: () => null,
    }),
    [quickIndex.data, quickMode, selectedDate, t],
  );

  const availabilityFilters: TableListFilterButtons = {
    legend: t("allBookableItems.quickFilters.legend"),
    controlsOnSeparateRow: true,
    controls: (
      <BookingDateControls
        date={selectedDate}
        today={userToday}
        timeZone={preferences.timeZone}
        controlsLabel={t("allBookableItems.dateControls")}
        navigationLabel={t("allBookableItems.dateNavigation")}
        previousLabel={t("allBookableItems.actions.previousDay")}
        todayLabel={t("allBookableItems.actions.today")}
        nextLabel={t("allBookableItems.actions.nextDay")}
        jumpToDateLabel={t("allBookableItems.jumpToDate")}
        onPrevious={() => setDate(addCalendarDays(selectedDate, -1))}
        onNext={() => setDate(addCalendarDays(selectedDate, 1))}
        onDateChange={setDate}
      />
    ),
    hasChanges: selectedDate !== userToday || Boolean(types?.length) || mine,
    buttons: [
      {
        id: "mine",
        label: t("allBookableItems.quickFilters.myItems"),
        icon: <PackageCheckIcon aria-hidden="true" />,
        pressed: mine,
        onClick: () =>
          void navigate({
            search: (current) => ({ ...current, mine: mine ? undefined : true, page: undefined }),
            replace: true,
          }),
      },
      ...(["available-now", "free-later-today"] as const).map((mode) => ({
        id: mode,
        label: (
          <>
            {mode === "available-now"
              ? t("allBookableItems.quickFilters.availableNow")
              : t("allBookableItems.quickFilters.freeLaterToday")}
            <span
              aria-hidden="true"
              className="ml-0.5 min-w-5 rounded-sm bg-foreground px-1 text-[10px] text-background"
            >
              {quickIndex.isError ? (
                "—"
              ) : quickIndex.data ? (
                [...quickIndex.data.values()].filter(({ category }) => category === mode).length
              ) : (
                <Skeleton className="h-3 w-3" />
              )}
            </span>
          </>
        ),
        icon: mode === "available-now" ? <Clock3Icon aria-hidden="true" /> : <CalendarClockIcon aria-hidden="true" />,
        pressed: quickMode === mode,
        disabled: quickIndex.error instanceof AvailabilityCandidateLimitError && quickMode !== mode,
        onClick: () =>
          setFilters({
            ...filters,
            expression: withAvailability(filters.expression, quickMode === mode ? undefined : mode),
          }),
      })),
    ],
    onReset: resetView,
  };

  return (
    <main className="space-y-5 p-4 sm:p-8">
      {tableProps.status === "loading" ? (
        <p role="status" className="sr-only">
          {t("allBookableItems.quickFilters.loading")}
        </p>
      ) : null}
      {quickIndex.isError ? (
        <div role="alert" className="flex items-center gap-3">
          <span>
            {t(
              quickIndex.error instanceof AvailabilityCandidateLimitError
                ? "allBookableItems.quickFilters.limit"
                : "allBookableItems.quickFilters.error",
            )}
          </span>
          {quickIndex.error instanceof AvailabilityCandidateLimitError ? null : (
            <Button type="button" variant="outline" onClick={() => void quickIndex.refetch()}>
              {t("allBookableItems.quickFilters.retry")}
            </Button>
          )}
        </div>
      ) : null}
      <TableList
        {...tableProps}
        headingClassName="text-2xl font-semibold"
        onReset={resetView}
        rows={runtimeFilterBlocked || quickFilterPending || quickFilterError ? [] : rows}
        filterButtons={availabilityFilters}
        presentations={{ table: "wide", cards: "narrow" }}
        uiColumns={[
          {
            id: "availability",
            label: t("calendar.availability"),
            minWidth: 320,
            width: 520,
            card: { fullWidth: true },
            renderCell: (row) => {
              const target = row.target;
              if (!target || !row.timezone) return t("calendar.availabilityUnavailable");
              const item = {
                name: target.value.name,
                globalId: target.globalId,
                ...(target.value.parentContainerName != null && target.value.parentContainerGlobalId != null
                  ? {
                      location: {
                        name: target.value.parentContainerName,
                        globalId: target.value.parentContainerGlobalId,
                      },
                    }
                  : {}),
              };
              const quickEntry = quickIndex.data?.get(target.globalId);
              if (quickMode || (useQuickAvailability && quickEntry)) {
                if (!quickEntry) return t("calendar.availabilityUnavailable");
                return (
                  <AvailabilityBar
                    intervals={quickEntry.intervals}
                    periodStart={new Date(quickEntry.bounds.start)}
                    periodEnd={new Date(quickEntry.bounds.end)}
                    now={quickIndex.now}
                    showBookingContextDetails={false}
                    showCurrentAvailability
                    showPeriodLabels
                    timeZone={preferences.timeZone}
                    item={item}
                  />
                );
              }
              if ((useQuickAvailability && quickIndex.isPending) || availability.isPending)
                return (
                  <div aria-busy="true">
                    <span role="status" className="sr-only">
                      {t("calendar.availabilityLoading")}
                    </span>
                    <Skeleton aria-hidden="true" className="h-16 w-full" />
                  </div>
                );
              if (availability.isError || !availability.data) {
                return <span role="status">{t("calendar.availabilityUnavailable")}</span>;
              }
              return (
                <AvailabilityBar
                  intervals={availability.data.get(target.globalId) ?? []}
                  periodStart={new Date(bounds.start)}
                  periodEnd={new Date(bounds.end)}
                  now={selectedDate === userToday ? quickIndex.now : undefined}
                  showBookingContextDetails={false}
                  showCurrentAvailability
                  showPeriodLabels
                  timeZone={preferences.timeZone}
                  item={item}
                />
              );
            },
          },
        ]}
        rowActions={rowActions}
        createAction={
          <Link to="/booking/bookable-items/add" className={cn(buttonVariants(), "rounded-sm")} data-slot="button">
            <PlusIcon aria-hidden="true" data-icon="inline-start" />
            {t("bookableItems.actions.add")}
          </Link>
        }
      />
    </main>
  );
}
