/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { Optional } from "../../optional";

describe("orElseTry", () => {
  test("The types should be merged.", () => {
    const input: Optional<string> = Optional.present("foo");
    // the key bit of this test is that this type annotation doesn't error
    const next: Optional<string | number> = input.orElseTry(() =>
      Optional.present(4)
    );
    next.do((value) => expect(value).toBe("foo"));
  });
});
