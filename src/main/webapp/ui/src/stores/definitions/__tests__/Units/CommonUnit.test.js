/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import {
  toCommonUnit,
  massIds,
  fromCommonUnit,
  quantityIds,
} from "../../Units";
import fc from "fast-check";
import { values } from "../../../../util/Util";

// number of decimal places for floating point comparison
const PRECISION = 5;

describe("toCommonUnit", () => {
  test("1g = 1000mg", () => {
    expect(toCommonUnit(1, massIds.grams)).toEqual(
      toCommonUnit(1000, massIds.milligrams)
    );
  });

  test("Multiplication distributes over toCommonUnit", () => {
    fc.assert(
      fc.property(
        fc.tuple(fc.integer(), fc.constantFrom(...values(quantityIds))),
        ([x, unit]) => {
          expect(toCommonUnit(x, unit) * 2).toEqual(toCommonUnit(x * 2, unit));
        }
      )
    );
  });
});

describe("fromCommonUnit", () => {
  test("1 quadrillion picograms = 1g", () => {
    expect(fromCommonUnit(10 ** 12, massIds.grams)).toEqual(1);
  });
});

describe("CommonUnit mutual test", () => {
  test("forall x, unit . fromCommonUnit(toCommonUnit(x, unit), unit) = x", () => {
    fc.assert(
      fc.property(
        fc.tuple(fc.integer(), fc.constantFrom(...values(quantityIds))),
        ([x, unit]) => {
          expect(fromCommonUnit(toCommonUnit(x, unit), unit)).toBeCloseTo(
            x,
            PRECISION
          );
        }
      )
    );
  });
});
