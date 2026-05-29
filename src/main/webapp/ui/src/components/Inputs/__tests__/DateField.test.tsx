import { test, describe, expect, vi } from "vitest";
import React from "react";
import { configure, getConfig, render, screen } from "@testing-library/react";

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
        expect.anything(),
      );
    });
  });
  describe("When passed an invalid date string,", () => {
    test("there is an error message shown.", () => {
      const { container } = render(
        <DateField value="2021-13-01" onChange={() => {}} />,
      );
      expect(container).toHaveTextContent("Invalid date");
    });
  });
  describe("When passed a Date object,", () => {
    test("there is no error message shown.", () => {
      const { container } = render(
        <DateField value={new Date(2021, 0, 1)} onChange={() => {}} />,
      );

      expect(container).not.toHaveTextContent("Invalid date");
    });
  });
  describe("When passed a yyyy-MM-dd date string,", () => {
    test("there is no error message shown.", () => {
      const { container } = render(
        <DateField value="2021-01-01" onChange={() => {}} />,
      );

      expect(container).not.toHaveTextContent("Invalid date");
    });
  });
  describe("When passed an ISO date-time string,", () => {
    test("there is no error message shown.", () => {
      const { container } = render(
        <DateField value="2021-01-01T00:00:00Z" onChange={() => {}} />,
      );

      expect(container).not.toHaveTextContent("Invalid date");
    });
  });
  describe("When passed a non-ISO date string,", () => {
    test("there is an error message shown even if the string is parseable by the Date constructor.", () => {
      const { container } = render(
        <DateField
          value={new Date(2021, 0, 1).toString()}
          onChange={() => {}}
        />,
      );

      expect(container).toHaveTextContent("Invalid date");
    });
  });
  describe("When passed a placeholder and test id,", () => {
    test("they are forwarded to the rendered field.", () => {
      const { testIdAttribute } = getConfig();
      render(
        <DateField
          value={null}
          onChange={() => {}}
          placeholder="the beginning"
          data-test-id="from-date"
        />,
      );

      try {
        configure({ testIdAttribute: "data-test-id" });
        expect(screen.getByTestId("from-date")).toBeInTheDocument();
      } finally {
        configure({ testIdAttribute });
      }
      expect(screen.getByPlaceholderText("the beginning")).toBeInTheDocument();
    });
  });
});
