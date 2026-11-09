import { createFileRoute } from "@tanstack/react-router";
import BookingSettingsPage from "@/modules/booking/pages/bookable-items/BookingSettingsPage";

export const Route = createFileRoute("/booking/config/settings/")({
  component: BookingSettingsPage,
});
