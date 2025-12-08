/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import { TYPE_LABEL } from "../../../../stores/definitions/Search";
import materialTheme from "../../../../theme";
import ToggleView from "../ToggleView";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("ToggleView", () => {
    test("Current view should have aria-current property", () => {
        render(
            <ThemeProvider theme={materialTheme}>
                <ToggleView
                    currentView={(Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>)[0]}
                    views={Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>}
                    onChange={() => Promise.resolve()}
                />
            </ThemeProvider>,
        );

        fireEvent.click(screen.getByRole("button", { name: "Change view" }));

        expect(
            screen.getByRole("menuitem", {
                name: TYPE_LABEL[(Object.keys(TYPE_LABEL) as Array<keyof typeof TYPE_LABEL>)[0]],
            }),
        ).toHaveAttribute("aria-current", "true");
    });
});
