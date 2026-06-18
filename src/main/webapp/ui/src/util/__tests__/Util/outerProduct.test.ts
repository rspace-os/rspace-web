import fc from "fast-check";
import { describe, expect, test } from "vitest";
import * as ArrayUtils from "../../ArrayUtils";

import { monoids } from "../helpers";

/* eslint-disable @typescript-eslint/no-explicit-any */
describe("outerProduct", () => {
  test("When composed with .flat(), it has property of associativity over monoids.", () => {
    fc.assert(
      fc.property(
        fc
          // reasonable max length is necessary to not cause memory usage issues
          .tuple(fc.nat(10), fc.oneof(...monoids))
          .chain(([length, [valueGenerator, booleanFunction]]) =>
            fc.tuple(
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              fc.array(valueGenerator() as fc.Arbitrary<any>, {
                minLength: length,
                maxLength: length,
              }),
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              fc.array(valueGenerator() as fc.Arbitrary<any>, {
                minLength: length,
                maxLength: length,
              }),
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              fc.array(valueGenerator() as fc.Arbitrary<any>, {
                minLength: length,
                maxLength: length,
              }),
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              fc.constant(booleanFunction as (a: any, b: any) => any),
            ),
          ),
        <T>([as, bs, cs, booleanFunction]: [T[], T[], T[], (a: T, b: T) => T]) => {
          expect(
            ArrayUtils.outerProduct(
              as,
              ArrayUtils.outerProduct(bs, cs, booleanFunction).flat(),
              booleanFunction,
            ).flat(),
          ).toEqual(
            ArrayUtils.outerProduct(
              ArrayUtils.outerProduct(as, bs, booleanFunction).flat(),
              cs,
              booleanFunction,
            ).flat(),
          );
        },
      ),
    );
  });
});
