import "@/stores/stores/RootStore";
import { describe, expect, test, vi } from "vitest";
import FieldModel from "../../FieldModel";
import { makeMockSample } from "../SampleModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} })); // break import cycle
describe("hasContent", () => {
  describe('type = "Number"', () => {
    test.each`
      value | boolValue
      ${0}  | ${true}
      ${4}  | ${true}
      ${""} | ${false}
    `("{value = $value}", ({ value, boolValue }: { value: string | number; boolValue: boolean }) => {
      const field = new FieldModel(
        {
          type: "number",
          content: value.toString(),
          selectedOptions: [],
          columnIndex: null,
          attachment: null,
          mandatory: false,
        },
        makeMockSample(),
      );
      expect(field.hasContent).toBe(boolValue);
    });
  });

  describe('type = "link"', () => {
    test("a filestore item target counts as content", () => {
      const field = new FieldModel(
        {
          type: "link",
          content: null,
          selectedOptions: null,
          columnIndex: null,
          attachment: null,
          mandatory: true,
          link: {
            relationType: "IsSourceOf",
            targetGlobalId: null,
            versionPin: null,
            filestoreTarget: { filestoreId: 12, remotePath: "/XRD/run.dat" },
          },
        },
        makeMockSample(),
      );

      expect(field.hasContent).toBe(true);
      expect(field.link?.targetGlobalId).toBe("");
    });
  });
});
