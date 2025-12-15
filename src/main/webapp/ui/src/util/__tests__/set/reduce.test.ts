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
        fc.oneof(...monoids) as any,
        ([, booleanFunction, identityElement]: any) => {
          const emptySet = new RsSet();
          expect(
            emptySet.reduce(
              (acc: any, elem: any) => booleanFunction(acc, elem),
              identityElement
            )
          ).toEqual(identityElement);
        }
      )
    );
  });
  test("Reducing a set of two elements, by a monoial operation, should give the same as applying the monoid's boolean function to the two elements.", () => {
    fc.assert(
      fc.property(
        (fc
          .oneof(...monoids)
          .chain(([valueGenerator, booleanFunction, identityElement]: any) =>
            fc.tuple(
              arbRsSet(valueGenerator(), { maxSize: 2, minSize: 2 }),
              fc.constant(booleanFunction),
              fc.constant(identityElement)
            )
          ) as any),
        ([set, booleanFunction, identityElement]: any) => {
          expect(
            set.reduce(
              (acc: any, elem: any) => booleanFunction(acc, elem),
              identityElement
            )
          ).toEqual(booleanFunction(set.first, set.last));
        }
      )
    );
  });
});