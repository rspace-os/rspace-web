import { describe, expect, test } from "vitest";
import { isInvalidUri } from "@/Inventory/components/Fields/templateFieldValues";
import { cases } from "../../../../../../test/resources/inventory/uriFieldCases.json";

/*
 * The cases are shared with the Java InventoryUriFieldCasesTest, which runs
 * them through `InventoryUriField.validate` (`new java.net.URI(value)`).
 */
describe("isInvalidUri", () => {
  test.each(cases.filter(({ accepted }) => accepted).map(({ value }) => value))("accepts %j", (value) => {
    expect(isInvalidUri(value)).toBe(false);
  });

  test.each(cases.filter(({ accepted }) => !accepted).map(({ value }) => value))("rejects %j", (value) => {
    expect(isInvalidUri(value)).toBe(true);
  });
});
