import { describe, expect, test } from "vitest";
import i18n from "@/modules/common/i18n";

const KEY = "inventory:search.results.selectResultItem";

describe("missing ICU values in Browser Mode", () => {
  test("the shared singleton throws when a placeholder has no value", () => {
    expect(() => i18n.t(KEY)).toThrow(/"name" was not provided/);
    expect(i18n.t(KEY, { name: "Ada" })).toBe("Select Ada");
  });
});
