// PROTOTYPE ONLY. Explore the recurring booking workflow (plan 004) on top of the production booking form,
// day timeline, item card and lists. Network reads are served by story-local MSW handlers; nothing is written.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */

import { Link } from "@tanstack/react-router";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import { BookingSummaryCard } from "@/modules/booking/components/BookingSummaryCard";
import type { Booking } from "@/modules/booking/domain/booking";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingHourCycle } from "@/modules/booking/domain/bookingTime";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { buttonVariants } from "@/modules/common/ui/button";
import { Heading } from "@/modules/common/ui/typography";
import { UserBadge } from "@/modules/common/ui/user-badge";
import {
  currentBooking,
  DISPLAY_TIMEZONE,
  item,
  SERIES_POSITION,
  seriesBookings,
  seriesOf,
  storedBookings,
  toListDocument,
} from "./RecurringBookingFableModel";
import { RepeatBadge, SeriesFacts } from "./RecurringBookingFableSeries.story";

// ---------------------------------------------------------------------------
// Series representation in lists: the production TableList and booking summary card.
// ---------------------------------------------------------------------------

type EventRow = Booking & { seriesLabel: string | null };

const listRows: readonly EventRow[] = [
  seriesBookings[SERIES_POSITION - 1],
  seriesBookings[SERIES_POSITION],
  storedBookings[2],
  storedBookings[0],
].map((booking) => {
  const series = seriesOf(booking);
  return { ...booking, seriesLabel: series ? `${series.position} of ${series.count}` : null };
});

function SeriesEventTable() {
  const { t, i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const formatter = React.useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, {
        dateStyle: "medium",
        timeStyle: "short",
        hourCycle: bookingHourCycle(timeFormat),
        timeZone: DISPLAY_TIMEZONE,
      }),
    [i18n.language, timeFormat],
  );
  const config = React.useMemo(
    () =>
      resolveCollectionConfig({
        slug: "recurring-prototype-events",
        idField: "id",
        useAsTitle: "start",
        defaultColumns: ["start", "seriesLabel", "bookedBy", "purpose"],
        pagination: { defaultLimit: 10, limits: [10] },
        labels: { singularKey: "booking:calendar.event", pluralKey: "booking:bookableItemDetails.upcoming" },
        fields: [
          { name: "id", type: "number", labelKey: "booking:calendar.fields.id", list: false },
          {
            name: "start",
            type: "dateTime",
            labelKey: "booking:bookings.form.time",
            list: {
              width: 280,
              minWidth: 220,
              dependencies: ["end"],
              renderCell: ({ row }) => (
                <BookingInstrumentTimeTooltip
                  start={row.start}
                  end={row.end}
                  displayTimeZone={DISPLAY_TIMEZONE}
                  instrumentTimeZone={row.timezone}
                >
                  <time dateTime={row.start}>{formatter.formatRange(new Date(row.start), new Date(row.end))}</time>
                </BookingInstrumentTimeTooltip>
              ),
            },
          },
          { name: "end", type: "dateTime", labelKey: "booking:myBookings.fields.end", list: false },
          {
            name: "seriesLabel",
            type: "text",
            nullable: true,
            labelKey: "booking:calendar.event",
            label: "Repeats",
            list: {
              width: 220,
              renderCell: ({ row }) =>
                row.seriesLabel ? (
                  <span className="flex flex-wrap items-center gap-2">
                    <RepeatBadge />
                    <span className="text-muted-foreground">{row.seriesLabel}</span>
                  </span>
                ) : null,
            },
          },
          {
            name: "bookedBy",
            type: "text",
            nullable: true,
            labelKey: "booking:bookableItemDetails.events.actor",
            label: t("bookableItemDetails.events.actor"),
            list: {
              width: 220,
              renderCell: ({ row }) =>
                row.privacy === "busy" || row.bookedBy === null ? (
                  t("bookableItemDetails.events.busy")
                ) : (
                  <UserBadge name={row.bookedBy} />
                ),
            },
          },
          {
            name: "purpose",
            type: "text",
            nullable: true,
            labelKey: "booking:bookableItemDetails.events.purpose",
            list: { renderCell: ({ row }) => (row.privacy === "full" ? row.purpose : null) },
          },
        ],
      } satisfies CollectionConfig<EventRow>),
    [formatter, t],
  );
  const table = useTableList<EventRow>({
    config,
    dataSource: { type: "client", rows: listRows },
    features: { filtering: false, sorting: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });
  const rowActions = React.useMemo<TableListRowActions<EventRow>>(
    () => ({
      id: "actions",
      label: t("calendar.actions.label"),
      width: 160,
      minWidth: 140,
      renderCell: ({ row }) =>
        row.privacy === "full" ? (
          <Link
            className={buttonVariants({ size: "sm", variant: "outline" })}
            to="/booking/calendar/bookings/$id"
            params={{ id: String(row.id) }}
          >
            {t("calendar.actions.viewDetails")}
          </Link>
        ) : null,
      renderInteraction: () => null,
    }),
    [t],
  );
  return (
    <TableList
      {...table.tableProps}
      hideHeader
      emptyDescription={t("bookableItemDetails.events.empty")}
      presentations={{ table: "wide", cards: "narrow" }}
      rowActions={rowActions}
    />
  );
}

export function SeriesInListsPrototype() {
  const series = seriesOf(currentBooking);
  return (
    <main className="space-y-8 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {item.name}
      </Heading>
      <div className="max-w-4xl space-y-8">
        <SeriesFacts booking={currentBooking} />
        <section className="space-y-3">
          <Heading level={5} as="h2">
            Upcoming events
          </Heading>
          <SeriesEventTable />
        </section>
        <section className="space-y-3">
          <Heading level={5} as="h2">
            Summary card
          </Heading>
          <div className="max-w-md space-y-2">
            {series ? (
              <p className="flex flex-wrap items-center gap-2 text-sm">
                <RepeatBadge />
                <span className="text-muted-foreground">
                  {series.position} of {series.count}
                </span>
              </p>
            ) : null}
            <BookingSummaryCard booking={toListDocument(currentBooking)} timeZone={DISPLAY_TIMEZONE} />
          </div>
        </section>
      </div>
    </main>
  );
}
