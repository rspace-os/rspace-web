import { Temporal } from "@js-temporal/polyfill";

/** The instant an ISO 8601 date-time with an offset or `Z` names, or null for any other text. Keeps sub-millisecond digits. */
export function parseInstant(value: string): Temporal.Instant | null {
  try {
    return Temporal.Instant.from(value);
  } catch {
    return null;
  }
}

/** An instant cut to whole milliseconds, the precision of the REST API v2 wire format. */
export function truncateToMilliseconds(instant: Temporal.Instant): Temporal.Instant {
  return instant.round({ smallestUnit: "millisecond", roundingMode: "trunc" });
}

/** The REST API v2 wire format for an instant: UTC with exactly three fractional digits, as `Date#toISOString` printed. */
export function instantToWire(instant: Temporal.Instant): string {
  return instant.toString({ fractionalSecondDigits: 3 });
}

/** Epoch milliseconds of an ISO 8601 instant, or `NaN` for other text, as `Date.parse` returned. */
export function epochMillisecondsOf(value: string): number {
  return parseInstant(value)?.epochMilliseconds ?? Number.NaN;
}
