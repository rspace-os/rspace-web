import fc from "fast-check";
import { describe, expect, test } from "vitest";
import RsSet from "../../set";
import { monoids } from "../helpers";

import { arbRsSet } from "./helpers";

describe("reduce", () => {
  test("Reducing an empty set, by a monoial operation, should give the monoid's identity element.", () => {
    fc.assert(
      fc.property(
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        fc.oneof(...monoids) as any,
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        ([, booleanFunction, identityElement]: any) => {
          const emptySet = new RsSet();
          expect(
            emptySet.reduce(
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              (acc: any, elem: any) => booleanFunction(acc, elem),
              identityElement,
            ),
          ).toEqual(identityElement);
        },
      ),
    );
  });
  test("Reducing a set of two elements, by a monoial operation, should give the same as applying the monoid's boolean function to the two elements.", () => {
    fc.assert(
      fc.property(
        fc.oneof(...monoids).chain(
          // biome-ignore lint/suspicious/noExplicitAny: heterogeneous monoid tuples from fc.oneof are not statically typed here
          ([valueGenerator, booleanFunction, identityElement]: any) =>
            fc.tuple(
              arbRsSet(valueGenerator(), { maxSize: 2, minSize: 2 }),
              fc.constant(booleanFunction),
              fc.constant(identityElement),
            ),
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        ) as any,
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        ([set, booleanFunction, identityElement]: any) => {
          expect(
            set.reduce(
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              (acc: any, elem: any) => booleanFunction(acc, elem),
              identityElement,
            ),
          ).toEqual(booleanFunction(set.first, set.last));
        },
      ),
    );
  });
});
