import { describe, expect, test, vi } from 'vitest';
import "@testing-library/jest-dom/vitest";
import { makeMockTemplate } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({})
}));

describe("permalinkURL", () => {
  test("When the template has not yet been saved, the permalinkURL should be null.", () => {
    const template = makeMockTemplate({ id: null, globalId: null });
    expect(template.permalinkURL).toBe(null);
  });
});


