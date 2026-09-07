import { type AnyRoute, createRoute } from "@tanstack/react-router";
import { createLoader, type inferParserType, parseAsStringLiteral } from "nuqs";
import MyBookingsPage from "./MyBookingsPage";

export const myBookingsPeriodParser = parseAsStringLiteral(["upcoming", "past"] as const)
  .withDefault("upcoming")
  .withOptions({ history: "replace", clearOnDefault: false });
export type MyBookingsPeriod = inferParserType<typeof myBookingsPeriodParser>;

const loadMyBookingsSearch = createLoader({ period: myBookingsPeriodParser });

export function myBookingsSearch(search: Record<string, unknown>) {
  return loadMyBookingsSearch({ period: typeof search.period === "string" ? search.period : undefined });
}

export function createMyBookingsRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/my-bookings",
    validateSearch: myBookingsSearch,
    component: MyBookingsPage,
  });
}
