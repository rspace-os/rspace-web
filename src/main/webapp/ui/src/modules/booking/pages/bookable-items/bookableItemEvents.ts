import type * as v from "valibot";
import { bookingApiV2Headers } from "@/modules/booking/domain/apiV2";
import { BOOKING_READ_FIELDS, type Booking, BookingSchema } from "@/modules/booking/domain/booking";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2ListEnvelope } from "@/modules/common/queries/v2Pagination";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql/rsqlCodec";

export type BookingEventPeriod = "upcoming" | "past";

const BookableItemEventsSchema = v2ListEnvelope(BookingSchema);

export type BookableItemEvents = v.InferOutput<typeof BookableItemEventsSchema>;

export async function fetchBookableItemEvents(input: {
  globalId: string;
  period: BookingEventPeriod;
  cutoff: string;
  page: number;
  token: string;
  signal?: AbortSignal;
}): Promise<BookableItemEvents> {
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
  const parameters = new URLSearchParams({
    where,
    sort: input.period === "upcoming" ? "start,id" : "-end,-id",
    page: String(input.page + 1),
    limit: "10",
    depth: "1",
    "fields[bookings]": BOOKING_READ_FIELDS,
  });
  const response = await fetch(`/api/v2/bookings?${parameters}`, {
    headers: bookingApiV2Headers(input.token),
    signal: input.signal,
  });
  if (!response.ok) throw new Error(`Bookable item events request failed with status ${response.status}`);
  const result = parseOrThrow(BookableItemEventsSchema, (await response.json()) as unknown);
  if (result.docs.some((booking) => booking.target.globalId !== input.globalId)) {
    throw new Error(`Booking response contained an event for another target`);
  }
  return result;
}
