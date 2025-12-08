/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import TypeFilter from "../TypeFilter";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("TypeFilter", () => {
    test("Current type should have aria-current property", () => {
        render(
            <ThemeProvider theme={materialTheme}>
                <TypeFilter current="ALL" onClose={() => {}} anchorEl={document.createElement("div")} />
            </ThemeProvider>,
        );

        expect(
            screen.getByRole("menuitem", {
                name: /All/,
            }),
        ).toHaveAttribute("aria-current", "true");
    });
});
