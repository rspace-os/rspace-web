import { test, describe, expect, vi } from 'vitest';
import React from "react";
import { render } from "@testing-library/react";

import NoValue from "../../../components/NoValue";
import DateField from "../DateField";

vi.mock("../../../components/NoValue", () => ({
  default: vi.fn(() => <></>),
}));
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

