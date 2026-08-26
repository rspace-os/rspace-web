import { type AnyRoute, createRoute } from "@tanstack/react-router";
import AllBookableItemsPage from "./AllBookableItemsPage";
import { localToday, validCalendarDate } from "./calendarDate";

const availabilityModes = ["available-now", "free-later-today"] as const;

export function allBookableItemsSearch(search: Record<string, unknown>) {
  const availability = availabilityModes.find((mode) => mode === search.availability);
  return {
    date: validCalendarDate(search.date) ? search.date : localToday(),
    ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
    ...(availability ? { availability } : {}),
  };
}

export function createAllBookableItemsRoute<TParentRoute extends AnyRoute>(
  bookingRoute: TParentRoute,
  component = AllBookableItemsPage,
) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/all-items",
    validateSearch: allBookableItemsSearch,
    component,
  });
}
