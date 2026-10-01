import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { todayInTimeZone, useBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import { useAlignedMinute } from "@/modules/booking/hooks/useAlignedMinute";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { Heading } from "@/modules/common/ui/typography";
import { AtAGlanceWidget } from "./AtAGlanceWidget";
import { DashboardQuickActions } from "./DashboardQuickActions";
import { UpcomingBookingsWidget } from "./UpcomingBookingsWidget";

export { AtAGlanceWidget } from "./AtAGlanceWidget";
export { DashboardQuickActions } from "./DashboardQuickActions";
export { UpcomingBookingsWidget } from "./UpcomingBookingsWidget";

const dashboardPagePadding = "space-y-8 p-4 sm:p-8";

function DashboardSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className={dashboardPagePadding} aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <Skeleton className="h-10 w-48" />
      <div className="space-y-8" aria-hidden="true">
        <Skeleton className="h-24 w-full" />
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <Skeleton className="h-80 w-full" />
          <Skeleton className="h-80 w-full" />
        </div>
      </div>
    </main>
  );
}

function BookingDashboardContent() {
  const { t } = useTranslation("booking");
  const { data: currentUser } = useCurrentUserQuery();
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const preferences = useBookingDisplayPreferences();
  const asOf = useAlignedMinute();
  const today = todayInTimeZone(preferences.timeZone, new Date(asOf));

  return (
    <main className={dashboardPagePadding}>
      <Heading level={3} as="h1">
        {t("sidebar.dashboard")}
      </Heading>
      <div className="space-y-8">
        <DashboardQuickActions today={today} />
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <UpcomingBookingsWidget
            requesterId={currentUser.id}
            token={token}
            timeZone={preferences.timeZone}
            asOf={asOf}
          />
          <AtAGlanceWidget requesterId={currentUser.id} token={token} timeZone={preferences.timeZone} today={today} />
        </div>
      </div>
    </main>
  );
}

export default function BookingDashboardPage() {
  return (
    <Suspense fallback={<DashboardSkeleton />}>
      <BookingDashboardContent />
    </Suspense>
  );
}
