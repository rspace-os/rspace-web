import { randomUUID } from "node:crypto";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// Resolves a fixture file relative to the calling spec.
export function fixturePath(importMetaUrl: string, ...segments: string[]): string {
  return resolve(dirname(fileURLToPath(importMetaUrl)), ...segments);
}

export const DYNAMIC_USER_PASSWORD = "Passw0rd!23";

export function uniqueName(prefix: string): string {
  return `${prefix}-${randomUUID().slice(0, 12)}`;
}

/** Hands out unique names and remembers them, so teardown removes only what the test created. */
export function trackedUniqueNames(prefix: string): { next: () => string; created: ReadonlySet<string> } {
  const created = new Set<string>();
  return {
    next: () => {
      const name = uniqueName(prefix);
      created.add(name);
      return name;
    },
    created,
  };
}

export function alphaNumericUnique(prefix: string): string {
  return uniqueName(prefix).replaceAll("-", "");
}

export const TINY_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);
