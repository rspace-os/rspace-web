import { randomUUID } from "node:crypto";

export const DYNAMIC_USER_PASSWORD = "Passw0rd!23";

export function uniqueName(prefix: string): string {
  return `${prefix}-${randomUUID().slice(0, 12)}`;
}

export function alphaNumericUnique(prefix: string): string {
  return uniqueName(prefix).replaceAll("-", "");
}

export const TINY_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);
