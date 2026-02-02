import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { sameKeysAndValues } from "../../Util";

describe("sameKeysAndValues", () => {
  it("Return true for keys in same order", () => {
    expect(sameKeysAndValues({ foo: 1, bar: 2 }, { foo: 1, bar: 2 })).toBe(
      true
    );
  });

  /*
   * This is likely to an unintuitive aspect of the behaviour of the function
   * if one doesn't look at the implementation
   */
  it("Returns false for keys in different order.", () => {
    expect(sameKeysAndValues({ bar: 2, foo: 1 }, { foo: 1, bar: 2 })).toBe(
      false
    );
  });
});


