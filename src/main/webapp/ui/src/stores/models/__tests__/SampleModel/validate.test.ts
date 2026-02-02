import { describe, expect, test, vi } from 'vitest';
import { makeMockSample } from "./mocking";
vi.mock("../../../../common/InvApiService", () => ({ default: {} })); // break import cycle
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})
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
});

