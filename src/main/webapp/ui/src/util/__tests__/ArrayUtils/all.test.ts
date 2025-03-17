/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { Optional } from "../../optional";
import * as ArrayUtils from "../../ArrayUtils";

describe("all", () => {
  test("Zero element: Any Optional.empty will always result in Optional.empty.", () => {
    fc.assert(
      fc.property(
        fc
          .array(fc.option(fc.nat(), { nil: null }))
          .map((nullable: Array<number | null>) =>
            nullable.map((x) => Optional.fromNullable(x))
          ),
        (optionals) => {
          expect(
            ArrayUtils.all([...optionals, Optional.empty<number>()]).isEmpty()
          ).toBe(true);
        }
      )
    );
  });
});
