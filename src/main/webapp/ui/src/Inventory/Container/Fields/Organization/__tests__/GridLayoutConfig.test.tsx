/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import { axe, toHaveNoViolations } from "jest-axe";
import { makeMockContainer } from "../../../../../stores/models/__tests__/ContainerModel/mocking";
import materialTheme from "../../../../../theme";
import GridLayoutConfig from "../GridLayoutConfig";

expect.extend(toHaveNoViolations);

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

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

        expect(await axe(container)).toHaveNoViolations();
    });
});
