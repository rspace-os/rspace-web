/*
 */
import { describe, test, expect, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makeMockSubSample } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  }}));
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})
}));

describe("permalinkURL", () => {
  test("When the subsample has not yet been saved, the permalinkURL should be null.", () => {
    const subsample = makeMockSubSample({ id: null, globalId: null });
    expect(subsample.permalinkURL).toBe(null);
  });
});


