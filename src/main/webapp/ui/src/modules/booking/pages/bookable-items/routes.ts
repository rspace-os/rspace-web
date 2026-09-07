import { type AnyRoute, createRoute } from "@tanstack/react-router";
import BookingSettingsPage from "./BookingSettingsPage";

export function createBookingSettingsRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/config/settings",
    component: BookingSettingsPage,
  });
}
