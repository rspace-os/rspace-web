/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import FieldModel from "../../FieldModel";
import { makeMockSample } from "../SampleModel/mocking";

jest.mock("../../../../common/InvApiService", () => {});

describe("method: validState", () => {
  test("Error flag should be asserted.", () => {
    const field = new FieldModel(
      {
        attachment: null,
        columnIndex: 1,
        content: "2",
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
    field.setError(true);

    expect(field.validate().isOk).toBe(false);
  });
});
