import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { AvailabilityBar } from "@/modules/booking/components/AvailabilityBar";
import { useBookableItem } from "@/modules/booking/components/BookableItemPicker";
import { DayTimeline } from "@/modules/booking/components/DayTimeline";
import { addCalendarDays, currentWallClock, zonedDayBounds } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Input } from "@/modules/common/ui/input";
import { InventoryItem } from "@/modules/common/ui/inventory-item";
import { Label } from "@/modules/common/ui/label";
import { UnknownItem } from "@/modules/common/ui/unknown-item";
import {
  type BookingConfiguration,
  type BookingConfigurationRow,
  BookingConfigurationSchema,
  bookingConfigurationConfig,
} from "../bookable-items/bookingConfiguration";
import { useCalendarAvailability } from "./calendarAvailability";
import { adaptCalendarDetail, useCalendarDetail } from "./calendarDetail";

const projection = { fixed: ["id", "target", "enabled", "timezone"] } as const;
const enabledFilter = { kind: "comparison", field: "enabled", operator: "equals", value: true } as const;

const calendarConfig: CollectionConfig<BookingConfiguration> = {
  ...bookingConfigurationConfig,
  slug: "booking-calendar-items",
  labels: { singularKey: "booking:calendar.item", pluralKey: "booking:calendar.items" },
  defaultColumns: ["target", "timezone"],
  fields: bookingConfigurationConfig.fields.map((field) =>
    field.name === "target"
      ? {
          ...field,
          list: {
            renderCell: ({ row }: { row: BookingConfiguration }) =>
              row.target ? (
                <InventoryItem name={row.target.value.name} globalId={row.target.globalId} compact size="xs" />
              ) : (
                <UnknownItem size="xs" />
              ),
          },
        }
      : field,
  ),
};

function browserToday(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

export default function CalendarPage() {
  const { t } = useTranslation("booking");
  const { date, target } = useSearch({ from: "/booking/calendar" });
  const navigate = useNavigate({ from: "/booking/calendar" });
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const request = useMemo(() => ({ token, depth: 1, projection, baseFilter: enabledFilter }), [token]);
  const table = useApiV2TableList({
    resourceName: "booking-configurations",
    config: calendarConfig,
    documentSchema: BookingConfigurationSchema,
    request,
    query: { keepPreviousData: true },
    table: {
      initialState: { visibleFields: ["target", "timezone"] },
      features: { columns: false },
      queryString: { parameterPrefix: "calendar-items", tableId: "booking-calendar-items" },
    },
  });
  const rows = table.tableProps.rows as readonly BookingConfigurationRow[];
  const availabilityRows = rows.flatMap((row) =>
    row.target && row.timezone ? [{ globalId: row.target.globalId, timezone: row.timezone }] : [],
  );
  const availability = useCalendarAvailability(availabilityRows, date, token);
  const selectedOnPage = rows.find((row) => row.target?.globalId === target);
  const selectedLookup = useBookableItem(selectedOnPage ? undefined : target, token);
  const selected =
    selectedOnPage ??
    (selectedLookup.data
      ? {
          id: selectedLookup.data.configurationId,
          enabled: true,
          timezone: selectedLookup.data.timezone,
          updatedAt: null,
          target: {
            relationTo: "instruments" as const,
            globalId: selectedLookup.data.globalId,
            value: {
              id: selectedLookup.data.targetId,
              name: selectedLookup.data.name,
              deleted: false,
            },
          },
        }
      : undefined);
  const detail = useCalendarDetail(target, date, selected?.timezone, token);
  const adapted =
    selected?.timezone && detail.data ? adaptCalendarDetail(detail.data, date, selected.timezone) : undefined;

  const changeSearch = (changes: { date?: string; target?: string }) =>
    void navigate({ search: (current) => ({ ...current, ...changes }), replace: true });
  const today = selected?.timezone
    ? currentWallClock(new Date().toISOString(), selected.timezone).date
    : browserToday();
  const now = new Date();

  const rowActions = useMemo<TableListRowActions<BookingConfigurationRow>>(
    () => ({
      id: "booking-actions",
      label: t("calendar.actions.label"),
      width: 100,
      renderCell: ({ row }) =>
        row.target ? (
          <Link
            className={buttonVariants({ size: "sm" })}
            to="/booking/calendar/bookings/add"
            search={{ date, target: row.target.globalId }}
          >
            {t("calendar.actions.book")}
          </Link>
        ) : null,
      renderInteraction: () => null,
    }),
    [date, t],
  );

  return (
    <main className="space-y-6 p-4 sm:p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold">{t("calendar.title")}</h1>
        <p className="text-sm text-muted-foreground">{t("calendar.description")}</p>
      </header>
      <div className="flex flex-wrap items-end gap-2">
        <div className="space-y-2">
          <Label htmlFor="booking-calendar-date">{t("calendar.date")}</Label>
          <Input
            id="booking-calendar-date"
            className="w-auto"
            type="date"
            value={date}
            onChange={(event) => changeSearch({ date: event.currentTarget.value })}
          />
        </div>
        <Button
          type="button"
          size="icon"
          variant="outline"
          aria-label={t("calendar.previousDay")}
          onClick={() => changeSearch({ date: addCalendarDays(date, -1) })}
        >
          <ChevronLeftIcon />
        </Button>
        <Button type="button" variant="outline" onClick={() => changeSearch({ date: today })}>
          {t("calendar.today")}
        </Button>
        <Button
          type="button"
          size="icon"
          variant="outline"
          aria-label={t("calendar.nextDay")}
          onClick={() => changeSearch({ date: addCalendarDays(date, 1) })}
        >
          <ChevronRightIcon />
        </Button>
      </div>
      <TableList
        {...table.tableProps}
        onRowOpen={(row) => row.target && changeSearch({ target: row.target.globalId })}
        uiColumns={[
          {
            id: "availability",
            label: t("calendar.availability"),
            minWidth: 320,
            width: 520,
            renderCell: (row) => {
              if (!row.target || !row.timezone) return t("calendar.availabilityUnavailable");
              if (availability.isPending) return t("calendar.availabilityLoading");
              if (availability.isError || !availability.data) return t("calendar.availabilityUnavailable");
              const bounds = zonedDayBounds(date, row.timezone);
              const periodStart = new Date(bounds.start);
              const periodEnd = new Date(bounds.end);
              return (
                <AvailabilityBar
                  intervals={availability.data.get(row.target.globalId) ?? []}
                  periodStart={periodStart}
                  periodEnd={periodEnd}
                  now={now >= periodStart && now < periodEnd ? now : undefined}
                  timeZone={row.timezone}
                  itemName={row.target.value.name}
                />
              );
            },
          },
        ]}
        rowActions={rowActions}
      />
      {selected?.target && selected.timezone && (
        <section className="space-y-4" aria-label={t("calendar.detail", { itemName: selected.target.value.name })}>
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-xl font-semibold">{selected.target.value.name}</h2>
            <Link
              className={buttonVariants()}
              to="/booking/calendar/bookings/add"
              search={{ date, target: selected.target.globalId }}
            >
              {t("calendar.actions.book")}
            </Link>
          </div>
          {detail.isPending && <p>{t("calendar.detailLoading")}</p>}
          {detail.isError && <p>{t("calendar.detailUnavailable")}</p>}
          {adapted && (
            <>
              <DayTimeline
                date={date}
                timezone={selected.timezone}
                events={adapted.timeline}
                startWindow={8 * 60}
                endWindow={18 * 60}
                nowMinute={
                  currentWallClock(now.toISOString(), selected.timezone).date === date
                    ? currentWallClock(now.toISOString(), selected.timezone).minute
                    : undefined
                }
              />
              <ol className="space-y-2" aria-label={t("calendar.agenda")}>
                {adapted.agenda.map((event) => (
                  <li key={event.id} className="rounded-md border p-3">
                    <p className="font-medium">{event.privacy === "busy" ? t("calendar.busy") : event.bookedBy}</p>
                    <p>{event.period}</p>
                    {event.privacy === "full" && event.purpose && <p>{event.purpose}</p>}
                    {event.canEdit && (
                      <Link
                        className={buttonVariants({ variant: "link" })}
                        to="/booking/calendar/bookings/$id"
                        params={{ id: String(event.id) }}
                        search={{ date, target }}
                      >
                        {t("calendar.actions.edit")}
                      </Link>
                    )}
                  </li>
                ))}
              </ol>
            </>
          )}
        </section>
      )}
    </main>
  );
}
