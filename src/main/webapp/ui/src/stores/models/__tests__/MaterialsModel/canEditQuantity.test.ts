/*
 */
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Material, type Quantity } from "../../MaterialsModel";
import { makeMockContainer } from "../ContainerModel/mocking";
import { makeMockSample } from "../SampleModel/mocking";
import { makeMockSubSample } from "../SubSampleModel/mocking";
import fc, { type Arbitrary } from "fast-check";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})
}));

const arbitraryQuantity: Arbitrary<Quantity> = fc.record({
  unitId: fc.nat(),
  numericValue: fc.float(),
});

describe("canEditQuantity", () => {
  it("Should return false if invRec is a deleted SubSample", () => {
    fc.assert(
      fc.property(
        fc.option(arbitraryQuantity, { nil: null }),
        (usedQuantity) => {
          const material = new Material({
            invRec: makeMockSubSample({ deleted: true }),
            usedQuantity,
          });

          expect(material.canEditQuantity).toBe(false);
        }
      )
    );
  });
  it("Should return false if invRec is a Sample", () => {
    const material = new Material({
      invRec: makeMockSample(),
      usedQuantity: null,
    });

    expect(material.canEditQuantity).toBe(false);
  });
  it("Should return false if invRec is a Container", () => {
    const material = new Material({
      invRec: makeMockContainer(),
      usedQuantity: null,
    });

    expect(material.canEditQuantity).toBe(false);
  });
});


