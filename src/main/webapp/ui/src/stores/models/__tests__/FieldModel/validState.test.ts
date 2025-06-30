/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import { cleanup } from "@testing-library/react";
import FieldModel from "../../FieldModel";
import { makeMockSample } from "../SampleModel/mocking";

jest.mock("../../../../common/InvApiService", () => {});

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("method: validate", () => {
  test("Mandatory choice fields should be allowed once they have a value.", () => {
    const field = new FieldModel(
      {
        name: "foo",
        type: "choice",
        selectedOptions: null,
        definition: {
          options: ["foo", "bar"],
        },
        columnIndex: null,
        attachment: null,
        mandatory: true,
      },
      makeMockSample()
    );

    expect(field.validate().isError).toBe(true);
    field.validate().do((errorMsg) => {
      expect(errorMsg).toEqual(
        `The mandatory custom field "foo" must have a valid value.`
      );
    });

    field.setAttributesDirty({
      selectedOptions: ["option1"],
    });

    expect(field.validate().isError).toBe(false);
  });
  test("Mandatory radio fields should be allowed once they have a value.", () => {
    const field = new FieldModel(
      {
        name: "foo",
        type: "radio",
        selectedOptions: null,
        definition: {
          options: ["foo", "bar"],
        },
        columnIndex: null,
        attachment: null,
        mandatory: true,
      },
      makeMockSample()
    );

    expect(field.validate().isError).toBe(true);
    field.validate().do((errorMsg) => {
      expect(errorMsg).toEqual(
        `The mandatory custom field "foo" must have a valid value.`
      );
    });

    field.setAttributesDirty({
      selectedOptions: "option1",
    });

    expect(field.validate().isError).toBe(false);
  });
});
