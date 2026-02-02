import { test, describe, expect, afterEach, vi } from 'vitest';
import React from "react";
import {
  render,
} from "@testing-library/react";
import FormLabel from "@mui/material/FormLabel";
import FormControl from "../FormControl";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

vi.mock("@mui/material/FormLabel", () => ({
  default: vi.fn(() => <div></div>),
}));

describe("FormControl", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("Label correctly", () => {
    test("FormLabel is rendered when label is passed.", () => {
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
    test("FormLabel is not rendered when label is not passed.", () => {
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

