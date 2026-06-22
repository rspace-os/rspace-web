import { ThemeProvider } from "@mui/material/styles";
import { render } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import materialTheme from "../../../../../theme";
import GridLayoutConfig from "../GridLayoutConfig";

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
      </ThemeProvider>,
    );

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(container).toBeAccessible();
  });
});
