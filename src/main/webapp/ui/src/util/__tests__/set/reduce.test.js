//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { monoids } from "../helpers";
import RsSet from "../../set";
import { arbRsSet } from "./helpers";

describe("reduce", () => {
  test("Reducing an empty set, by a monoial operation, should give the monoid's identity element.", () => {
    fc.assert(
      fc.property(
        fc.oneof(...monoids),
        ([, booleanFunction, identityElement]) => {
          // $FlowExpectedError[underconstrained-implicit-instantiation] Might be a way of getting this to work, but not worth the effort
          expect(new RsSet().reduce(booleanFunction, identityElement)).toEqual(
            identityElement
          );
        }
      )
    );
  });
  test("Reducing a set of two elements, by a monoial operation, should give the same as applying the monoid's boolean function to the two elements.", () => {
    fc.assert(
      fc.property(
        fc
          .oneof(...monoids)
          .chain(([valueGenerator, booleanFunction, identityElement]) =>
            // $FlowExpectedError[incompatible-call] Might be a way of getting this to work, but not worth the effort
            fc.tuple(
              arbRsSet(valueGenerator(), { maxSize: 2, minSize: 2 }),
              fc.constant(booleanFunction),
              fc.constant(identityElement)
            )
          ),
        // $FlowExpectedError[incompatible-use] Might be a way of getting this to work, but not worth the effort
        ([set, booleanFunction, identityElement]) => {
          expect(set.reduce(booleanFunction, identityElement)).toEqual(
            booleanFunction(set.first, set.last)
          );
        }
      )
    );
  });
});
