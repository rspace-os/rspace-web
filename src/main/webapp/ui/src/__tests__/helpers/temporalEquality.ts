import { expect } from "vitest";

const temporalTag = /^\[object Temporal\.\w+\]$/;

/**
 * Polyfill Temporal objects keep their state out of reach, so `toEqual` would treat any two of them as equal. Compare
 * them by type and ISO text instead.
 */
export function registerTemporalEquality(): void {
  expect.addEqualityTesters([
    function temporalEquals(a: unknown, b: unknown): boolean | undefined {
      const tagA = Object.prototype.toString.call(a);
      const tagB = Object.prototype.toString.call(b);
      if (!temporalTag.test(tagA) && !temporalTag.test(tagB)) return undefined;
      return tagA === tagB && String(a) === String(b);
    },
  ]);
}
