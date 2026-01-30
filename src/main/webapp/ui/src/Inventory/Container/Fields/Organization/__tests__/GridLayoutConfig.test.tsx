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
import GridLayoutConfig from "../GridLayoutConfig";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import { axe } from "vitest-axe";
import { toHaveNoViolations } from "vitest-axe/matchers";

expect.extend({ toHaveNoViolations });

beforeEach(() => {
  vi.clearAllMocks();
});


describe("GridLayoutConfig", () => {
  it("Should have no axe violations.", async () => {
    const gridContainer = makeMockContainer({
      cType: "GRID",
      gridLayout: {
        columnsNumber: 1,
        rowsNumber: 1,
        columnsLabelType: "ABC",
        rowsLabelType: "ABC",
      },
    });

    const { container } = render(
      <ThemeProvider theme={materialTheme}>
        <GridLayoutConfig container={gridContainer} />
      </ThemeProvider>
    );

    expect(await axe(container)).toHaveNoViolations();
  });
});


