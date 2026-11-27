import { Temporal } from "@js-temporal/polyfill";
import { isPlainObject } from "es-toolkit";

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

/**
 * `value` with every `Temporal.Instant` in its plain objects and arrays turned back into ISO 8601 text, losslessly.
 * Containers without an instant keep their identity, and every other value is left as it is.
 */
export function encodeInstants(value: unknown): unknown {
  if (value instanceof Temporal.Instant) return value.toString();
  if (Array.isArray(value)) {
    const items = value.map(encodeInstants);
    return items.some((item, index) => item !== value[index]) ? items : value;
  }
  if (!isPlainObject(value)) return value;
  const entries = Object.entries(value).map(([key, member]) => [key, encodeInstants(member)] as const);
  return entries.some(([key, member]) => member !== value[key]) ? Object.fromEntries(entries) : value;
}
