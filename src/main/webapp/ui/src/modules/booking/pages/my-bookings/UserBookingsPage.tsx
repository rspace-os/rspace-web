import { useQuery } from "@tanstack/react-query";
import { CalendarClockIcon, HistoryIcon, RefreshCwIcon } from "lucide-react";
import { useQueryState } from "nuqs";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  type BookingListDocument,
  BookingListDocumentTableValidation,
  mapBookingListDocument,
} from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import { countBookings, getCountBookingsQueryOptions } from "@/modules/common/api/v2/generated/bookings/bookings";
import type { ApiV2CountResult } from "@/modules/common/api/v2/generated/models/apiV2CountResult";
import { useAlignedMinute } from "@/modules/common/hooks/useAlignedMinute";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/types";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { Heading } from "@/modules/common/ui/typography";
import { type BookingRow, BookingRowActions } from "./BookingRowActions";
import { bookingListConfig } from "./bookingList";
import { type MyBookingsPeriod, myBookingsPeriodParser } from "./search";

const bookingListResource = {
  ...apiV2Resources.bookings,
  list: {
    ...apiV2Resources.bookings.list,
    queryKey: (params) =>
      apiV2Resources.bookings.list.queryKey({
        ...params,
        fields: { ...params?.fields, instruments: "id,globalId,name,deleted" },
      }),
    request: (params, options) =>
      apiV2Resources.bookings.list.request(
        {
          ...params,
          fields: { ...params?.fields, instruments: "id,globalId,name,deleted" },
        },
        options,
      ),
  },
} satisfies typeof apiV2Resources.bookings;

const projection = {
  fixed: [
    "id",
    "version",
    "target",
    "canViewConfiguration",
    "timezone",
    "start",
    "end",
    "state",
    "privacy",
    "purpose",
    "canEdit",
    "canCancel",
    "bookedBy",
    "createdAt",
    "updatedAt",
  ],
} as const;
const emptyDescriptionKeys = {
  upcoming: "myBookings.empty.upcoming",
  past: "myBookings.empty.past",
} as const satisfies Record<MyBookingsPeriod, string>;

export type UserBookingsPageProps = {
  requesterId: number;
  title: string;
};

export async function fetchUpcomingBookingCount(
  requesterId: number,
  asOf: Date,
  signal?: AbortSignal,
): Promise<ApiV2CountResult & { totalDocs: number }> {
  const result = await countBookings(
    {
      where: `requesterId==${requesterId};kind==BOOKING;state==CONFIRMED;end=gt=${asOf.toISOString()}`,
      max: 100,
    },
    { signal },
  );
  if (typeof result.totalDocs !== "number") throw new Error("Booking count missing totalDocs");
  return { ...result, totalDocs: result.totalDocs };
}

export function UserBookingsPage({ requesterId, title }: UserBookingsPageProps) {
  const [period, setPeriod] = useQueryState("period", myBookingsPeriodParser);
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const preferences = useBookingDisplayPreferences();
  const listConfig = useMemo(() => bookingListConfig(preferences.timeZone), [preferences.timeZone]);
  const asOf = useAlignedMinute();
  const asOfDate = useMemo(() => new Date(asOf), [asOf]);
  const baseFilter = useMemo<FilterExpression<BookingListDocument>>(
    () => ({
      kind: "and",
      children: [
        { kind: "comparison", field: "requester.value", operator: "equals", value: requesterId },
        { kind: "comparison", field: "kind", operator: "equals", value: "BOOKING" },
        ...(period === "upcoming"
          ? [
              { kind: "comparison" as const, field: "state" as const, operator: "equals" as const, value: "CONFIRMED" },
              { kind: "comparison" as const, field: "end" as const, operator: "greaterThan" as const, value: asOfDate },
            ]
          : [
              {
                kind: "or" as const,
                children: [
                  {
                    kind: "comparison" as const,
                    field: "end" as const,
                    operator: "lessThanOrEqual" as const,
                    value: asOfDate,
                  },
                  {
                    kind: "comparison" as const,
                    field: "state" as const,
                    operator: "equals" as const,
                    value: "CANCELLED",
                  },
                ],
              },
            ]),
      ],
    }),
    [asOfDate, period, requesterId],
  );
  const request = useMemo(
    () => ({
      depth: 1,
      projection,
      baseFilter,
      validateRows: BookingListDocumentTableValidation.validateRows,
    }),
    [baseFilter],
  );
  const table = useApiV2TableList({
    resource: bookingListResource,
    config: listConfig,
    documentSchema: BookingListDocumentTableValidation.documentSchema,
    mapDocument: mapBookingListDocument,
    request,
    query: { keepPreviousData: true },
    table: {
      queryString: { parameterPrefix: "my-bookings", tableId: "booking-my-bookings" },
    },
  });
  const upcomingCount = useQuery(
    getCountBookingsQueryOptions(
      {
        where: `requesterId==${requesterId};kind==BOOKING;state==CONFIRMED;end=gt=${asOfDate.toISOString()}`,
        max: 100,
      },
      {
        query: {
          select: (result) => {
            if (typeof result.totalDocs !== "number") throw new Error("Booking count missing totalDocs");
            return { ...result, totalDocs: result.totalDocs };
          },
        },
      },
    ),
  );
  const rowActions = useMemo<TableListRowActions<BookingRow>>(
    () => ({
      id: "actions",
      label: t("myBookings.actions.label"),
      width: 176,
      minWidth: 176,
      renderCell: ({ row }) => (
        <BookingRowActions
          row={row}
          timeZone={preferences.timeZone}
          onCancelled={async () => {
            await table.refetch();
          }}
        />
      ),
      renderInteraction: () => null,
    }),
    [preferences.timeZone, t],
  );

  const selectPeriod = (nextPeriod: "upcoming" | "past") => {
    if (nextPeriod === period) return;
    table.setPage({ ...table.state.page, pageIndex: 0 });
    void setPeriod(nextPeriod);
  };

  return (
    <TooltipProvider delay={250}>
      <main className="space-y-6 p-4 sm:p-8">
        <header className="space-y-1">
          <Heading level={3} as="h1">
            {title}
          </Heading>
          <p className="text-sm text-muted-foreground">
            {t("myBookings.timezone", { timezone: preferences.timeZone })}
          </p>
        </header>
        <div className="space-y-2">
          <ButtonGroup aria-label={t("myBookings.period.legend")}>
            <Button
              type="button"
              size="sm"
              variant={period === "upcoming" ? "secondary" : "outline"}
              aria-label={
                upcomingCount.isSuccess
                  ? t("myBookings.period.withCount", {
                      label: t("myBookings.period.upcoming"),
                      count: upcomingCount.data.totalIsLowerBound
                        ? t("myBookings.count.lowerBound")
                        : upcomingCount.data.totalDocs,
                    })
                  : t("myBookings.period.upcoming")
              }
              aria-pressed={period === "upcoming"}
              onClick={() => selectPeriod("upcoming")}
            >
              <CalendarClockIcon aria-hidden="true" />
              {t("myBookings.period.upcoming")}{" "}
              {upcomingCount.isSuccess && (
                <Badge
                  variant={period === "upcoming" ? "secondary" : "outline"}
                  className="pointer-events-none min-w-5 px-1"
                  aria-label={
                    upcomingCount.data.totalIsLowerBound
                      ? t("myBookings.count.lowerBoundAccessible")
                      : t("myBookings.count.accessible", { count: upcomingCount.data.totalDocs })
                  }
                >
                  {" "}
                  {upcomingCount.data.totalIsLowerBound
                    ? t("myBookings.count.lowerBound")
                    : upcomingCount.data.totalDocs}
                </Badge>
              )}
              {upcomingCount.isPending && (
                <span role="status" className="sr-only">
                  {t("myBookings.count.loading")}
                </span>
              )}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={period === "past" ? "secondary" : "outline"}
              aria-label={t("myBookings.period.past")}
              aria-pressed={period === "past"}
              onClick={() => selectPeriod("past")}
            >
              <HistoryIcon aria-hidden="true" />
              {t("myBookings.period.past")}
            </Button>
          </ButtonGroup>
          {upcomingCount.isError && (
            <div className="flex items-center gap-2 text-sm text-destructive" role="alert">
              <span>{t("myBookings.count.error")}</span>
              <Tooltip>
                <TooltipTrigger
                  render={
                    <Button
                      type="button"
                      size="icon-xs"
                      variant="outline"
                      aria-label={commonT("actions.retry")}
                      onClick={() => void upcomingCount.refetch()}
                    />
                  }
                >
                  <RefreshCwIcon aria-hidden="true" />
                </TooltipTrigger>
                <TooltipContent role="tooltip">{commonT("actions.retry")}</TooltipContent>
              </Tooltip>
            </div>
          )}
        </div>
        <TableList
          {...table.tableProps}
          variant="transparent"
          presentations={{ table: "wide", cards: "narrow" }}
          emptyDescription={t(emptyDescriptionKeys[period])}
          rowActions={rowActions}
          hideHeader
        />
      </main>
    </TooltipProvider>
  );
}
