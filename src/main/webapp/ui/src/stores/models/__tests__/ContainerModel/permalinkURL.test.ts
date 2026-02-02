import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makeMockContainer } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({})
}));

describe("permalinkURL", () => {
  it("When the container has not yet been saved, the permalinkURL should be null.", () => {
    const container = makeMockContainer({ id: null, globalId: null });
    expect(container.permalinkURL).toBe(null);
  });
});


