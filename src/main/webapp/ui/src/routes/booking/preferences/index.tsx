import { createFileRoute } from "@tanstack/react-router";
import BookingPreferencesPage from "@/modules/booking/pages/preferences/BookingPreferencesPage";

export const Route = createFileRoute("/booking/preferences/")({
  component: BookingPreferencesPage,
});
