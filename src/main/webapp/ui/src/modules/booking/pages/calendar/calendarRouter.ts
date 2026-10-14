import { createRootRoute, createRoute, createRouter, Outlet, type RouterHistory } from "@tanstack/react-router";
import { createElement } from "react";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import BookingPage from "../BookingPage";
import { createBookableItemRoute } from "../bookable-items/routes";
import { createAddBookingRoute, createBookingEventRouteTree } from "../bookings/routes";
import { createCalendarRoute } from "./routes";

export function createCalendarRouter(history: RouterHistory) {
  const root = createRootRoute({
    component: () => createElement(NuqsAdapter, null, createElement(Outlet)),
  });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  return createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createCalendarRoute(booking),
        createAddBookingRoute(booking),
        createBookingEventRouteTree(booking),
        createBookableItemRoute(booking),
      ]),
    ]),
    history,
  });
}
