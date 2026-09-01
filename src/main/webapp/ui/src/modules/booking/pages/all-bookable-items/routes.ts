import { type AnyRoute, createRoute } from "@tanstack/react-router";
import AllBookableItemsPage from "./AllBookableItemsPage";
import { validCalendarDate } from "./calendarDate";

const availabilityModes = ["available-now", "free-later-today"] as const;

function stringList(value: unknown, pattern: RegExp): string[] {
  const values = Array.isArray(value) ? value : typeof value === "string" ? [value] : [];
  return values.filter((entry): entry is string => typeof entry === "string" && pattern.test(entry));
}

export function allBookableItemsSearch(search: Record<string, unknown>) {
  const availability = availabilityModes.find((mode) => mode === search.availability);
  const locations = stringList(search.locations, /^(?:IC|BE)\d+$/);
  const types = stringList(search.types, /^[A-Z][A-Z0-9_]*$/);
  const page = typeof search.page === "number" ? search.page : Number(search.page);
  return {
    ...(validCalendarDate(search.date) ? { date: search.date } : {}),
    ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
    ...(availability ? { availability } : {}),
    ...(typeof search.q === "string" && search.q.trim() ? { q: search.q.slice(0, 255) } : {}),
    ...(locations.length > 0 ? { locations } : {}),
    ...(types.length > 0 ? { types } : {}),
    ...(Number.isSafeInteger(page) && page > 1 ? { page } : {}),
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
