/** Require fields needed by a domain view after generated wire validation. */
export function requireBookingValue<T>(value: T | null | undefined, field: string): NonNullable<T> {
  if (value === undefined || value === null) throw new Error(`Booking field ${field} is unavailable`);
  return value;
}
