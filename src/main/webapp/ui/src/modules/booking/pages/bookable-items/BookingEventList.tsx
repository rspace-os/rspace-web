import { Link } from "@tanstack/react-router";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { Booking } from "@/modules/booking/domain/booking";
import type { CollectionConfig } from "@/modules/common/collection/collectionConfig";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import { useTableList } from "@/modules/common/table-list/useTableList";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/modules/common/ui/empty";
import { UserBadge } from "@/modules/common/ui/user-badge";
import { type BookingEventPeriod, fetchBookableItemEvents } from "./bookableItemEvents";

type BookingEventListProps = {
  globalId: string;
  timezone: string;
  period: BookingEventPeriod;
  cutoff: string;
};

function BookingEventTable({ globalId, timezone, period, cutoff }: BookingEventListProps) {
  const { t, i18n } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const formatter = useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: timezone,
      }),
    [i18n.language, timezone],
  );
  const config = useMemo(
    () =>
      resolveCollectionConfig({
        slug: `bookable-item-${period}-events`,
        idField: "id",
        useAsTitle: "kind",
        defaultColumns: ["start", "kind", "bookedBy", "purpose"],
        pagination: { defaultLimit: 10, limits: [10] },
        labels: {
          singularKey: "booking:calendar.event",
          pluralKey:
            period === "upcoming" ? "booking:bookableItemDetails.upcoming" : "booking:bookableItemDetails.past",
        },
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
                <time dateTime={row.start}>{formatter.formatRange(new Date(row.start), new Date(row.end))}</time>
              ),
            },
          },
          { name: "end", type: "dateTime", labelKey: "booking:myBookings.fields.end", list: false },
          {
            name: "kind",
            type: "text",
            labelKey: "booking:bookableItemDetails.events.kind",
            label: t("bookableItemDetails.events.kind"),
            list: {
              width: 180,
              renderCell: ({ row }) =>
                t(row.kind === "MAINTENANCE" ? "bookings.form.typeBlockout" : "bookings.form.typeBooking"),
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
            labelKey: "booking:bookableItemDetails.events.actor",
            list: false,
          },
          {
            name: "purpose",
            type: "text",
            nullable: true,
            labelKey: "booking:bookableItemDetails.events.purpose",
            list: {
              renderCell: ({ row }) => (row.kind === "MAINTENANCE" || row.privacy === "full" ? row.purpose : null),
            },
          },
        ],
      } satisfies CollectionConfig<Booking>),
    [formatter, period, t],
  );
  const table = useTableList<Booking>({
    config,
    dataSource: {
      type: "remote",
      queryKey: (state) => [
        "api-v2",
        "bookings",
        "bookable-item-events",
        globalId,
        period,
        cutoff,
        state.page.pageIndex,
        state.page.pageSize,
      ],
      fetch: async (state, { signal }) => {
        const result = await fetchBookableItemEvents({
          globalId,
          period,
          cutoff,
          page: state.page.pageIndex,
          limit: state.page.pageSize,
          token,
          signal,
        });
        return { rows: result.docs, rowCount: result.totalDocs };
      },
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
      width: 130,
      minWidth: 120,
      renderCell: ({ row }) =>
        row.canEdit ? (
          <Link
            className={buttonVariants({ size: "sm" })}
            to="/booking/calendar/bookings/$id"
            params={{ id: String(row.id) }}
          >
            {t("bookableItemDetails.events.edit")}
          </Link>
        ) : null,
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
      hideHeader
      emptyDescription={t("bookableItemDetails.events.empty")}
      presentations={{ table: "wide", cards: "narrow" }}
      rowActions={rowActions}
    />
  );
}

export function BookingEventList(props: BookingEventListProps) {
  return <BookingEventTable {...props} key={`${props.globalId}:${props.period}:${props.cutoff}`} />;
}
