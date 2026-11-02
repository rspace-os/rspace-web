import { createRootRoute, createRoute, createRouter, Outlet, type RouterHistory } from "@tanstack/react-router";
import { createElement } from "react";
import { MemoryHistoryNuqsAdapter as NuqsAdapter } from "@/__tests__/MemoryHistoryNuqsAdapter";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import { bookingFormSearch } from "@/modules/booking/pages/bookingFormSearch";
import AddBookingPage from "@/modules/booking/pages/bookings/AddBookingPage";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";
import BookingPage from "../BookingPage";
import { createBookingEventTestRouteTree } from "../bookings/__tests__/bookingEventTestHarness";

export function createCalendarRouter(history: RouterHistory) {
  const root = createRootRoute({
    component: () => createElement(NuqsAdapter, null, createElement(Outlet)),
  });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  return createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createRoute({
          getParentRoute: () => booking,
          path: "/calendar/",
          validateSearch: bookingFormSearch,
          component: CalendarPage,
        }),
        createRoute({
          getParentRoute: () => booking,
          path: "/calendar/bookings/add/",
          validateSearch: bookingFormSearch,
          component: AddBookingPage,
        }),
        createBookingEventTestRouteTree(booking),
        createRoute({
          getParentRoute: () => booking,
          path: "/bookable-items/$globalId/{-$tab}/",
          validateSearch: bookableItemSearch,
          component: BookableItemPage,
        }),
      ]),
    ]),
    history,
  });
}
