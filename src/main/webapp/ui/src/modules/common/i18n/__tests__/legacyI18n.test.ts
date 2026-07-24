import { describe, expect, test } from "vitest";
import { formatIcuMessage, selectLegacyCatalogue } from "../legacyI18n";
import legacyMessages from "../locales/en-US/server.legacyJs.json";

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

  test("selects the catalogue matching the deployment locale", () => {
    const catalogues = {
      "en-US": { greeting: "Hello" },
      "fr-FR": { greeting: "Bonjour" },
    };

    expect(selectLegacyCatalogue(catalogues, "fr-FR")).toEqual({ greeting: "Bonjour" });
    expect(selectLegacyCatalogue(catalogues, "de-DE")).toEqual({ greeting: "Hello" });
  });
});
