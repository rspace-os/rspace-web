import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { EyeIcon } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { TableList } from "@/modules/common/table-list/TableList";
import type { TableListFeatures, TableListUiColumn } from "@/modules/common/table-list/tableListState";
import { buttonVariants } from "@/modules/common/ui/button";
import { Card, CardContent } from "@/modules/common/ui/card";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/modules/common/ui/tooltip";
import { bookingListConfig } from "../my-bookings/bookingList";
import { DashboardEmpty, DashboardError } from "./DashboardFeedback";
import { fetchUpcomingDashboardBookings } from "./dashboardBookings";
import { dashboardBooking } from "./dashboardHelpers";

const dashboardTableFeatures: TableListFeatures<BookingListDocument> = {
  filtering: false,
  sorting: false,
  pagination: false,
  columns: false,
};

function UpcomingBookingTable({ rows, timeZone }: { rows: readonly BookingListDocument[]; timeZone: string }) {
  const { t } = useTranslation("booking");
  const config = useMemo(() => resolveCollectionConfig(bookingListConfig(timeZone)), [timeZone]);
  const uiColumns = useMemo<readonly TableListUiColumn<BookingListDocument>[]>(
    () => [
      {
        id: "details",
        label: t("myBookings.actions.label"),
        card: { placement: "footer" },
        renderCell: (row) =>
          row.privacy === "full" ? (
            <Tooltip>
              <TooltipTrigger
                render={
                  <Link
                    to="/booking/calendar/bookings/$id"
                    params={{ id: String(row.id) }}
                    aria-label={t("myBookings.actions.viewDetails")}
                    className={buttonVariants({ variant: "outline", size: "icon-lg" })}
                    data-slot="button"
                  />
                }
              >
                <EyeIcon aria-hidden="true" />
              </TooltipTrigger>
              <TooltipContent role="tooltip">{t("myBookings.actions.viewDetails")}</TooltipContent>
            </Tooltip>
          ) : null,
      },
    ],
    [t],
  );

  return (
    <TableList
      config={config}
      rows={rows}
      getRowId={(row) => String(row.id)}
      features={dashboardTableFeatures}
      clientSide
      hideHeader
      queryString={false}
      reserveEmptyRows={false}
      variant="transparent"
      uiColumns={uiColumns}
    />
  );
}

function UpcomingBookingSkeleton() {
  return (
    <div className="divide-y" aria-hidden="true">
      {Array.from({ length: 5 }, (_, index) => (
        <div key={index} className="flex items-center gap-4 px-4 py-3">
          <div className="min-w-0 flex-1 space-y-2">
            <Skeleton className="h-4 w-2/5" />
            <Skeleton className="h-3 w-3/5" />
          </div>
          <Skeleton className="h-8 w-20" />
        </div>
      ))}
    </div>
  );
}

export function UpcomingBookingsWidget({
  requesterId,
  token,
  timeZone,
  asOf,
}: {
  requesterId: number;
  token: string;
  timeZone: string;
  asOf: number;
}) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const asOfIso = useMemo(() => new Date(asOf).toISOString(), [asOf]);
  const query = useQuery({
    queryKey: ["api-v2", "bookings", "dashboard", "upcoming", requesterId, timeZone, asOfIso],
    queryFn: ({ signal }) => fetchUpcomingDashboardBookings({ requesterId, asOf: asOfIso, token, signal }),
    enabled: token.length > 0,
    retry: false,
    refetchOnWindowFocus: true,
  });
  const rows = useMemo(() => (query.isSuccess ? query.data.map(dashboardBooking) : []), [query.data, query.isSuccess]);
  const retryLabel = commonT("actions.retry");

  return (
    <section aria-labelledby="booking-dashboard-upcoming" className="min-w-0 space-y-3">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id="booking-dashboard-upcoming" className="text-xl font-semibold tracking-tight">
          {t("dashboard.upcoming.title")}
        </h2>
        <Link
          to="/booking/my-bookings"
          search={{ period: "upcoming" }}
          className="shrink-0 text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {t("dashboard.upcoming.viewAll")}
        </Link>
      </div>
      <Card className="overflow-hidden">
        <CardContent className="p-0">
          {query.isPending ? (
            <div aria-busy="true">
              <p role="status" className="sr-only">
                {t("dashboard.upcoming.loading")}
              </p>
              <UpcomingBookingSkeleton />
            </div>
          ) : query.isError ? (
            <DashboardError
              title={t("dashboard.upcoming.error.title")}
              description={t("dashboard.upcoming.error.description")}
              retryLabel={retryLabel}
              onRetry={() => void query.refetch()}
            />
          ) : rows.length === 0 ? (
            <DashboardEmpty>{t("dashboard.upcoming.empty")}</DashboardEmpty>
          ) : (
            <div aria-busy={query.isFetching}>
              <UpcomingBookingTable rows={rows} timeZone={timeZone} />
            </div>
          )}
        </CardContent>
      </Card>
    </section>
  );
}
