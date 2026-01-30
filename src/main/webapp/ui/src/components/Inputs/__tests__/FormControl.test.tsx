/*
 */
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
import FormLabel from "@mui/material/FormLabel";
import FormControl from "../FormControl";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

vi.mock("@mui/material/FormLabel", () => ({
  default: vi.fn(() => <div></div>),
}));

beforeEach(() => {
  vi.clearAllMocks();
});


describe("FormControl", () => {
  describe("Label correctly", () => {
    it("FormLabel is rendered when label is passed.", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <FormControl label="foo">
            <div></div>
          </FormControl>
        </ThemeProvider>,
      );

      expect(FormLabel).toHaveBeenCalledWith(
        expect.objectContaining({
          children: expect.objectContaining({
            props: expect.objectContaining({
              label: "foo",
            }),
          }),
        }),
        expect.anything(),
      );
    });
    it("FormLabel is not rendered when label is not passed.", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <FormControl>
            <div></div>
          </FormControl>
        </ThemeProvider>,
      );

      expect(FormLabel).not.toHaveBeenCalled();
    });
  });
});

