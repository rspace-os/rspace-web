import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import {
  CalendarClockIcon,
  CalendarPlusIcon,
  Clock3Icon,
  EyeIcon,
  KeyRoundIcon,
  PlusIcon,
  SettingsIcon,
} from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import {
  BookingDateControls,
  BookingTimeZoneBadge,
  bookingToolbarClassName,
} from "@/modules/booking/components/BookingToolbar";
import {
  catalogueItemAsConfiguration,
  fetchBookingCatalogue,
  fetchBookingCatalogueLocations,
} from "@/modules/booking/domain/bookingCatalogue";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { addCalendarDays, displayInterval } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { TableList, type TableListProps, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import { cn } from "@/modules/common/utils/cn";
import { type BookingConfiguration, bookingConfigurationConfig } from "../bookable-items/bookingConfiguration";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import { type AvailabilityQuickFilter, useAvailabilityQuickFilterIndex } from "./availabilityQuickFilters";

const allBookableItemsConfig = resolveCollectionConfig({
  ...bookingConfigurationConfig,
  slug: "all-bookable-items",
  labels: {
    singularKey: "booking:allBookableItems.singular",
    pluralKey: "booking:allBookableItems.plural",
  },
  defaultColumns: ["target"],
  fields: bookingConfigurationConfig.fields.map((field) =>
    field.name === "target"
      ? {
          ...field,
          list: {
            ...field.list,
            width: 280,
            minWidth: 240,
            renderCell: ({ row }: { row: BookingConfiguration }) =>
              row.target ? (
                <InventoryItem name={row.target.value.name} globalId={row.target.globalId} size="xs" />
              ) : (
                <UnknownItem size="xs" />
              ),
          },
        }
      : field,
  ),
} as const satisfies CollectionConfig<BookingConfiguration>);

const currentDate = () => new Date();

export default function AllBookableItemsPage({
  clock = currentDate,
  userTimeZone: _legacyUserTimeZone,
}: {
  clock?: () => Date;
  /** @deprecated Display timezone comes from Booking preferences. */
  userTimeZone?: string;
} = {}) {
  const { t } = useTranslation("booking");
  const { date, availability: quickMode, locations = [], q, page = 1 } = useSearch({ from: "/booking/all-items" });
  const navigate = useNavigate({ from: "/booking/all-items" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const userToday = todayInTimeZone(preferences.timeZone, clock());
  const selectedDate = date ?? userToday;
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
    quickMode,
    token,
    preferences.timeZone,
    preferences.availabilityWindow.start,
    preferences.availabilityWindow.end,
    clock,
  );
  const matchingIds = useMemo(
    () =>
      quickIndex.data
        ? [...quickIndex.data].flatMap(([globalId, entry]) => (entry.category === quickMode ? [globalId] : []))
        : [],
    [quickIndex.data, quickMode],
  );
  const catalogue = useQuery({
    queryKey: ["api-v2", "booking-catalogue", "all-items", token, q, locations, page],
    queryFn: ({ signal }) => fetchBookingCatalogue({ q, locations, page, pageSize: 20 }, token, signal),
    placeholderData: (previous) => previous,
    staleTime: 30_000,
  });
  const locationOptions = useQuery({
    queryKey: ["api-v2", "booking-catalogue", "locations", token],
    queryFn: ({ signal }) => fetchBookingCatalogueLocations({ pageSize: 100 }, token, signal),
  });
  const rows = (catalogue.data?.items ?? []).map(catalogueItemAsConfiguration);
  const visibleRows = quickMode ? rows.filter((row) => row.target && matchingIds.includes(row.target.globalId)) : rows;
  const availabilityRows = rows.flatMap((row) => {
    if (!row.target) return [];
    const availabilityRow = calendarAvailabilityRow({ globalId: row.target.globalId, ...row });
    return availabilityRow ? [availabilityRow] : [];
  });
  const availability = useCalendarAvailability(quickMode ? [] : availabilityRows, bounds, token);

  const setDate = (nextDate: string) =>
    void navigate({ search: (current) => ({ ...current, date: nextDate }), replace: true });
  const setQuickMode = (mode: AvailabilityQuickFilter | undefined) =>
    void navigate({ search: (current) => ({ ...current, availability: mode }), replace: true });
  const setLocations = (nextLocations: readonly string[]) => {
    void navigate({
      search: (current) => ({
        ...current,
        locations: nextLocations.length > 0 ? [...nextLocations] : undefined,
        page: undefined,
      }),
      replace: true,
    });
  };
  const tableProps: TableListProps<BookingConfiguration> = {
    config: allBookableItemsConfig,
    rows,
    getRowId: (row) => String(row.id),
    clientSide: false,
    status: catalogue.isError
      ? "error"
      : catalogue.isPending
        ? "loading"
        : catalogue.isFetching
          ? "refreshing"
          : "idle",
    error: catalogue.error,
    queryString: false,
    features: {
      filtering: {
        value: { search: q ?? "", expression: null },
        onChange: (filters) =>
          void navigate({
            search: (current) => ({ ...current, q: filters.search || undefined, page: undefined }),
            replace: true,
          }),
      },
      sorting: false,
      columns: false,
      pagination: {
        value: { pageIndex: page - 1, pageSize: 20 },
        rowCount: catalogue.data?.total ?? 0,
        onChange: (nextPage) =>
          void navigate({
            search: (current) => ({ ...current, page: nextPage.pageIndex > 0 ? nextPage.pageIndex + 1 : undefined }),
            replace: true,
          }),
      },
    },
  };

  const rowActions = useMemo<TableListRowActions<BookingConfiguration>>(
    () => ({
      id: "actions",
      label: t("allBookableItems.fields.actions"),
      width: 176,
      minWidth: 120,
      renderCell: ({ row }) => {
        if (!row.target) return null;
        const rowDate = quickIndex.data?.get(row.target.globalId)?.date ?? selectedDate;
        const detailsLabel = t("allBookableItems.actions.viewDetails");
        const bookLabel = t("allBookableItems.actions.book");
        return (
          <div className="flex gap-1">
            <Tooltip>
              <TooltipTrigger
                render={
                  <Link
                    aria-label={detailsLabel}
                    className={buttonVariants({ variant: "outline", size: "icon-sm" })}
                    data-slot="button"
                    to="/booking/bookable-items/$globalId"
                    params={{ globalId: row.target.globalId }}
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
                      className={buttonVariants({ variant: "outline", size: "icon-sm" })}
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
                      className={buttonVariants({ variant: "outline", size: "icon-sm" })}
                      data-slot="button"
                      to="/booking/bookable-items/$globalId"
                      params={{ globalId: row.target.globalId }}
                      search={{ tab: "details", edit: true }}
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
            {row.capabilities?.canViewAccess ? (
              <Tooltip>
                <TooltipTrigger
                  render={
                    <Link
                      aria-label={t("allBookableItems.actions.access")}
                      className={buttonVariants({ variant: "outline", size: "icon-sm" })}
                      data-slot="button"
                      to="/booking/bookable-items/$globalId"
                      params={{ globalId: row.target.globalId }}
                      search={{ tab: "access" }}
                    />
                  }
                >
                  <KeyRoundIcon aria-hidden="true" />
                </TooltipTrigger>
                <TooltipContent role="tooltip" className="rounded-sm">
                  {t("allBookableItems.actions.access")}
                </TooltipContent>
              </Tooltip>
            ) : null}
          </div>
        );
      },
      renderInteraction: () => null,
    }),
    [quickIndex.data, selectedDate, t],
  );

  const quickFilterButtons = {
    legend: t("allBookableItems.quickFilters.legend"),
    buttons: (["available-now", "free-later-today"] as const).map((mode) => ({
      id: mode,
      label:
        mode === "available-now"
          ? t("allBookableItems.quickFilters.availableNow")
          : t("allBookableItems.quickFilters.freeLaterToday"),
      icon: mode === "available-now" ? <Clock3Icon aria-hidden="true" /> : <CalendarClockIcon aria-hidden="true" />,
      pressed: quickMode === mode,
      count: quickIndex.data
        ? [...quickIndex.data.values()].filter(({ category }) => category === mode).length
        : undefined,
      onClick: () => setQuickMode(quickMode === mode ? undefined : mode),
    })),
  };

  return (
    <main className="space-y-5 p-4 sm:p-8">
      <div className="overflow-hidden rounded-sm border bg-card">
        <div role="toolbar" aria-label={t("allBookableItems.toolbar")} className={bookingToolbarClassName}>
          {!quickMode ? (
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
          ) : (
            <p className="flex min-w-0 items-center gap-2 text-sm text-muted-foreground">
              <CalendarClockIcon aria-hidden="true" />
              {t("allBookableItems.quickFilters.scope")}
            </p>
          )}
          <div className="flex min-w-0 flex-wrap items-center gap-2 xl:ml-auto xl:flex-nowrap">
            <BookingTimeZoneBadge
              timeZone={preferences.timeZone}
              label={t("availabilityBar.timezone", { timezone: preferences.timeZone })}
            />
            <fieldset className="flex min-w-0 flex-wrap items-center gap-2">
              <legend className="sr-only">{quickFilterButtons.legend}</legend>
              {quickFilterButtons.buttons.map((button) => (
                <Button
                  key={button.id}
                  type="button"
                  size="sm"
                  aria-pressed={button.pressed}
                  variant={button.pressed ? "secondary" : "outline"}
                  onClick={button.onClick}
                >
                  {button.icon}
                  {button.label}
                  {button.count === undefined ? null : (
                    <span
                      aria-hidden="true"
                      className="ml-0.5 rounded-sm bg-foreground px-1 text-[10px] text-background"
                    >
                      {button.count}
                    </span>
                  )}
                </Button>
              ))}
            </fieldset>
          </div>
        </div>
      </div>
      {quickIndex.isPending ? <p role="status">{t("allBookableItems.quickFilters.loading")}</p> : null}
      {quickIndex.isError ? (
        <div role="alert" className="flex items-center gap-3">
          <span>{t("allBookableItems.quickFilters.error")}</span>
          <Button type="button" variant="outline" onClick={() => void quickIndex.refetch()}>
            {t("allBookableItems.quickFilters.retry")}
          </Button>
        </div>
      ) : null}
      {locationOptions.data && locationOptions.data.items.length > 0 ? (
        <fieldset className="flex flex-wrap gap-2">
          <legend className="mb-2 text-sm font-medium">{t("allBookableItems.filters.location")}</legend>
          {locationOptions.data.items.map((location) => (
            <label
              key={location.globalId}
              className="inline-flex min-h-6 items-center gap-2 rounded-sm border px-2 py-1 text-sm"
            >
              <input
                type="checkbox"
                checked={locations.includes(location.globalId)}
                onChange={(event) =>
                  setLocations(
                    event.currentTarget.checked
                      ? [...locations, location.globalId]
                      : locations.filter((value) => value !== location.globalId),
                  )
                }
              />
              {t("allBookableItems.filters.locationOption", {
                name: location.name,
                globalId: location.globalId,
              })}
            </label>
          ))}
        </fieldset>
      ) : null}
      <TableList
        {...tableProps}
        rows={quickIndex.isPending || quickIndex.isError ? [] : visibleRows}
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
                    itemName={target.value.name}
                  />
                );
              }
              if (availability.isPending) return <span role="status">{t("calendar.availabilityLoading")}</span>;
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
                  itemName={target.value.name}
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
