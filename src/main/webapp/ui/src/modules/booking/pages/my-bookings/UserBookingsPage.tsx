import { queryOptions, useQuery } from "@tanstack/react-query";
import { CalendarClockIcon, CalendarX2Icon, HistoryIcon, RefreshCwIcon } from "lucide-react";
import { useQueryState } from "nuqs";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { type BookingListDocument, BookingListDocumentTableValidation } from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { bookingKeys } from "@/modules/booking/domain/bookingKeys";
import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useAlignedMinute } from "@/modules/common/hooks/useAlignedMinute";
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/types";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { Heading } from "@/modules/common/ui/typography";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import { type BookingRow, BookingRowActions } from "./BookingRowActions";
import { upcomingBookingWhere } from "./bookingCountFilter";
import { bookingListConfig } from "./bookingList";
import { type MyBookingsPeriod, myBookingsPeriodParser, myBookingsPeriods } from "./search";

const fetchBookingList: typeof fetch = (input, init = {}) => {
  const url = new URL(String(input), window.location.origin);
  url.searchParams.set("fields[instruments]", "id,globalId,name,deleted");
  return fetchWithUiToken(url.toString(), init);
};

const UpcomingCountSchema = v.object({ totalDocs: v.number(), totalIsLowerBound: v.optional(v.boolean()) });
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
  ],
} as const;

export type UserBookingsPageProps = {
  requesterId: number;
  title: string;
};

export async function fetchUpcomingBookingCount(
  requesterId: number,
  asOf: Date,
  token: string,
  signal?: AbortSignal,
): Promise<v.InferOutput<typeof UpcomingCountSchema>> {
  const parameters = new URLSearchParams({
    where: upcomingBookingWhere(requesterId, asOf.toISOString()),
    max: "100",
  });
  const response = await fetchWithUiToken(`/api/v2/bookings/count?${parameters}`, {
    headers: apiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking count request failed (${response.status})`);
  return parseOrThrow(UpcomingCountSchema, await response.json());
}

export function UserBookingsPage({ requesterId, title }: UserBookingsPageProps) {
  const [period, setPeriod] = useQueryState("period", myBookingsPeriodParser);
  const { t, i18n } = useTranslation("booking");
  const periodText = {
    upcoming: {
      label: t("myBookings.period.upcoming"),
      empty: t("myBookings.empty.upcoming"),
      Icon: CalendarClockIcon,
    },
    past: { label: t("myBookings.period.past"), empty: t("myBookings.empty.past"), Icon: HistoryIcon },
    cancelled: {
      label: t("myBookings.period.cancelled"),
      empty: t("myBookings.empty.cancelled"),
      Icon: CalendarX2Icon,
    },
  } as const;

  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const preferences = useBookingDisplayPreferences();
  const listConfig = useMemo(
    () => bookingListConfig(preferences.timeZone, preferences.timeFormat),
    [preferences.timeFormat, preferences.timeZone],
  );
  const asOf = useAlignedMinute();
  const asOfDate = useMemo(() => new Date(asOf), [asOf]);
  const baseFilter = useMemo<FilterExpression<BookingListDocument>>(
    () => ({
      kind: "and",
      children: [
        { kind: "comparison", field: "requesterId", operator: "equals", value: requesterId },
        { kind: "comparison", field: "kind", operator: "equals", value: "BOOKING" },
        // Cancelled bookings get their own period at any time, so upcoming and past list only confirmed ones.
        ...(period === "cancelled"
          ? [{ kind: "comparison" as const, field: "state" as const, operator: "equals" as const, value: "CANCELLED" }]
          : [
              { kind: "comparison" as const, field: "state" as const, operator: "equals" as const, value: "CONFIRMED" },
              {
                kind: "comparison" as const,
                field: "end" as const,
                operator: period === "upcoming" ? ("greaterThan" as const) : ("lessThanOrEqual" as const),
                value: asOfDate,
              },
            ]),
      ],
    }),
    [asOfDate, period, requesterId],
  );
  const request = useMemo(
    () => ({
      token,
      authScope: currentUser.id,
      depth: 1,
      fetch: fetchBookingList,
      projection,
      baseFilter,
      validateRows: BookingListDocumentTableValidation.validateRows,
    }),
    [baseFilter, currentUser.id, token],
  );
  const table = useApiV2TableList({
    resourceName: "bookings",
    config: listConfig,
    documentSchema: BookingListDocumentTableValidation.documentSchema,
    request,
    query: { keepPreviousData: true },
    table: {
      queryString: { parameterPrefix: "my-bookings", tableId: "booking-my-bookings" },
    },
  });
  const upcomingCount = useQuery({
    ...upcomingBookingCountQuery(requesterId, asOfDate, token),
  });
  const rowActions = useMemo<TableListRowActions<BookingRow>>(
    () => ({
      id: "actions",
      label: t("myBookings.actions.label"),
      // Three 40px icon controls and "More actions", with their gaps and the cell padding, on one line.
      width: 200,
      minWidth: 200,
      renderCell: ({ row }) => (
        <BookingRowActions
          row={row}
          token={token}
          timeZone={preferences.timeZone}
          timeFormat={preferences.timeFormat}
        />
      ),
      renderInteraction: () => null,
    }),
    [preferences.timeZone, t, token],
  );

  const selectPeriod = (nextPeriod: MyBookingsPeriod) => {
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
            {myBookingsPeriods.map((value) => {
              const { Icon, label } = periodText[value];
              return (
                <Button
                  key={value}
                  type="button"
                  size="sm"
                  variant={period === value ? "secondary" : "outline"}
                  aria-label={label}
                  aria-pressed={period === value}
                  onClick={() => selectPeriod(value)}
                >
                  <Icon aria-hidden="true" />
                  {label}
                  {value === "upcoming" && upcomingCount.isSuccess && (
                    <Badge
                      variant={period === "upcoming" ? "secondary" : "outline"}
                      className="pointer-events-none min-w-5 px-1"
                      aria-label={
                        upcomingCount.data.totalIsLowerBound
                          ? t("myBookings.count.lowerBoundAccessible")
                          : t("myBookings.count.accessible", { count: upcomingCount.data.totalDocs })
                      }
                    >
                      {upcomingCount.data.totalIsLowerBound
                        ? t("myBookings.count.lowerBound")
                        : formatNumber(upcomingCount.data.totalDocs, i18n.resolvedLanguage ?? i18n.language)}
                    </Badge>
                  )}
                  {value === "upcoming" && upcomingCount.isPending && (
                    <span role="status" className="sr-only">
                      {t("myBookings.count.loading")}
                    </span>
                  )}
                </Button>
              );
            })}
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
          presentations={{ table: "wide", cards: "narrow" }}
          emptyDescription={periodText[period].empty}
          rowActions={rowActions}
          hideHeader
        />
      </main>
    </TooltipProvider>
  );
}

export const upcomingBookingCountQuery = (requesterId: number, asOf: Date, token: string) =>
  queryOptions({
    queryKey: [...bookingKeys.all, "count", "upcoming", requesterId, asOf.toISOString()] as const,
    queryFn: ({ signal }) => fetchUpcomingBookingCount(requesterId, asOf, token, signal),
  });
