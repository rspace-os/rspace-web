import { describe, expect, test, vi } from 'vitest';
import "@testing-library/jest-dom/vitest";
import { makeMockSample } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})
}));

describe("permalinkURL", () => {
  test("When the sample has not yet been saved, the permalinkURL should be null.", () => {
    const sample = makeMockSample({ id: null, globalId: null });
    expect(sample.permalinkURL).toBe(null);
  });
});


