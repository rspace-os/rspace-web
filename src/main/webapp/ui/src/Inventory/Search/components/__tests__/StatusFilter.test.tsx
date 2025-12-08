/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import StatusFilter from "../StatusFilter";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("StatusFilter", () => {
    test("Current status should have aria-current property", () => {
        render(
            <ThemeProvider theme={materialTheme}>
                <StatusFilter current="EXCLUDE" onClose={() => {}} anchorEl={document.createElement("div")} />
            </ThemeProvider>,
        );

        expect(
            screen.getByRole("menuitem", {
                name: "Current",
            }),
        ).toHaveAttribute("aria-current", "true");
    });
});
