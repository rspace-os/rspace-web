import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makeMockContainer } from "./mocking";
import { personAttrs } from "../PersonModel/mocking";

describe("adjustableTableOptions", () => {
  describe("Number of Empty Locations", () => {
    it("List containers should have unlimited empty locations.", () => {
      const container = makeMockContainer({
        cType: "LIST",
        owner: personAttrs(),
      });

      const optionValue = container
        .adjustableTableOptions()
        .get("Number of Empty Locations");
      expect(optionValue).not.toBeNull();
      const cellContent = optionValue!;

      expect(cellContent().data).toEqual("Unlimited");
    });

    it("Empty grid containers should render the number of locations.", () => {
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

      const optionValue = container
        .adjustableTableOptions()
        .get("Number of Empty Locations");
      expect(optionValue).not.toBeNull();
      const cellContent = optionValue!;

      expect(cellContent().data).toEqual("6");
    });

    it("If contentSummary is null, then nothing should be shown.", () => {
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

      const optionValue = container
        .adjustableTableOptions()
        .get("Number of Empty Locations");
      expect(optionValue).not.toBeNull();
      const cellContent = optionValue!;

      expect(cellContent().data).toBe(null);
    });
  });
});
