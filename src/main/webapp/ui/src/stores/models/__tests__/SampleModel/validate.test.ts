import { describe, expect, test, vi } from "vitest";
import { makeMockSample } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} })); // break import cycle
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));
describe("method: validate", () => {
  describe("Asserts expiry date.", () => {
    test("Returns false when expiry date is an invalid date.", () => {
      const sample = makeMockSample({
        expiryDate: "2021-13-01",
      });
      expect(sample.validate().isOk).toBe(false);
    });
  });
  describe("Asserts description length.", () => {
    test("Returns true when the description is 2000 characters.", () => {
      const sample = makeMockSample({ description: "x".repeat(2000) });
      sample.currentlyEditableFields.add("description");
      expect(sample.validate().isOk).toBe(true);
    });
    test("Returns false when the description is 2001 characters.", () => {
      const sample = makeMockSample({ description: "x".repeat(2001) });
      sample.currentlyEditableFields.add("description");
      expect(sample.validate().isOk).toBe(false);
    });
  });
});
