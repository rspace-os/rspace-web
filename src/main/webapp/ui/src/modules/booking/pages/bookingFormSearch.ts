import { isPlainDate } from "@/modules/booking/domain/bookingTime";

export function bookingFormSearch(search: Record<string, unknown>): { date?: string; target?: string } {
  return {
    date: typeof search.date === "string" && isPlainDate(search.date) ? search.date : undefined,
    ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
  };
}
