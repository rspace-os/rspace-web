import { describe, expect, test } from "vitest";
import { formatIcuMessage, formatLegacyList, selectLegacyCatalogue } from "../legacyI18n";

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

  test("formats natural-language lists", () => {
    expect(formatLegacyList(["Ada", "Grace", "Linus"], "en-US")).toBe("Ada, Grace, and Linus");
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
