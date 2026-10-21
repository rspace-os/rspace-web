import { createLoader, type inferParserType, parseAsStringLiteral } from "nuqs";

export const myBookingsPeriods = ["upcoming", "past", "cancelled"] as const;

export const myBookingsPeriodParser = parseAsStringLiteral(myBookingsPeriods)
  .withDefault("upcoming")
  .withOptions({ history: "replace", clearOnDefault: false });
export type MyBookingsPeriod = inferParserType<typeof myBookingsPeriodParser>;

const loadMyBookingsSearch = createLoader({ period: myBookingsPeriodParser });

export function myBookingsSearch(search: Record<string, unknown>) {
  return loadMyBookingsSearch({ period: typeof search.period === "string" ? search.period : undefined });
}
