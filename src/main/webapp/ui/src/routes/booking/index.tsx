import { createFileRoute } from "@tanstack/react-router";
import BookingDashboardPage from "@/modules/booking/pages/dashboard/BookingDashboardPage";

export const Route = createFileRoute("/booking/")({
  component: BookingDashboardPage,
});
