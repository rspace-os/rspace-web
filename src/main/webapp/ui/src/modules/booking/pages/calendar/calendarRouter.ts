import { createRootRoute, createRoute, createRouter, Outlet, type RouterHistory } from "@tanstack/react-router";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";
import BookableItemPage from "@/modules/booking/pages/bookable-items/BookableItemPage";
import { bookableItemSearch } from "@/modules/booking/pages/bookable-items/search";
import CalendarPage from "@/modules/booking/pages/calendar/CalendarPage";
import BookingPage from "../BookingPage";

export function createCalendarRouter(history: RouterHistory) {
  const root = createRootRoute({ component: Outlet });
  const booking = createRoute({ getParentRoute: () => root, path: "/booking", component: BookingPage });
  return createRouter({
    routeTree: root.addChildren([
      booking.addChildren([
        createRoute({
          getParentRoute: () => booking,
          path: "/calendar/",
          validateSearch: (search: Record<string, unknown>): { date?: string; target?: string } => ({
            date: typeof search.date === "string" && isPlainDate(search.date) ? search.date : undefined,
            ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
          }),
          component: CalendarPage,
        }),
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
