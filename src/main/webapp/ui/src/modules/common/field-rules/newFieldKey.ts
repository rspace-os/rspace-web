import { v4 } from "uuid";

/**
 * A new field key: a random, lowercase RFC 4122 version 4 UUID. Unlike
 * `crypto.randomUUID`, which only exists in secure contexts (HTTPS or
 * localhost), this also works on plain-HTTP deployments, where the `uuid`
 * package falls back to `crypto.getRandomValues`.
 */
export function newFieldKey(): string {
  return v4();
}
