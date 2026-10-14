import { createLoader, type inferParserType, parseAsStringLiteral } from "nuqs";

export const myBookingsPeriodParser = parseAsStringLiteral(["upcoming", "past", "cancelled"] as const)
  .withDefault("upcoming")
  .withOptions({ history: "replace", clearOnDefault: false });
export type MyBookingsPeriod = inferParserType<typeof myBookingsPeriodParser>;

const loadMyBookingsSearch = createLoader({ period: myBookingsPeriodParser });

export function myBookingsSearch(search: Record<string, unknown>) {
  return loadMyBookingsSearch({ period: typeof search.period === "string" ? search.period : undefined });
}
