import { describe, expect, test, vi } from "vitest";
import RsSet from "../../../../util/set";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import Search from "../../Search";
import { makeMockSample } from "../SampleModel/mocking";

vi.mock("../../../stores/getRootStore", () => ({
  default: () => ({
    searchStore: {
      activeResult: {
        globalId: "IT1",
      },
    },
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));
describe("isActiveResultTemplateOfAny", () => {
  test("Example where the return value is true.", () => {
    const samples = new RsSet([
      makeMockSample({
        id: 1,
        globalId: "SA1",
        templateId: 1,
      }),
      makeMockSample({
        id: 2,
        globalId: "SA2",
        templateId: 2,
      }),
    ]);
    const search = new Search({
      factory: mockFactory(),
    });
    expect(search.isActiveResultTemplateOfAny(samples)).toBe(true);
  });
  test("Example where the return value is false.", () => {
    const samples = new RsSet([
      makeMockSample({
        id: 1,
        globalId: "SA1",
        templateId: 2,
      }),
      makeMockSample({
        id: 2,
        globalId: "SA2",
        templateId: 2,
      }),
    ]);
    const search = new Search({
      factory: mockFactory(),
    });
    expect(search.isActiveResultTemplateOfAny(samples)).toBe(false);
  });
});
