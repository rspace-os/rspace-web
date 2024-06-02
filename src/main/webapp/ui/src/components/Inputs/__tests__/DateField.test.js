/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import NoValue from "../../../components/NoValue";

import DateField from "../DateField";

jest.mock("../../../components/NoValue", () => jest.fn(() => <></>));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("DateField", () => {
  describe("When `null` is passed as the `value` and field is disabled,", () => {
    test("'None' is rendered", () => {
      render(<DateField value={null} disabled onChange={() => {}} />);
      expect(NoValue).toHaveBeenCalledWith(
        {
          label: "None",
        },
        expect.anything()
      );
    });
  });

  describe("When passed an invalid date string,", () => {
    test("there is an error message shown.", () => {
      const { container } = render(
        <DateField value="2021-13-01" onChange={() => {}} />
      );
      expect(container).toHaveTextContent("Invalid date");
    });
  });
});
