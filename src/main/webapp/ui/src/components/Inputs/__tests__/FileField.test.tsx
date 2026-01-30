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
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";

import FileField from "../FileField";
import Alert from "@mui/material/Alert";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

vi.mock("@mui/material/Alert", () => ({
  default: vi.fn(() => {
    return <div data-testid="Alert"></div>;
  }),
}));

beforeEach(() => {
  vi.clearAllMocks();
});


describe("FileField", () => {
  /*
   * The warningAlert allows for custom warning meesages to be associated with
   * a file field, useful in reporting issues around invalid data or providing
   * general help.
   */
  describe("When passed a warningAlert there should", () => {
    it("be an Alert on the page.", () => {
      render(
        <ThemeProvider theme={materialTheme}>
          <FileField
            onChange={() => {}}
            name="foo"
            accept="foo"
            warningAlert="A warning alert."
          />
        </ThemeProvider>
      );
      expect(screen.getByTestId("Alert")).toBeInTheDocument();
      expect(Alert).toHaveBeenCalledWith(
        expect.objectContaining({
          children: "A warning alert.",
          severity: "warning",
        }),
        expect.anything()
      );
    });
  });
});

