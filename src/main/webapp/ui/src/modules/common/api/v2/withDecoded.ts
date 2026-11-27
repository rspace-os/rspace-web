import { Temporal } from "@js-temporal/polyfill";
import { isPlainObject } from "es-toolkit";
import type * as v from "valibot";

/**
 * Copies the instants a generated schema decoded into the raw JSON it parsed, in place, and returns the raw value.
 * No key is added or removed, so extension members the parse strips and defaults it adds stay as the server sent
 * them. Only a member whose parsed value is a `Temporal.Instant` changes.
 */
export function withDecoded(raw: unknown, parsed: unknown): unknown {
  if (parsed instanceof Temporal.Instant) return parsed;
  if (raw === parsed) return raw;
  if (Array.isArray(raw) && Array.isArray(parsed)) {
    for (let index = 0; index < Math.min(raw.length, parsed.length); index++) {
      const next = withDecoded(raw[index], parsed[index]);
      if (next !== raw[index]) raw[index] = next;
    }
  } else if (isPlainObject(raw) && isPlainObject(parsed)) {
    for (const key of Object.keys(parsed)) {
      if (!Object.hasOwn(raw, key)) continue;
      const next = withDecoded(raw[key], parsed[key]);
      // defineProperty, because an own `__proto__` key from JSON.parse must stay a plain member.
      if (next !== raw[key])
        Object.defineProperty(raw, key, { value: next, writable: true, enumerable: true, configurable: true });
    }
  }
  return raw;
}

/**
 * `schema`, accepted only while it decodes nothing. Transport output is already decoded, and a generated schema that
 * decodes a date-time rejects the instant it made, so code that parses transport output again passes its schema
 * through here: a date-time added to it later fails `tsc` instead of every response.
 */
export function withoutDecoding<TSchema extends v.GenericSchema>(
  schema: TSchema &
    ([v.InferInput<TSchema>] extends [v.InferOutput<TSchema>]
      ? [v.InferOutput<TSchema>] extends [v.InferInput<TSchema>]
        ? unknown
        : never
      : never),
): TSchema {
  return schema;
}
