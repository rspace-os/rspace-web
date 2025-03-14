/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import * as ArrayUtils from "../../ArrayUtils";
import { monoids } from "../helpers";

/* eslint-disable @typescript-eslint/no-explicit-any */

describe("zipWith", () => {
  test("Has property of associativity over monoids.", () => {
    fc.assert(
      fc.property(
        fc
          // reasonable max length is necessary to not cause memory usage issues
          .tuple(fc.nat(10), fc.oneof(...monoids))
          .chain(([length, [valueGenerator, booleanFunction]]) => {
            return fc.tuple(
              fc.array(valueGenerator() as fc.Arbitrary<any>, {
                minLength: length,
                maxLength: length,
              }),
              fc.array(valueGenerator() as fc.Arbitrary<any>, {
                minLength: length,
                maxLength: length,
              }),
              fc.array(valueGenerator() as fc.Arbitrary<any>, {
                minLength: length,
                maxLength: length,
              }),
              fc.constant(booleanFunction as (a: any, b: any) => any)
            );
          }),
        <T>([as, bs, cs, booleanFunction]: [
          T[],
          T[],
          T[],
          (a: T, b: T) => T
        ]) => {
          expect(
            ArrayUtils.zipWith(
              as,
              ArrayUtils.zipWith(bs, cs, booleanFunction),
              booleanFunction
            )
          ).toEqual(
            ArrayUtils.zipWith(
              ArrayUtils.zipWith(as, bs, booleanFunction),
              cs,
              booleanFunction
            )
          );
        }
      )
    );
  });
});
