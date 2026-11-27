import type { Temporal } from "@js-temporal/polyfill";

/**
 * A generated REST API v2 type as JSON carries it, before the transport decodes declared date-times to
 * `Temporal.Instant`. For wire fixtures whose type has no generated Valibot schema to take `v.InferInput` of.
 */
export type ApiV2Wire<T> = T extends Temporal.Instant
  ? string
  : T extends object
    ? { [K in keyof T]: ApiV2Wire<T[K]> }
    : T;
