import { describe, expect, test } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";
import appI18n from "@/modules/common/i18n";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";

const KEY = "inventory:search.results.selectResultItem";
const MISSING_NAME = /cannot format "search\.results\.selectResultItem".*"name" was not provided/;

describe("missing ICU values", () => {
  test("an isolated test instance throws when a placeholder has no value", async () => {
    const i18n = await createTestI18n({ inventory: inventoryEn }, "inventory");
    expect(() => i18n.t(KEY)).toThrow(MISSING_NAME);
    expect(i18n.t(KEY, { name: "Ada" })).toBe("Select Ada");
  });

  describe("the shared singleton in English", () => {
    setupRealAppI18n();

    test("throws when a placeholder has no value", () => {
      expect(() => appI18n.t(KEY)).toThrow(MISSING_NAME);
      expect(appI18n.t(KEY, { name: "Ada" })).toBe("Select Ada");
    });
  });
});
