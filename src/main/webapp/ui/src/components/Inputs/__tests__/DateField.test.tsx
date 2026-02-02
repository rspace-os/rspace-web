import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React from "react";
import {
  render,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import NoValue from "../../../components/NoValue";

import DateField from "../DateField";

vi.mock("../../../components/NoValue", () => ({
  default: vi.fn(() => <></>),
}));

beforeEach(() => {
  vi.clearAllMocks();
});


describe("DateField", () => {
  describe("When `null` is passed as the `value` and field is disabled,", () => {
    it("'None' is rendered", () => {
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
    it("there is an error message shown.", () => {
      const { container } = render(
        <DateField value="2021-13-01" onChange={() => {}} />
      );
      expect(container).toHaveTextContent("Invalid date");
    });
  });
});

