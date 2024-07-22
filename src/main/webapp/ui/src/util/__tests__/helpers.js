//@flow
/* eslint-env jest */

import fc, { type Arbitrary } from "fast-check";
import * as ArrayUtils from "../ArrayUtils";

/*
 * This file contains a bunch of generic helper code for writing Inventory jest
 * tests.
 */

/*
 * Useful for asserting that two distinct arrays contain the same elements in
 * the same order
 */
export const arrayOfSameElements = <T>(
  arrayA: Array<T>,
  arrayB: Array<T>
): boolean =>
  ArrayUtils.zipWith(arrayA, arrayB, (a, b) => a === b).every(
    (isSame) => isSame
  );

/*
 * Performs the combination of asserting that the value is not null for jest,
 * and performing type refinement for flow.
 */
export const assertNotNull = <T>(x: ?T): T => {
  // first we assert that x is not null for the purposes of the jest test
  expect(x).not.toBeNull();

  // but flow still thinks that x could be null, so we refine the type
  if (x !== null && typeof x !== "undefined") return x;

  // now we're left with a conditional branch that can never happen
  // have to throw as an implicit undefined return would cause flow to complain
  throw new Error("Impossible state");
};

/*
 * === Monoids ===
 *
 * These are useful for using fast-check to assert the behaviour of generic
 * algoriths that operate on data of a particular type and an associated binary
 * function that together form a monoid.
 *
 * Here, each exposes a fast-check tuple that contains a function that returns
 * an Arbitrary of the values that the monoid is defined over and a fast-check
 * constant that wraps an implementation of the associated binary function.
 * This way, the Arbitary can be used to generate random values that can then
 * be applied to the function.
 *
 * The test covering Util's zipWith and outerProduct are examples of how these
 * can be used.
 */
export type ArbitraryMonoid<T> = Arbitrary<
  [() => Arbitrary<T>, (T, T) => T, T]
>;

const addition: ArbitraryMonoid<number> = fc.tuple(
  fc.constant(() => fc.integer()),
  fc.constant((x: number, y: number) => x + y),
  fc.constant(0)
);
const multiplication: ArbitraryMonoid<number> = fc.tuple(
  // the range of these integers is constrained to avoid performance issues
  fc.constant(() => fc.integer({ min: -1000, max: 1000 })),
  fc.constant((x: number, y: number) => x * y),
  fc.constant(1)
);
const conjunction: ArbitraryMonoid<boolean> = fc.tuple(
  fc.constant(() => fc.boolean()),
  fc.constant((x: boolean, y: boolean) => x && y),
  fc.constant(true)
);
const disjunction: ArbitraryMonoid<boolean> = fc.tuple(
  fc.constant(() => fc.boolean()),
  fc.constant((x: boolean, y: boolean) => x || y),
  fc.constant(false)
);
const concatenation: ArbitraryMonoid<string> = fc.tuple(
  fc.constant(() => fc.string()),
  fc.constant((x: string, y: string) => `${x}${y}`),
  fc.constant("")
);
const maximum: ArbitraryMonoid<number> = fc.tuple(
  fc.constant(() => fc.float()),
  fc.constant((x: number, y: number) => Math.max(x, y)),
  fc.constant(-Infinity)
);
const minimum: ArbitraryMonoid<number> = fc.tuple(
  fc.constant(() => fc.float()),
  fc.constant((x: number, y: number) => Math.min(x, y)),
  fc.constant(Infinity)
);

export const monoids: Array<
  ArbitraryMonoid<number> | ArbitraryMonoid<boolean> | ArbitraryMonoid<string>
> = [
  addition,
  multiplication,
  conjunction,
  disjunction,
  concatenation,
  maximum,
  minimum,
];
