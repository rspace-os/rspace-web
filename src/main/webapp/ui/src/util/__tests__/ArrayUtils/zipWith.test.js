/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import * as ArrayUtils from "../../ArrayUtils";
import { monoids } from "../helpers";

describe("zipWith", () => {
  test("Has property of associativity over monoids.", () => {
    fc.assert(
      fc.property(
        fc
          // reasonable max length is necessary to not cause memory usage issues
          // $FlowExpectedError[incompatible-call] Might be a way of getting this to work, but not worth the effort
          .tuple(fc.nat(10), fc.oneof(...monoids))
          .chain(([length, [valueGenerator, booleanFunction]]) =>
            // $FlowExpectedError[incompatible-call] Might be a way of getting this to work, but not worth the effort
            fc.tuple(
              fc.array(valueGenerator(), {
                minLength: length,
                maxLength: length,
              }),
              fc.array(valueGenerator(), {
                minLength: length,
                maxLength: length,
              }),
              fc.array(valueGenerator(), {
                minLength: length,
                maxLength: length,
              }),
              fc.constant(booleanFunction)
            )
          ),
        // $FlowExpectedError[incompatible-use] Might be a way of getting this to work, but not worth the effort
        ([as, bs, cs, booleanFunction]) => {
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
