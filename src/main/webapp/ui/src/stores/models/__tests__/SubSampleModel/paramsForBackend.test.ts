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
   * `paramsForBackend` is used for submitting the SubSampleModel to the API and
   * as such must be JSON serialisable. Most significantly, this means not
   * having any cyclical memory references, which is verified by demonstrating
   * that a call to JSON.stringify does not throw any errors and returns a
   * valid string.
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
   * write wins, which is the accepted norm (DevDocs/adr/0007). Echoing a quantity the user never
   * touched is not: an operation that deducted from this subsample between the page load and the
   * save is undone by the save, restoring stock that material was already made from. The server
   * cannot tell that payload apart from a user deliberately setting the same number, so the client
   * does not send it (Codex review, PR #1090).
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
      // Creating or deleting an IGSN calls fetchAdditionalInfo unconditionally, which calls
      // populateFromJson on the live instance. That clears quantityEdited, so the save omits the
      // quantity - but nothing refreshed this.quantity, so the field went on showing the user's
      // number, the server returned 200, the success alert fired and nothing was written. The
      // quantity is now assigned from the incoming params like every other field on the model, so
      // clearing the flag really does correspond to a new baseline (review 2026-09-14, C3).
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
      // rather than an echo of something stored. Every other case here uses the fixture's id of 1,
      // so without this a simplification to quantityEdited alone would create subsamples holding
      // nothing (review 2026-09-14, C3).
      const subSample = makeMockSubSample({ id: null });
      subSample.setEditable(new Set(["name", "quantity"]), true);

      expect(subSample.paramsForBackend).toHaveProperty("quantity", {
        numericValue: 1,
        unitId: 3,
      });
    });

    test("is sent when the user retyped the value it already had.", () => {
      // Deliberate: the user typed a number, so it is theirs to set. Only an untouched field is
      // left out.
      const subSample = editing();
      subSample.setFieldsDirty({ quantity: { numericValue: 1, unitId: 3 } });
      expect(subSample.paramsForBackend).toHaveProperty("quantity", {
        numericValue: 1,
        unitId: 3,
      });
    });
  });
});
