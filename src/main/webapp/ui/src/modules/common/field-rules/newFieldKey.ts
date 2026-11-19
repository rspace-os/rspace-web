import { v7 } from "uuid";

/**
 * A new field key: a lowercase RFC 9562 version 7 UUID, a millisecond
 * timestamp followed by random bits, as the server's `FieldKeys` generates.
 * Unlike `crypto.randomUUID`, which only exists in secure contexts (HTTPS or
 * localhost), this also works on plain-HTTP deployments, because the `uuid`
 * package draws its random bits from `crypto.getRandomValues`.
 */
export function newFieldKey(): string {
  return v7();
}
