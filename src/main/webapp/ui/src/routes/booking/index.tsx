import { createFileRoute } from "@tanstack/react-router";
import { BookingDashboard } from "@/modules/booking/pages/BookingPage";

export const Route = createFileRoute("/booking/")({
  component: BookingDashboard,
});
