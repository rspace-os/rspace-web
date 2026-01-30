/*
 */
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import getRootStore from "../../RootStore";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  get: (endpoint: string) => {
    if (endpoint === "baskets") {
      return Promise.resolve({
        data: [
          {
            id: 1,
            globalId: "BA1",
            name: "Basket #1",
            itemCount: 2,
            _links: [],
          },
        ],
      });
    }
    throw new Error(`Should not be calling endpoint: "${endpoint}".`);
  },

  }}));

describe("getBaskets", () => {
  it("Initialises basket objects correctly.", async () => {
    const { searchStore } = getRootStore();
    await searchStore.getBaskets();
    expect(searchStore.savedBaskets.length).toBe(1);
  });
});


