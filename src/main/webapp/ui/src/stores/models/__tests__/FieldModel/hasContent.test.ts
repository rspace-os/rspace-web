/*
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import FieldModel from "../../FieldModel";
import { makeMockSample } from "../SampleModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} })); // break import cycle

describe("hasContent", () => {
  describe('type = "Number"', () => {
    it.each`
    value | boolValue
    ${0}  | ${true}
    ${4}  | ${true}
    ${""} | ${false}
  `(
      "{value = $value}",
      ({
        value,
        boolValue,
      }: {
        value: string | number;
        boolValue: boolean;
      }) => {
        const field = new FieldModel(
          {
            type: "number",
            content: value.toString(),
            selectedOptions: [],
            columnIndex: null,
            attachment: null,
            mandatory: false,
          },
          makeMockSample()
        );
        expect(field.hasContent).toBe(boolValue);
      }
    );
  });
});


