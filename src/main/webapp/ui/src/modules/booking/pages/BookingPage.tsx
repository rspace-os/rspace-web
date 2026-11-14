import { Outlet } from "@tanstack/react-router";
import { Suspense } from "react";
import { BookingCreationStoreProvider } from "@/modules/booking/creation/bookingCreationStore";
import { CompactBookingCreationDialog } from "@/modules/booking/creation/CompactBookingCreationDialog";
import {
  BookingTimeFormatProvider,
  useBookingDisplayPreferences,
} from "@/modules/booking/domain/bookingDisplayPreferences";
import { BookingBreadcrumbs } from "./BookingBreadcrumbs";

export default function BookingPage() {
  const preferences = useBookingDisplayPreferences();
  return (
    <BookingTimeFormatProvider timeFormat={preferences.timeFormat}>
      <BookingCreationStoreProvider>
        <div className="mx-auto w-full max-w-7xl">
          <BookingBreadcrumbs />
          <Outlet />
        </div>
        <Suspense fallback={null}>
          <CompactBookingCreationDialog />
        </Suspense>
      </BookingCreationStoreProvider>
    </BookingTimeFormatProvider>
  );
}
