import { describe, expect, test } from "vitest";
import { formatIcuMessage } from "../legacyI18n";

describe("legacy ICU messages", () => {
  test("formats positional arguments", () => {
    expect(formatIcuMessage("Hello {0}", ["Ada"])).toBe("Hello Ada");
  });

  test("formats plurals", () => {
    const message = "{0, plural, =0 {No files} one {# file} other {# files}}";
    expect(formatIcuMessage(message, [2])).toBe("2 files");
  });

  test("preserves literal HTML", () => {
    expect(formatIcuMessage('<a href="{0}">Open</a>', ["/workspace"])).toBe('<a href="/workspace">Open</a>');
  });
});
