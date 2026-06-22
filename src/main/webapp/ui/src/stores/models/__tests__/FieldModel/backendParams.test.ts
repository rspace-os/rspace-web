import { describe, expect, test, vi } from "vitest";
import { makeMockField } from "./mocking";

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
      expect(JSON.stringify(field.paramsForBackend)).toEqual(expect.any(String));
    });
    test("the field is a choice field.", () => {
      const field = makeMockField({
        type: "choice",
        definition: {
          options: ["foo", "bar"],
        },
        selectedOptions: ["foo"],
      });
      expect(JSON.stringify(field.paramsForBackend)).toEqual(expect.any(String));
    });
  });

  describe("link fields", () => {
    test("carry the link payload and relation whitelist but no content", () => {
      const field = makeMockField({
        type: "link",
        allowedRelationTypes: ["References"],
        link: {
          relationType: "References",
          targetGlobalId: "SA2",
          versionPin: null,
        },
      });

      const params = field.paramsForBackend as Record<string, unknown>;
      expect(params.link).toEqual({
        relationType: "References",
        targetGlobalId: "SA2",
        versionPin: null,
      });
      expect(params.allowedRelationTypes).toEqual(["References"]);
      // a link field's value lives in its link, not the data column; sending
      // (empty) content trips the backend's mandatory-field content check
      expect(params).not.toHaveProperty("content");
    });
  });
});
