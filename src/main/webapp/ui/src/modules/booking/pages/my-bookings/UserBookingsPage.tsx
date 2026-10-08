import { useQuery } from "@tanstack/react-query";
import { CalendarClockIcon, CalendarX2Icon, HistoryIcon, RefreshCwIcon } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { type BookingListDocument, BookingListDocumentTableValidation } from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useAlignedMinute } from "@/modules/booking/hooks/useAlignedMinute";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { ButtonGroup } from "@/modules/common/ui/button-group";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { Heading } from "@/modules/common/ui/typography";
import {
  type BookingRow,
  BookingRowActions,
  type CancelledBookingRow,
  visibleBookingRowActions,
} from "./BookingRowActions";
import { bookingListConfig } from "./bookingList";
import type { MyBookingsPeriod } from "./routes";

const UpcomingCountSchema = v.object({ totalDocs: v.number() });
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
  period: MyBookingsPeriod;
  onPeriodChange: (period: MyBookingsPeriod) => void;
};

export async function fetchUpcomingBookingCount(
  requesterId: number,
  asOf: Date,
  token: string,
  signal?: AbortSignal,
): Promise<number> {
  const parameters = new URLSearchParams({
    where: `requesterId==${requesterId};kind==BOOKING;state==CONFIRMED;end=gt=${asOf.toISOString()}`,
  });
  const response = await fetch(`/api/v2/bookings/count?${parameters}`, {
    headers: bookingApiV2Headers(token),
    signal,
  });
  if (!response.ok) throw new Error(`Booking count request failed (${response.status})`);
  return parseOrThrow(UpcomingCountSchema, await response.json()).totalDocs;
}

export function UserBookingsPage({ requesterId, title, period, onPeriodChange }: UserBookingsPageProps) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const { data: currentUser } = useCurrentUserQuery();
  const preferences = useBookingDisplayPreferences();
  const listConfig = useMemo(() => bookingListConfig(preferences.timeZone), [preferences.timeZone]);
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
    queryKey: ["api-v2", "bookings", "count", "upcoming", requesterId, asOfDate.toISOString()],
    queryFn: ({ signal }) => fetchUpcomingBookingCount(requesterId, asOfDate, token, signal),
  });
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
          token={token}
          timeZone={preferences.timeZone}
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
    [preferences.timeZone, t, token],
  );

  const selectPeriod = (nextPeriod: MyBookingsPeriod) => {
    if (nextPeriod === period) return;
    setPendingFocus(null);
    table.setPage({ ...table.state.page, pageIndex: 0 });
    onPeriodChange(nextPeriod);
  };

  const cancelledLabel = t("myBookings.period.cancelled");

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
            <Button
              type="button"
              size="sm"
              variant={period === "upcoming" ? "secondary" : "outline"}
              aria-label={t("myBookings.period.upcoming")}
              aria-pressed={period === "upcoming"}
              onClick={() => selectPeriod("upcoming")}
            >
              <CalendarClockIcon aria-hidden="true" />
              {t("myBookings.period.upcoming")}
              {upcomingCount.isSuccess && (
                <Badge
                  variant={period === "upcoming" ? "secondary" : "outline"}
                  className="pointer-events-none min-w-5 px-1"
                  aria-label={t("myBookings.count.accessible", { count: upcomingCount.data })}
                >
                  {upcomingCount.data}
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
            <Button
              type="button"
              size="sm"
              variant={period === "cancelled" ? "secondary" : "outline"}
              aria-label={cancelledLabel}
              aria-pressed={period === "cancelled"}
              onClick={() => selectPeriod("cancelled")}
            >
              <CalendarX2Icon aria-hidden="true" />
              {cancelledLabel}
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
          presentations={{ table: "wide", cards: "narrow" }}
          emptyDescription={
            period === "upcoming"
              ? t("myBookings.empty.upcoming")
              : period === "past"
                ? t("myBookings.empty.past")
                : t("myBookings.empty.cancelled")
          }
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
