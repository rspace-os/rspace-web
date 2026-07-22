import { randomUUID } from "node:crypto";

export function uniqueName(prefix: string): string {
  return `${prefix}-${randomUUID().slice(0, 12)}`;
}

export function alphaNumericUnique(prefix: string): string {
  return uniqueName(prefix).replaceAll("-", "");
}
