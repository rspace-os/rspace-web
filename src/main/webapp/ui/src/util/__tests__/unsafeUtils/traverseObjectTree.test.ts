
import { describe, expect, it } from "vitest";
import { traverseObjectTree } from "../../unsafeUtils";

describe("traverseObjectTree", () => {
  it("basic example", () => {
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


