import { createRootRoute, createRoute, createRouter, Outlet, type RouterHistory } from "@tanstack/react-router";
import BookingPage from "../BookingPage";
import { createBookableItemRoute } from "../bookable-items/routes";
import { createCalendarRoute } from "./routes";

export function createCalendarRouter(history: RouterHistory) {
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  return createRouter({
    routeTree: root.addChildren([
      booking.addChildren([createCalendarRoute(booking), createBookableItemRoute(booking)]),
    ]),
    history,
  });
}
