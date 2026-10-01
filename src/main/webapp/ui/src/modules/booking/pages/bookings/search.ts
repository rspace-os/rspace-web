import { isPlainDate } from "@/modules/booking/domain/bookingTime";

export function bookingFormSearch(search: Record<string, unknown>) {
  const dateSearch: { date?: string } = {
    date: typeof search.date === "string" && isPlainDate(search.date) ? search.date : undefined,
  };
  return {
    ...dateSearch,
    ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
  };
}
