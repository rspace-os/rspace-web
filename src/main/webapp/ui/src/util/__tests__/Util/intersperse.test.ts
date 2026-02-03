import { describe, expect, test } from 'vitest';

import * as ArrayUtils from "../../ArrayUtils";
describe("intersperse", () => {
  test("Simple example", () => {
    expect(ArrayUtils.intersperse(", ", ["foo", "bar"])).toEqual([
      "foo",
      ", ",
      "bar",
    ]);
  });
});

