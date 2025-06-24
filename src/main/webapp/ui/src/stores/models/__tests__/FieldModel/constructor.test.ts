/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import FieldModel from "../../FieldModel";
import { makeMockSample } from "../SampleModel/mocking";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle

describe("constructor", () => {
  test("The content of number fields is parsed correctly.", () => {
    const field = new FieldModel(
      {
        attachment: null,
        columnIndex: 1,
        content: "2.03",
        definition: null,
        globalId: "SF19",
        id: 19,
        mandatory: false,
        name: "MyNumber",
        selectedOptions: null,
        type: "number",
      },
      makeMockSample()
    );

    expect(field.content).toBe(2.03);
  });
});
