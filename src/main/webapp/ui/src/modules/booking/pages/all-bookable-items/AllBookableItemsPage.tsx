import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { CalendarClockIcon, ChevronLeftIcon, ChevronRightIcon, Clock3Icon } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import { zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import i18n from "@/modules/common/i18n";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import {
  TableList,
  type TableListFilterButtons,
  type TableListRowActions,
} from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Input } from "@/modules/common/ui/input";
import { InventoryItem, InventoryLocationLink } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import {
  type BookingConfiguration,
  type BookingConfigurationRow,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
} from "../bookable-items/bookingConfiguration";
import { schedulingSettingsFieldNames } from "../bookable-items/schedulingSettings";
import { calendarAvailabilityRow, useCalendarAvailability } from "../calendar/calendarAvailability";
import { type AvailabilityQuickFilter, useAvailabilityQuickFilterIndex } from "./availabilityQuickFilters";
import { addCalendarDays, localToday } from "./calendarDate";

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

export default function AllBookableItemsPage({ clock = currentDate }: { clock?: () => Date } = {}) {
  const { t } = useTranslation("booking");
  const { date, availability: quickMode } = useSearch({ from: "/booking/all-items" });
  const navigate = useNavigate({ from: "/booking/all-items" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const quickIndex = useAvailabilityQuickFilterIndex(quickMode, token, clock);
  const synchronizedNowPosition = (quickIndex.now.getHours() * 60 + quickIndex.now.getMinutes()) / (24 * 60);
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
    query: { keepPreviousData: true },
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
  const availability = useCalendarAvailability(quickMode ? [] : availabilityRows, date, token);

  const setDate = (nextDate: string) =>
    void navigate({ search: (current) => ({ ...current, date: nextDate }), replace: true });
  const setQuickMode = (mode: AvailabilityQuickFilter | undefined) =>
    void navigate({ search: (current) => ({ ...current, availability: mode }), replace: true });

  const rowActions = useMemo<TableListRowActions<BookingConfigurationRow>>(
    () => ({
      id: "actions",
      label: t("allBookableItems.fields.actions"),
      width: 100,
      minWidth: 88,
      renderCell: ({ row }) => {
        if (!row.target) return null;
        const rowDate = quickIndex.data?.get(row.target.globalId)?.date ?? date;
        return (
          <Link
            className={buttonVariants({ size: "sm" })}
            to="/booking/calendar/bookings/add"
            search={{ date: rowDate, target: row.target.globalId }}
          >
            {t("allBookableItems.actions.book")}
          </Link>
        );
      },
      renderInteraction: () => null,
    }),
    [date, quickIndex.data, t],
  );

  const quickFilterButtons: TableListFilterButtons = {
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
    onReset: () => setQuickMode(undefined),
  };

  return (
    <main className="space-y-5 p-4 sm:p-8">
      {!quickMode ? (
        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-2">
            <Label htmlFor="all-bookable-items-date">{t("allBookableItems.date")}</Label>
            <Input
              id="all-bookable-items-date"
              className="w-auto"
              type="date"
              value={date}
              onChange={(event) => setDate(event.currentTarget.value)}
            />
          </div>
          <Button
            type="button"
            size="icon"
            variant="outline"
            aria-label={t("allBookableItems.actions.previousDay")}
            onClick={() => setDate(addCalendarDays(date, -1))}
          >
            <ChevronLeftIcon />
          </Button>
          <Button type="button" variant="outline" onClick={() => setDate(localToday())}>
            {t("allBookableItems.actions.today")}
          </Button>
          <Button
            type="button"
            size="icon"
            variant="outline"
            aria-label={t("allBookableItems.actions.nextDay")}
            onClick={() => setDate(addCalendarDays(date, 1))}
          >
            <ChevronRightIcon />
          </Button>
        </div>
      ) : (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <CalendarClockIcon aria-hidden="true" />
          {t("allBookableItems.quickFilters.scope")}
        </p>
      )}
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
        filterButtons={quickFilterButtons}
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
                    nowPosition={synchronizedNowPosition}
                    showCurrentAvailability
                    timeZone={row.timezone}
                    itemName={target.value.name}
                  />
                );
              }
              if (availability.isPending) return <span role="status">{t("calendar.availabilityLoading")}</span>;
              if (availability.isError || !availability.data) {
                return <span role="status">{t("calendar.availabilityUnavailable")}</span>;
              }
              const bounds = zonedDayBounds(date, row.timezone);
              return (
                <AvailabilityBar
                  intervals={availability.data.get(target.globalId) ?? []}
                  periodStart={new Date(bounds.start)}
                  periodEnd={new Date(bounds.end)}
                  timeZone={row.timezone}
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
