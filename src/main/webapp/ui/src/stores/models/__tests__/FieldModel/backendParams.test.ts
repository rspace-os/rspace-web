import { describe, expect, test, vi } from 'vitest';
import { makeMockField } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})
}));

describe("computed: paramsForBackend", () => {
  /*
   * `paramsForBackend` is used for submitting the SampleModel to the API and as
   * such must be JSON serialisable. Most significantly, this means not having
   * any cyclical memory references, which is verified by demonstrating that a
   * call to JSON.stringify does not throw any errors and returns a valid
   * string.
   */
  describe("paramsForBackend should be JSON serialisable when", () => {
    test("the field is a number field.", () => {
      const field = makeMockField({
        type: "number",
        content: "1",
      });
      expect(JSON.stringify(field.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });
    test("the field is a choice field.", () => {
      const field = makeMockField({
        type: "choice",
        definition: {
          options: ["foo", "bar"],
        },
        selectedOptions: ["foo"],
      });
      expect(JSON.stringify(field.paramsForBackend)).toEqual(
        expect.any(String)
      );
    });
  });
});


