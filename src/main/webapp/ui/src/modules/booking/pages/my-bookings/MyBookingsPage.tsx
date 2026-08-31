import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useQueryState } from "nuqs";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { type BookingListDocument, BookingListDocumentTableValidation } from "@/modules/booking/domain/booking";
import { useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useAlignedMinute } from "@/modules/booking/hooks/useAlignedMinute";
import type { CollectionRow } from "@/modules/common/collection/collectionConfig";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { useApiV2TableList } from "@/modules/common/table-list/adapters/apiV2/useApiV2TableList";
import { TableList, type TableListRowActions } from "@/modules/common/table-list/TableList";
import type { FilterExpression } from "@/modules/common/table-list/tableListState";
import { Badge } from "@/modules/common/ui/badge";
import { Button, buttonVariants } from "@/modules/common/ui/button";
import { bookingListConfig } from "./bookingList";
import { type MyBookingsPeriod, myBookingsPeriodParser } from "./routes";

const UpcomingCountSchema = v.object({ totalDocs: v.number() });
const projection = {
  fixed: ["id", "target", "canViewConfiguration", "timezone", "start", "end", "purpose"],
} as const;
const emptyDescriptionKeys = {
  upcoming: "myBookings.empty.upcoming",
  past: "myBookings.empty.past",
} as const satisfies Record<MyBookingsPeriod, string>;

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
    where: `requesterId==${requesterId};kind==BOOKING;end=gt=${asOf.toISOString()}`,
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
        {
          kind: "comparison",
          field: "end",
          operator: period === "upcoming" ? "greaterThan" : "lessThanOrEqual",
          value: asOfDate,
        },
      ],
    }),
    [asOfDate, period, requesterId],
  );
  const request = useMemo(
    () => ({
      token,
      depth: 1,
      projection,
      baseFilter,
      validateRows: BookingListDocumentTableValidation.validateRows,
    }),
    [baseFilter, token],
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
  const rowActions = useMemo<
    TableListRowActions<CollectionRow<BookingListDocument, "id" | "target" | "canViewConfiguration">>
  >(
    () => ({
      id: "actions",
      label: t("myBookings.actions.label"),
      width: 120,
      renderCell: ({ row }) =>
        row.canViewConfiguration ? (
          <Link
            className={buttonVariants({ size: "sm", variant: "outline" })}
            to="/booking/bookable-items/$globalId"
            params={{ globalId: row.target.globalId }}
          >
            {t("myBookings.actions.viewDetails")}
          </Link>
        ) : (
          <span className="text-sm text-muted-foreground">{t("myBookings.roleLoss.readOnly")}</span>
        ),
      renderInteraction: () => null,
    }),
    [t],
  );

  const selectPeriod = (nextPeriod: "upcoming" | "past") => {
    if (nextPeriod === period) return;
    table.setPage({ ...table.state.page, pageIndex: 0 });
    onPeriodChange(nextPeriod);
  };

  return (
    <main className="space-y-6 p-4 sm:p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold">{title}</h1>
        <p className="text-sm text-muted-foreground">{t("myBookings.description")}</p>
      </header>
      <div className="space-y-2">
        <fieldset>
          <legend className="sr-only">{t("myBookings.period.legend")}</legend>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              className={period === "upcoming" ? "border-foreground bg-muted text-foreground" : undefined}
              aria-pressed={period === "upcoming"}
              onClick={() => selectPeriod("upcoming")}
            >
              {t("myBookings.period.upcoming")}
              {upcomingCount.isSuccess && (
                <Badge
                  variant={period === "upcoming" ? "secondary" : "outline"}
                  aria-label={t("myBookings.count.accessible", { count: upcomingCount.data })}
                >
                  {upcomingCount.data}
                </Badge>
              )}
              {upcomingCount.isPending && <span role="status">{t("myBookings.count.loading")}</span>}
            </Button>
            <Button
              type="button"
              variant="outline"
              className={period === "past" ? "border-foreground bg-muted text-foreground" : undefined}
              aria-pressed={period === "past"}
              onClick={() => selectPeriod("past")}
            >
              {t("myBookings.period.past")}
            </Button>
          </div>
        </fieldset>
        {upcomingCount.isError && (
          <p className="text-sm text-destructive" role="alert">
            {t("myBookings.count.error")}{" "}
            <Button type="button" size="xs" variant="outline" onClick={() => void upcomingCount.refetch()}>
              {commonT("actions.retry")}
            </Button>
          </p>
        )}
      </div>
      <TableList
        {...table.tableProps}
        variant="transparent"
        emptyDescription={t(emptyDescriptionKeys[period])}
        rowActions={rowActions}
      />
    </main>
  );
}

export function MyBookingsRoutePage({ requesterId, title }: Pick<UserBookingsPageProps, "requesterId" | "title">) {
  const [period, setPeriod] = useQueryState("period", myBookingsPeriodParser);
  return (
    <UserBookingsPage
      requesterId={requesterId}
      title={title}
      period={period}
      onPeriodChange={(nextPeriod) => void setPeriod(nextPeriod)}
    />
  );
}

export default function MyBookingsPage() {
  const { t } = useTranslation("booking");
  const { data: currentUser } = useCurrentUserQuery();
  return <MyBookingsRoutePage requesterId={currentUser.id} title={t("myBookings.title")} />;
}
