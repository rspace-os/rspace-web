import { useQuery } from "@tanstack/react-query";
import { CalendarClockIcon, CalendarX2Icon, HistoryIcon, RefreshCwIcon } from "lucide-react";
import { useQueryState } from "nuqs";
import { useEffect, useMemo, useRef, useState } from "react";
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
import { formatNumber } from "@/modules/common/i18n/localeFormat";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { useApiV2TableList } from "@/modules/common/table-list/hooks/apiV2TableList";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/types";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { Heading } from "@/modules/common/ui/typography";
import { requesterValueField } from "../ownerPreset";
import {
  type BookingRow,
  BookingRowActions,
  type CancelledBookingRow,
  visibleBookingRowActions,
} from "./BookingRowActions";
import { upcomingBookingWhere } from "./bookingCountFilter";
import { bookingListConfig } from "./bookingList";
import { type MyBookingsPeriod, myBookingsPeriodParser, myBookingsPeriods } from "./search";

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
      where: upcomingBookingWhere(requesterId, asOf.toISOString()),
      max: 100,
    },
    { signal },
  );
  if (typeof result.totalDocs !== "number") throw new Error("Booking count missing totalDocs");
  return { ...result, totalDocs: result.totalDocs };
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
        { kind: "comparison", field: requesterValueField, operator: "equals", value: requesterId },
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
      authScope: currentUser.id,
      depth: 1,
      projection,
      baseFilter,
      validateRows: BookingListDocumentTableValidation.validateRows,
    }),
    [baseFilter, currentUser.id],
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
        where: upcomingBookingWhere(requesterId, asOfDate.toISOString()),
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
  const rows = table.tableProps.rows;
  const periodControlsRef = useRef<HTMLDivElement>(null);
  // A page-level live region: the cancelled row, and the dialog's own status message, unmount on refetch.
  const [cancelAnnouncement, setCancelAnnouncement] = useState("");
  const [pendingFocus, setPendingFocus] = useState<CancelledBookingRow | null>(null);
  useEffect(() => {
    // Waits until the refetched rows no longer include the cancelled booking, so focus never lands on it.
    if (pendingFocus === null || rows.some(({ id }) => id === pendingFocus.bookingId)) return;
    setPendingFocus(null);
    const { position } = pendingFocus;
    const remaining = position?.list.isConnected ? visibleBookingRowActions(position.list) : [];
    const neighbourId = position
      ? remaining[Math.min(position.index, remaining.length - 1)]?.dataset.bookingRowActions
      : undefined;
    const focusTarget = () => {
      const neighbour =
        neighbourId && position?.list.isConnected
          ? visibleBookingRowActions(position.list).find((actions) => actions.dataset.bookingRowActions === neighbourId)
          : undefined;
      return (
        neighbour?.querySelector<HTMLElement>("a, button:not(:disabled)") ??
        periodControlsRef.current?.querySelector<HTMLElement>('[aria-pressed="true"]')
      );
    };
    const target = focusTarget();
    target?.focus();
    // The card view can render a row's actions once more after the refetch commits, replacing the focused
    // control. Removal does not blur in every engine, so watch the list briefly and follow the booking.
    if (target && position?.list.isConnected) {
      const observer = new MutationObserver(() => {
        if (target.isConnected) return;
        observer.disconnect();
        if (document.activeElement === null || document.activeElement === document.body) focusTarget()?.focus();
      });
      observer.observe(position.list, { childList: true, subtree: true });
      window.setTimeout(() => observer.disconnect(), 1000);
    }
  }, [pendingFocus, rows]);
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
          timeZone={preferences.timeZone}
          timeFormat={preferences.timeFormat}
          onCancelled={(cancelled) => {
            // The dialog has already refetched the bookings; another refetch would re-mount the rows and drop focus.
            setCancelAnnouncement(
              t("myBookings.cancelled.announcement", { itemName: cancelled.itemName, period: cancelled.period }),
            );
            setPendingFocus(cancelled);
          }}
        />
      ),
      renderInteraction: () => null,
    }),
    [preferences.timeZone, t],
  );

  const selectPeriod = (nextPeriod: MyBookingsPeriod) => {
    if (nextPeriod === period) return;
    setPendingFocus(null);
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
        <div ref={periodControlsRef} className="space-y-2">
          <ButtonGroup aria-label={t("myBookings.period.legend")}>
            {myBookingsPeriods.map((value) => {
              const { Icon, label } = periodText[value];
              return (
                <Button
                  key={value}
                  type="button"
                  size="sm"
                  variant={period === value ? "secondary" : "outline"}
                  aria-label={
                    value === "upcoming" && upcomingCount.isSuccess
                      ? t("myBookings.period.withCount", {
                          label,
                          count: upcomingCount.data.totalIsLowerBound
                            ? t("myBookings.count.lowerBound")
                            : formatNumber(upcomingCount.data.totalDocs, i18n.resolvedLanguage ?? i18n.language),
                        })
                      : label
                  }
                  aria-pressed={period === value}
                  onClick={() => selectPeriod(value)}
                >
                  <Icon aria-hidden="true" />
                  {label}{" "}
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
        <p role="status" aria-live="polite" className="sr-only">
          {cancelAnnouncement}
        </p>
      </main>
    </TooltipProvider>
  );
}
