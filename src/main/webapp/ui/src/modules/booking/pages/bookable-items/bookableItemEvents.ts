import type * as v from "valibot";
import { BOOKING_READ_FIELDS, type Booking, BookingSchema } from "@/modules/booking/domain/booking";
import { apiV2Headers } from "@/modules/common/api/apiV2Headers";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { v2PageEnvelope } from "@/modules/common/queries/v2Pagination";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";

export type BookingEventPeriod = "upcoming" | "past";

const BookableItemEventsSchema = v2PageEnvelope(BookingSchema);

export type BookableItemEvents = v.InferOutput<typeof BookableItemEventsSchema>;

export async function fetchBookableItemEvents(input: {
  globalId: string;
  period: BookingEventPeriod;
  cutoff: string;
  page: number;
  limit: number;
  token: string;
  signal?: AbortSignal;
  totals?: boolean;
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
    limit: String(input.limit),
    depth: "1",
    "fields[bookings]": BOOKING_READ_FIELDS,
  });
  if (input.totals) parameters.set("totals", "true");
  const response = await fetchWithUiToken(`/api/v2/bookings?${parameters}`, {
    headers: apiV2Headers(input.token),
    signal: input.signal,
  });
  if (!response.ok) throw new Error(`Bookable item events request failed with status ${response.status}`);
  const result = parseOrThrow(BookableItemEventsSchema, (await response.json()) as unknown);
  if (result.docs.some((booking) => booking.target !== null && booking.target.globalId !== input.globalId)) {
    throw new Error(`Booking response contained an event for another target`);
  }
  return result;
}
