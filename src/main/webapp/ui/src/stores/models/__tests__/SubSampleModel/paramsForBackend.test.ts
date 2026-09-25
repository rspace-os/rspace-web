import { describe, expect, test, vi } from "vitest";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import { makeMockSubSample, makeMockSubSampleWithParentContainer, subsampleAttrs } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    unitStore: {
      getUnit: () => ({ label: "ml" }),
    },
  }),
}));
describe("computed: paramsForBackend", () => {
  /*
   * Not throwing on JSON.stringify demonstrates there are no cyclical references.
   */
  describe("paramsForBackend should be JSON serialisable when", () => {
    test("the subSample is on the bench.", () => {
      const subSample = makeMockSubSample();
      expect(JSON.stringify(subSample.paramsForBackend)).toEqual(expect.any(String));
    });
    test("the subSample has a parent container.", () => {
      const subSample = makeMockSubSampleWithParentContainer();
      expect(JSON.stringify(subSample.paramsForBackend)).toEqual(expect.any(String));
    });
  });

  /*
   * Quantity is stock, not a label. Every other editable field is echoed back on save and the last
   * write wins, which is the accepted norm (DevDocs/adr/0011). Echoing a quantity the user never
   * touched is not: an operation that deducted from this subsample between the page load and the
   * save is undone by the save, restoring stock that material was already made from. The server
   * cannot tell that payload apart from a user deliberately setting the same number, so the client
   * does not send it.
   */
  describe("quantity", () => {
    const editing = () => {
      const subSample = makeMockSubSample();
      subSample.setEditable(new Set(["name", "quantity"]), true);
      return subSample;
    };

    test("is omitted from an edit that did not touch it.", () => {
      expect(editing().paramsForBackend).not.toHaveProperty("quantity");
    });

    test("is sent when the user edited it.", () => {
      const subSample = editing();
      subSample.setFieldsDirty({ quantity: { numericValue: 4, unitId: 3 } });
      expect(subSample.paramsForBackend).toHaveProperty("quantity", {
        numericValue: 4,
        unitId: 3,
      });
    });

    test("is not left outstanding when a mid-edit refetch resets the baseline.", () => {
      // populateFromJson clears quantityEdited, so quantity must be assigned from the incoming
      // params in the same call - otherwise the cleared flag would not correspond to a new
      // baseline.
      const subSample = editing();
      subSample.setFieldsDirty({ quantity: { numericValue: 4, unitId: 3 } });

      subSample.populateFromJson(new AlwaysNewFactory(), {
        ...subsampleAttrs({ quantity: { numericValue: 9, unitId: 3 } }),
        sample: subSample.sample,
      });

      expect(subSample.quantity).toEqual({ numericValue: 9, unitId: 3 });
    });

    test("is sent on a create even though the user never touched it.", () => {
      // A record with no id yet is being created, so its quantity is part of what is being created
      // rather than an echo of something stored.
      const subSample = makeMockSubSample({ id: null });
      subSample.setEditable(new Set(["name", "quantity"]), true);

      expect(subSample.paramsForBackend).toHaveProperty("quantity", {
        numericValue: 1,
        unitId: 3,
      });
    });

    test("is sent when the user retyped the value it already had.", () => {
      const subSample = editing();
      subSample.setFieldsDirty({ quantity: { numericValue: 1, unitId: 3 } });
      expect(subSample.paramsForBackend).toHaveProperty("quantity", {
        numericValue: 1,
        unitId: 3,
      });
    });
  });
});
