import { Outlet } from "@tanstack/react-router";
import { Suspense } from "react";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { CompactBookingCreationDialog } from "@/modules/booking/creation/CompactBookingCreationDialog";
import { BookingBreadcrumbs } from "./BookingBreadcrumbs";

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
