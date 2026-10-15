import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { BookingInstrumentTimeTooltip } from "@/modules/booking/components/BookingInstrumentTimeTooltip";
import type { Booking } from "@/modules/booking/domain/booking";
import { useBookingTimeFormat } from "@/modules/booking/domain/bookingDisplayPreferences";
import { formatBookingPeriod } from "@/modules/booking/domain/bookingTime";
import { bookableItemEventsQuery } from "@/modules/booking/pages/bookable-items/bookableItemEvents";
import type { CollectionConfig, CollectionTranslationKey } from "@/modules/common/collection/config";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useTableList } from "@/modules/common/table-list/hooks/state";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import type { CollectionQueryState } from "@/modules/common/table-list/types";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { UserBadge } from "@/modules/common/ui/user-badge";
import type { BookingEventPeriod } from "./bookableItemEvents";

export type BookingEventListProps = {
  globalId: string;
  timezone: string;
  period: BookingEventPeriod;
  cutoff: string;
};

export function BookingEventList(props: BookingEventListProps) {
  return <BookingEventTable {...props} key={`${props.globalId}:${props.period}:${props.cutoff}`} />;
}

function BookingEventTable({ globalId, timezone, period, cutoff }: BookingEventListProps) {
  const { t, i18n } = useTranslation("booking");
  const timeFormat = useBookingTimeFormat();
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const config = useMemo(
    () =>
      resolveCollectionConfig({
        slug: `bookable-item-${period}-events`,
        idField: "id",
        useAsTitle: "kind",
        defaultColumns: ["start", "kind", "bookedBy", "purpose"],
        pagination: { defaultLimit: 10, limits: [10] },
        labels: {
          singularKey: "booking:calendar.event" satisfies CollectionTranslationKey,
          pluralKey:
            period === "upcoming" ? "booking:bookableItemDetails.upcoming" : "booking:bookableItemDetails.past",
        },
        fields: [
          {
            name: "id",
            type: "number",
            labelKey: "booking:calendar.fields.id" satisfies CollectionTranslationKey,
            list: false,
          },
          {
            name: "start",
            type: "dateTime",
            labelKey: "booking:bookings.form.time" satisfies CollectionTranslationKey,
            list: {
              width: 280,
              minWidth: 220,
              dependencies: ["end"],
              renderCell: ({ row }) => (
                <BookingInstrumentTimeTooltip
                  start={row.start}
                  end={row.end}
                  displayTimeZone={timezone}
                  instrumentTimeZone={row.timezone}
                >
                  <time dateTime={row.start}>
                    {formatBookingPeriod(row.start, row.end, timezone, i18n.language, timeFormat)}
                  </time>
                </BookingInstrumentTimeTooltip>
              ),
            },
          },
          {
            name: "end",
            type: "dateTime",
            labelKey: "booking:myBookings.fields.end" satisfies CollectionTranslationKey,
            list: false,
          },
          {
            name: "kind",
            type: "text",
            labelKey: "booking:bookableItemDetails.events.kind" satisfies CollectionTranslationKey,
            list: {
              width: 180,
              renderCell: ({ row }) =>
                row.kind === "MAINTENANCE" ? t("bookings.form.typeBlockout") : t("bookings.form.typeBooking"),
            },
          },
          {
            name: "bookedBy",
            type: "text",
            nullable: true,
            labelKey: "booking:bookableItemDetails.events.actor" satisfies CollectionTranslationKey,
            list: {
              width: 220,
              renderCell: ({ row }) => {
                if (row.kind === "MAINTENANCE") {
                  return row.createdBy ? <UserBadge name={row.createdBy} /> : t("bookings.maintenanceLabel");
                }
                return row.privacy === "busy" || row.bookedBy === null ? (
                  t("bookableItemDetails.events.busy")
                ) : (
                  <UserBadge name={row.bookedBy} />
                );
              },
            },
          },
          {
            name: "createdBy",
            type: "text",
            nullable: true,
            labelKey: "booking:bookableItemDetails.events.actor" satisfies CollectionTranslationKey,
            list: false,
          },
          {
            name: "purpose",
            type: "text",
            nullable: true,
            labelKey: "booking:bookableItemDetails.events.purpose" satisfies CollectionTranslationKey,
            list: {
              renderCell: ({ row }) => (row.kind === "MAINTENANCE" || row.privacy === "full" ? row.purpose : null),
            },
          },
        ],
      } satisfies CollectionConfig<Booking>),
    [i18n.language, period, t, timeFormat, timezone],
  );
  const queryPrefix = ["api-v2", "bookings", "bookable-item-events", globalId, period, cutoff];
  const totals = useQuery({
    queryKey: [...queryPrefix, "totals"],
    staleTime: Infinity,
    retry: false,
    queryFn: async ({ signal }) => {
      const result = await fetchBookableItemEvents({
        globalId,
        period,
        cutoff,
        page: 0,
        limit: 1,
        token,
        signal,
        totals: true,
      });
      if (result.totalDocs === undefined) throw new Error("Booking totals response omitted totalDocs");
      return result.totalDocs;
    },
  });
  const eventsOptions = (state: CollectionQueryState<Booking>) =>
    bookableItemEventsQuery({
      globalId,
      period,
      cutoff,
      page: state.page.pageIndex,
      limit: state.page.pageSize,
      token,
    });
  const table = useTableList<Booking>({
    config,
    dataSource: {
      type: "remote",
      queryKey: (state) => eventsOptions(state).queryKey,
      fetch: (state, { signal }) => eventsOptions(state).queryFn({ signal }),
      keepPreviousData: true,
      retry: false,
    },
    features: { filtering: false, sorting: false, columns: false },
    queryString: false,
    reserveEmptyRows: false,
  });
  const rowActions = useMemo<TableListRowActions<Booking>>(
    () => ({
      id: "actions",
      label: t("calendar.actions.label"),
      width: 220,
      minWidth: 180,
      renderCell: ({ row }) => (
        <div className="flex gap-2">
          {row.privacy === "full" ? (
            <Link
              className={buttonVariants({ size: "sm", variant: "outline" })}
              to="/booking/calendar/bookings/$id"
              params={{ id: String(row.id) }}
            >
              {t("calendar.actions.viewDetails")}
            </Link>
          ) : null}
          {row.canEdit ? (
            <Link
              className={buttonVariants({ size: "sm" })}
              to="/booking/calendar/bookings/$id/edit"
              params={{ id: String(row.id) }}
            >
              {t("bookableItemDetails.events.edit")}
            </Link>
          ) : null}
        </div>
      ),
      renderInteraction: () => null,
    }),
    [t],
  );

  if (table.tableProps.status === "error") {
    return (
      <Empty className="border">
        <EmptyHeader>
          <EmptyTitle>{t("bookableItemDetails.events.error.title")}</EmptyTitle>
          <EmptyDescription>{t("bookableItemDetails.events.error.description")}</EmptyDescription>
        </EmptyHeader>
        <Button type="button" variant="outline" onClick={() => void table.refetch()}>
          {commonT("actions.retry")}
        </Button>
      </Empty>
    );
  }

  return (
    <TableList
      {...table.tableProps}
      features={{
        ...table.tableProps.features,
        pagination:
          table.tableProps.features.pagination === false
            ? false
            : { ...table.tableProps.features.pagination, rowCount: totals.data },
      }}
      hideHeader
      emptyDescription={t("bookableItemDetails.events.empty")}
      presentations={{ table: "wide", cards: "narrow" }}
      rowActions={rowActions}
    />
  );
}
