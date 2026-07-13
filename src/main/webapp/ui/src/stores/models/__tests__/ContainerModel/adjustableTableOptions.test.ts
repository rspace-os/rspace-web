import { describe, expect, test } from "vitest";
import { personAttrs } from "../PersonModel/mocking";
import { makeMockContainer } from "./mocking";

describe("adjustableTableOptions", () => {
  describe("numberOfEmptyLocations", () => {
    test("List containers should have unlimited empty locations.", () => {
      const container = makeMockContainer({
        cType: "LIST",
        owner: personAttrs(),
      });

      const optionValue = container.adjustableTableOptions().get("numberOfEmptyLocations");
      expect(optionValue).not.toBeNull();
      // biome-ignore lint/style/noNonNullAssertion: initial biome migration
      const cellContent = optionValue!;
      expect(cellContent().data).toEqual("inventory:container.availableLocations.unlimited");
    });
    test("Empty grid containers should render the number of locations.", () => {
      const container = makeMockContainer({
        cType: "GRID",
        gridLayout: {
          columnsNumber: 2,
          rowsNumber: 3,
          columnsLabelType: "ABC",
          rowsLabelType: "ABC",
        },
        locationsCount: 6,
        owner: personAttrs(),
      });

      const optionValue = container.adjustableTableOptions().get("numberOfEmptyLocations");
      expect(optionValue).not.toBeNull();
      // biome-ignore lint/style/noNonNullAssertion: initial biome migration
      const cellContent = optionValue!;
      expect(cellContent().data).toEqual("6");
    });
    test("If contentSummary is null, then nothing should be shown.", () => {
      const container = makeMockContainer({
        cType: "GRID",
        gridLayout: {
          columnsNumber: 2,
          rowsNumber: 3,
          columnsLabelType: "ABC",
          rowsLabelType: "ABC",
        },
        locationsCount: 6,
        owner: personAttrs(),
        contentSummary: null,
      });

      const optionValue = container.adjustableTableOptions().get("numberOfEmptyLocations");
      expect(optionValue).not.toBeNull();
      // biome-ignore lint/style/noNonNullAssertion: initial biome migration
      const cellContent = optionValue!;
      expect(cellContent().data).toBe(null);
    });
  });
});
