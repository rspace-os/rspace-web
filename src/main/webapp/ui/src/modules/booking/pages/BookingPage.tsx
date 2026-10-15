import { Outlet } from "@tanstack/react-router";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { CompactBookingCreationDialog } from "@/modules/booking/creation/CompactBookingCreationDialog";
import { Heading } from "@/modules/common/ui/typography";
import { BookingBreadcrumbs } from "./BookingBreadcrumbs";

export function BookingDashboard() {
  const { t } = useTranslation("booking");
  return (
    <main className="space-y-6 p-4 sm:p-8">
      <Heading level={3} as="h1">
        {t("sidebar.dashboard")}
      </Heading>
    </main>
  );
}

export default function BookingPage() {
  return (
    <BookingCreationStoreProvider>
      <div className="mx-auto w-full max-w-7xl">
        <BookingBreadcrumbs />
        <Outlet />
      </div>
      <Suspense fallback={null}>
        <CompactBookingCreationDialog />
      </Suspense>
    </BookingCreationStoreProvider>
  );
}
