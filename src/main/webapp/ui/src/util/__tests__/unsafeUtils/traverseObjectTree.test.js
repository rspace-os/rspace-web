/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */

import { traverseObjectTree } from "../../unsafeUtils";

describe("traverseObjectTree", () => {
  test("basic example", () => {
    expect(
      traverseObjectTree(
        {
          foo: {
            bar: {
              baz: "test",
            },
          },
        },
        "foo.bar.baz",
        null
      )
    ).toBe("test");
  });
});
