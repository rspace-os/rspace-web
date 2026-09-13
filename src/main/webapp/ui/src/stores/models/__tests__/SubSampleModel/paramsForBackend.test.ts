import { describe, expect, test, vi } from "vitest";
import { makeMockSubSample, makeMockSubSampleWithParentContainer } from "./mocking";

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
