import { BOOKING_READ_FIELDS, mapSelectedBookings } from "@/modules/booking/domain/booking";
import { apiV2Fields } from "@/modules/common/api/v2/apiV2Selected";
import type { ApiV2WhereObject } from "@/modules/common/api/v2/apiV2Where";
import { getListBookingsQueryKey, listBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import type { ListBookings200 } from "@/modules/common/api/v2/generated/models/listBookings200";
export type BookingEventPeriod = "upcoming" | "past";

type BookableItemEventsInput = {
  globalId: string;
  period: BookingEventPeriod;
  cutoff: string;
  page: number;
  limit: number;

  signal?: AbortSignal;
  totals?: boolean;
};

function parameters(input: BookableItemEventsInput) {
  const where: ApiV2WhereObject<"Bookings"> = {
    target: input.globalId,
    state: "CONFIRMED",
    end: input.period === "upcoming" ? { greaterThan: input.cutoff } : { lessThanOrEqual: input.cutoff },
  };
  return {
    where,
    totals: input.totals || undefined,
    sort: input.period === "upcoming" ? "start,id" : "-end,-id",
    page: input.page + 1,
    limit: input.limit,
    depth: 1,
    fields: { bookings: apiV2Fields(BOOKING_READ_FIELDS) },
  };
}
function mapEvents(result: ListBookings200, input: BookableItemEventsInput) {
  const mapped = { ...result, docs: mapSelectedBookings(result.docs) };
  if (mapped.docs.some((booking) => booking.target !== null && booking.target.globalId !== input.globalId)) {
    throw new Error(`Booking response contained an event for another target`);
  }
  return mapped;
}

export type BookableItemEvents = ReturnType<typeof mapEvents>;
export async function fetchBookableItemEvents(input: BookableItemEventsInput): Promise<BookableItemEvents> {
  return mapEvents(await listBookings(parameters(input), { signal: input.signal }), input);
}
/** The list-bookings key of an events read, so cache entries share the generated prefix. */
export function bookableItemEventsQueryKey(input: BookableItemEventsInput) {
  return getListBookingsQueryKey(parameters(input));
}
