import { Temporal } from "@js-temporal/polyfill";
import { isPlainDate } from "@/modules/booking/domain/bookingTime";

export { addCalendarDays } from "@/modules/booking/domain/bookingTime";

export function validCalendarDate(value: unknown): value is string {
  return typeof value === "string" && isPlainDate(value);
}

export function localToday(): string {
  return Temporal.Now.plainDateISO().toString();
}
