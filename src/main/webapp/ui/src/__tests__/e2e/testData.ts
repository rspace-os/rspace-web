import { randomUUID } from "node:crypto";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// Resolves a fixture file relative to the calling spec.
export function fixturePath(importMetaUrl: string, ...segments: string[]): string {
  return resolve(dirname(fileURLToPath(importMetaUrl)), ...segments);
}

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
