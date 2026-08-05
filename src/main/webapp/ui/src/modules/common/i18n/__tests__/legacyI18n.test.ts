import { beforeAll, describe, expect, test } from "vitest";
import legacyMessages from "../locales/en-US/server.legacyJs.json";

// The entry exports nothing, so import it for its side effects and read the same `window.RS`
// surface the legacy scripts use. The Vite plugin prepends FormatJS here as it does in the browser.
let formatIcuMessage: (pattern: string, args: Array<string | number>) => string;

beforeAll(async () => {
  await import("../legacyI18n");
  const rs = (window as Window & { RS?: { formatIcuMessage?: typeof formatIcuMessage } }).RS;
  if (!rs?.formatIcuMessage) {
    throw new Error("legacyI18n did not install RS.formatIcuMessage");
  }
  formatIcuMessage = rs.formatIcuMessage;
});

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

  test("formats complete sharing failure variants", () => {
    const message = legacyMessages.legacyjs.core.share.failure;

    expect(formatIcuMessage(message, ["sharing", "partial", 2, 1, "Not permitted"])).toBe(
      "Sharing was partially unsuccessful, 2 documents were skipped because of the following error:<br/>- Not permitted",
    );
    expect(formatIcuMessage(message, ["publication", "full", 1, 0, ""])).toBe(
      "Publication was unsuccessful. Maybe the document is already published?",
    );
  });
});
