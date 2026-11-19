import "@/stores/stores/RootStore";
import { describe, expect, test, vi } from "vitest";
import FieldModel, { type FieldModelAttrs } from "../../FieldModel";
import { makeMockSample } from "../SampleModel/mocking";
import { makeMockField } from "./mocking";

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

  describe("text, as the server's isNotBlank reads it", () => {
    test.each`
      content      | boolValue
      ${"SN-1"}    | ${true}
      ${""}        | ${false}
      ${"  \t\n "} | ${false}
      ${"\u3000"}  | ${false}
      ${"\u00a0"}  | ${true}
    `("{content = $content}", ({ content, boolValue }: { content: string; boolValue: boolean }) => {
      expect(makeMockField({ type: "string", content }).hasContent).toBe(boolValue);
    });
  });

  describe("link, as the server's InventoryLinkField reads it", () => {
    const filestoreTarget = { filestoreId: 3, remotePath: "/scans/lsm980.tif" };
    test.each`
      description                                | link                                                                                       | boolValue
      ${"a Global ID target"}                    | ${{ relationType: "References", targetGlobalId: "SA20", versionPin: null }}                | ${true}
      ${"a Filestore target"}                    | ${{ relationType: "References", targetGlobalId: null, versionPin: null, filestoreTarget }} | ${true}
      ${"a blank Global ID and no Filestore"}    | ${{ relationType: "References", targetGlobalId: " ", versionPin: null }}                   | ${false}
      ${"a Filestore target without a relation"} | ${{ relationType: " ", targetGlobalId: null, versionPin: null, filestoreTarget }}          | ${false}
      ${"no link"}                               | ${null}                                                                                    | ${false}
    `("$description", ({ link, boolValue }: { link: FieldModelAttrs["link"]; boolValue: boolean }) => {
      expect(makeMockField({ type: "link", link }).hasContent).toBe(boolValue);
    });
  });
});
