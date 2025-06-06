/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import Result from "../../result";

describe("orElseTry", () => {
  test("The types should be merged.", () => {
    const input: Result<string> = Result.Ok("foo");
    // the key bit of this test is that this type annotation doesn't error
    const next: Result<string | number> = input.orElseTry(() => Result.Ok(4));
    next.do((value) => expect(value).toBe("foo"));
  });
});
