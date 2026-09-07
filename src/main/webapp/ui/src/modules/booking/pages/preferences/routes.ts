import { type AnyRoute, createRoute } from "@tanstack/react-router";
import BookingPreferencesPage from "./BookingPreferencesPage";

export function createBookingPreferencesRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/preferences",
    component: BookingPreferencesPage,
  });
}
