import { createFileRoute } from "@tanstack/react-router";
import BookingSettingsRoutePage from "@/modules/booking/pages/bookable-items/BookingSettingsRoutePage";

export const Route = createFileRoute("/booking/config/settings/")({
  // Permission guards live in the component because the router has no current-user context.
  component: BookingSettingsRoutePage,
});
