import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { CalendarClockIcon, CalendarPlusIcon, Clock3Icon, EyeIcon } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import {
  BookingDateControls,
  BookingTimeZoneBadge,
  bookingToolbarClassName,
} from "@/modules/booking/components/BookingToolbar";
import { schedulingSettingsFieldNames } from "@/modules/booking/configuration/schedulingSettings";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { addCalendarDays, displayInterval } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import i18n from "@/modules/common/i18n";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import {
  type BookingConfiguration,
  type BookingConfigurationRow,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
} from "../bookable-items/bookingConfiguration";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import { type AvailabilityQuickFilter, useAvailabilityQuickFilterIndex } from "./availabilityQuickFilters";

const requestProjection = { fixed: ["id", "target", "enabled", "timezone", ...schedulingSettingsFieldNames] } as const;
const baseFilter = {
  kind: "and",
  children: [
    { kind: "comparison", field: "enabled", operator: "equals", value: true },
    { kind: "comparison", field: "target.deleted", operator: "equals", value: false },
  ],
} as const;

const allBookableItemsConfig: CollectionConfig<BookingConfiguration> = {
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
        }
      : field,
  ),
} as const;

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
  const { date, availability: quickMode } = useSearch({ from: "/booking/all-items" });
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
  const requestBaseFilter = useMemo<FilterExpression<BookingConfiguration>>(() => {
    if (!quickMode) return baseFilter;
    const quickFilter: FilterExpression<BookingConfiguration> =
      matchingIds.length > 0
        ? { kind: "comparison", field: "target", operator: "in", value: matchingIds }
        : { kind: "comparison", field: "id", operator: "equals", value: -1 };
    return { kind: "and", children: [...baseFilter.children, quickFilter] };
  }, [matchingIds, quickMode]);
  const request = useMemo(
    () => ({ token, depth: 1, projection: requestProjection, baseFilter: requestBaseFilter }),
    [requestBaseFilter, token],
  );
  const table = useApiV2TableList({
    resourceName: "booking-configurations",
    config: allBookableItemsConfig,
    documentSchema: BookingConfigurationSchema,
    request,
    query: { keepPreviousData: true, staleTime: 30_000 },
    table: {
      initialState: {
        visibleFields: ["target"],
      },
      features: { sorting: false, columns: false },
      queryString: {
        parameterPrefix: "all-bookable-items",
        tableId: "booking-all-bookable-items",
      },
    },
  });
  const rows = table.tableProps.rows as readonly BookingConfigurationRow[];
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

  const rowActions = useMemo<TableListRowActions<BookingConfigurationRow>>(
    () => ({
      id: "actions",
      label: t("allBookableItems.fields.actions"),
      width: 72,
      minWidth: 68,
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
      <TableList
        {...table.tableProps}
        rows={quickIndex.isPending || quickIndex.isError ? [] : table.tableProps.rows}
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
      />
    </main>
  );
}
