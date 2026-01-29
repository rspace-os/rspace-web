/*
 * @vitest-environment jsdom
 */

import { describe, test, expect } from "vitest";
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


