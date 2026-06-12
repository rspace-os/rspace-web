import fc from "fast-check";
import { describe, expect, test } from "vitest";
// biome-ignore lint/style/useImportType: initial biome migration
import RsSet from "../../set";
import { arbRsSet, arbSubsetOf } from "./helpers";

describe("isSame", () => {
  test("Symmetric", () => {
    fc.assert(
      fc.property(
        arbRsSet(fc.anything()).chain((set) => fc.tuple(fc.constant(set), arbSubsetOf(set))),
        ([setA, setB]: [RsSet<unknown>, RsSet<unknown>]) => {
          expect(setA.isSame(setB)).toEqual(setB.isSame(setA));
        },
      ),
    );
  });
});
