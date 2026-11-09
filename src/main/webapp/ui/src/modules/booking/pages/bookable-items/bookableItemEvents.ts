import { BOOKING_READ_FIELDS, type Booking, mapBooking } from "@/modules/booking/domain/booking";
import { requireBookingValue as required } from "@/modules/booking/domain/bookingMapping";
import { getListBookingsQueryOptions, listBookings } from "@/modules/common/api/v2/generated/bookings/bookings";
import type { ListBookings200 } from "@/modules/common/api/v2/generated/models/listBookings200";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
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
  const where = serializeRsqlExpression<Booking>({
    kind: "and",
    children: [
      { kind: "comparison", field: "target", operator: "equals", value: input.globalId },
      { kind: "comparison", field: "state", operator: "equals", value: "CONFIRMED" },
      {
        kind: "comparison",
        field: "end",
        operator: input.period === "upcoming" ? "greaterThan" : "lessThanOrEqual",
        value: input.cutoff,
      },
    ],
  });
  return {
    where,
    totals: input.totals || undefined,
    sort: input.period === "upcoming" ? "start,id" : "-end,-id",
    page: input.page + 1,
    limit: input.limit,
    depth: 1,
    fields: { bookings: BOOKING_READ_FIELDS },
  };
}
function mapEvents(result: ListBookings200, input: BookableItemEventsInput) {
  const mapped = {
    ...result,
    docs: result.docs.map(mapBooking),
    hasNextPage: required(result.hasNextPage, "hasNextPage"),
  };
  if (mapped.docs.some((booking) => booking.target !== null && booking.target.globalId !== input.globalId)) {
    throw new Error(`Booking response contained an event for another target`);
  }
  return mapped;
}

export type BookableItemEvents = ReturnType<typeof mapEvents>;
export async function fetchBookableItemEvents(input: BookableItemEventsInput): Promise<BookableItemEvents> {
  return mapEvents(await listBookings(parameters(input), { signal: input.signal }), input);
}
export function bookableItemEventsQuery(input: BookableItemEventsInput) {
  return getListBookingsQueryOptions<BookableItemEvents>(parameters(input), {
    query: { select: (result) => mapEvents(result, input), retry: false },
  });
}
