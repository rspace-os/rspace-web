import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { CalendarClockIcon, CalendarPlusIcon, Clock3Icon, EyeIcon, PlusIcon, SettingsIcon } from "lucide-react";
import { Suspense, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import { BookingDateControls } from "@/modules/booking/components/BookingToolbar";
import { catalogueItemAsConfiguration, fetchBookingCatalogue } from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { addCalendarDays, displayInterval } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import i18n from "@/modules/common/i18n";
import { parseRsqlExpression, serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";
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
  type AvailabilityQuickFilter,
  hasAvailabilityFilter,
  resolveAvailabilityFilters,
  useAvailabilityQuickFilterIndex,
  withAvailability,
} from "./availabilityQuickFilters";

function createAllBookableItemsConfig(availableNow: string, freeLaterToday: string) {
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
                idLinkLabel={i18n.t("common:tableList.filters.openRecord", { globalId: row.target.globalId })}
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

function AllItemsSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="space-y-5 p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-5">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-24 w-full" />
        {[0, 1, 2, 3].map((row) => (
          <Skeleton key={row} className="h-20 w-full" />
        ))}
      </div>
    </main>
  );
}

export default function AllBookableItemsPage(props: Parameters<typeof AllBookableItemsContent>[0] = {}) {
  return (
    <Suspense fallback={<AllItemsSkeleton />}>
      <AllBookableItemsContent {...props} />
    </Suspense>
  );
}

function AllBookableItemsContent({
  clock = currentDate,
  userTimeZone: _legacyUserTimeZone,
}: {
  clock?: () => Date;
  /** @deprecated Display timezone comes from Booking preferences. */
  userTimeZone?: string;
} = {}) {
  const { t } = useTranslation("booking");
  const config = useMemo(
    () =>
      createAllBookableItemsConfig(
        t("allBookableItems.quickFilters.availableNow"),
        t("allBookableItems.quickFilters.freeLaterToday"),
      ),
    [t],
  );
  const {
    date,
    availability: routeAvailability,
    target,
    where,
    q,
    page = 1,
    pageSize = 20,
  } = useSearch({ from: "/booking/all-items" });
  const navigate = useNavigate({ from: "/booking/all-items" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
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
  const invalidFilter = Boolean(where && !filters.expression);
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
  );
  const quickFilterPending = usesAvailability && quickIndex.isPending;
  const quickFilterError = usesAvailability && quickIndex.isError;
  const serverFilter = resolveAvailabilityFilters(filters.expression, quickIndex.data);
  const serverWhere = serverFilter ? serializeRsqlExpression(serverFilter) : undefined;
  const catalogue = useQuery({
    queryKey: ["api-v2", "booking-catalogue", "all-items", token, q, serverWhere, page, pageSize],
    queryFn: ({ signal }) => fetchBookingCatalogue({ q, where: serverWhere, page, pageSize }, token, signal),
    enabled: !invalidFilter && (!usesAvailability || quickIndex.data !== undefined),
    staleTime: 30_000,
  });
  const rows = (catalogue.data?.items ?? []).map(catalogueItemAsConfiguration);
  const availabilityRows = rows.flatMap((row) => {
    if (!row.target) return [];
    const availabilityRow = calendarAvailabilityRow({ globalId: row.target.globalId, ...row });
    return availabilityRow ? [availabilityRow] : [];
  });
  const availability = useCalendarAvailability(quickMode ? [] : availabilityRows, bounds, token);

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
        where: undefined,
        q: undefined,
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
  const tableProps: TableListProps<AllBookableItem> = {
    config,
    rows,
    getRowId: (row) => String(row.id),
    clientSide: false,
    status:
      invalidFilter || quickFilterError || catalogue.isError
        ? "error"
        : quickFilterPending || catalogue.isPending
          ? "loading"
          : catalogue.isFetching
            ? "refreshing"
            : "idle",
    error: quickFilterError ? undefined : catalogue.error,
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
    hasChanges: selectedDate !== userToday,
    buttons: (["available-now", "free-later-today"] as const).map((mode) => ({
      id: mode,
      label: (
        <>
          {mode === "available-now"
            ? t("allBookableItems.quickFilters.availableNow")
            : t("allBookableItems.quickFilters.freeLaterToday")}
          <span aria-hidden="true" className="ml-0.5 min-w-5 rounded-sm bg-foreground px-1 text-[10px] text-background">
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
      onClick: () =>
        setFilters({
          ...filters,
          expression: withAvailability(filters.expression, quickMode === mode ? undefined : mode),
        }),
    })),
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
          <span>{t("allBookableItems.quickFilters.error")}</span>
          <Button type="button" variant="outline" onClick={() => void quickIndex.refetch()}>
            {t("allBookableItems.quickFilters.retry")}
          </Button>
        </div>
      ) : null}
      <TableList
        {...tableProps}
        onReset={resetView}
        rows={quickFilterPending || quickFilterError ? [] : rows}
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
              if (quickMode) {
                if (!quickEntry) return t("calendar.availabilityUnavailable");
                return (
                  <AvailabilityBar
                    intervals={quickEntry.intervals}
                    periodStart={new Date(quickEntry.bounds.start)}
                    periodEnd={new Date(quickEntry.bounds.end)}
                    now={quickIndex.now}
                    showCurrentAvailability
                    showPeriodLabels
                    timeZone={preferences.timeZone}
                    item={item}
                  />
                );
              }
              if (availability.isPending)
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
