import type { ApiV2WhereObject } from "@/modules/common/api/v2/apiV2Where";

export function upcomingBookingWhere(requesterId: number, asOf: string): ApiV2WhereObject<"Bookings"> {
  return { "requester.value": requesterId, kind: "BOOKING", state: "CONFIRMED", end: { greaterThan: asOf } };
}
