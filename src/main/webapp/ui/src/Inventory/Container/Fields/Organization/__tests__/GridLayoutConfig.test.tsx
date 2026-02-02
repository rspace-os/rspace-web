import { describe, expect, vi, test } from 'vitest';
import React from "react";
import {
  render,
} from "@testing-library/react";
import GridLayoutConfig from "../GridLayoutConfig";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import { toBeAccessible } from "@sa11y/vitest";
expect.extend({ toBeAccessible });
describe("GridLayoutConfig", () => {
  test("Should have no axe violations.", async () => {
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
    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(container).toBeAccessible();
  });
});
});
