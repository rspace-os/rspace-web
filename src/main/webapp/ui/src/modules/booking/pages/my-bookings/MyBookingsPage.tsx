import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { Skeleton } from "@/modules/common/ui/skeleton";
import { MyBookingsRoutePage } from "./MyBookingsRoutePage";

export { MyBookingsRoutePage } from "./MyBookingsRoutePage";
export { UserBookingsPage, type UserBookingsPageProps } from "./UserBookingsPage";

export default function MyBookingsPage() {
  return (
    <Suspense fallback={<MyBookingsSkeleton />}>
      <MyBookingsContent />
    </Suspense>
  );
}

function MyBookingsSkeleton() {
  const { t } = useTranslation("common");
  return (
    <main className="space-y-6 p-4 sm:p-8" aria-busy="true">
      <p role="status" className="sr-only">
        {t("loading")}
      </p>
      <div aria-hidden="true" className="space-y-6">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-20 w-full" />
        {[0, 1, 2, 3].map((row) => (
          <Skeleton key={row} className="h-20 w-full" />
        ))}
      </div>
    </main>
  );
}

function MyBookingsContent() {
  const { t } = useTranslation("booking");
  const { data: currentUser } = useCurrentUserQuery();
  return <MyBookingsRoutePage requesterId={currentUser.id} title={t("myBookings.title")} />;
}
