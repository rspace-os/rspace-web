import { describe, expect, test } from "vitest";
import { flattenMessages } from "../flattenMessages";

describe("flattenMessages", () => {
  test("turns nested message objects into legacy dotted keys", () => {
    expect(
      flattenMessages({
        legacyjs: {
          status: { generic: "Status: {0}" },
          cancel: "Cancel",
        },
      }),
    ).toEqual({
      "legacyjs.status.generic": "Status: {0}",
      "legacyjs.cancel": "Cancel",
    });
  });

  test("rejects non-string message leaves", () => {
    expect(() => flattenMessages({ legacyjs: { invalid: 1 } })).toThrow(
      "Message at 'legacyjs.invalid' must be a string or object",
    );
  });
});
