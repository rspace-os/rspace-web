/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import FieldModel from "../../FieldModel";
import each from "jest-each";
import { makeMockSample } from "../SampleModel/mocking";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle

describe("hasContent", () => {
  describe('type = "Number"', () => {
    each`
    value | boolValue
    ${0}  | ${true}
    ${4}  | ${true}
    ${""} | ${false}
  `.test(
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
