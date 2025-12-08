/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import each from "jest-each";
import materialTheme from "../../../theme";
import ScopeField, { type Scope } from "../ScopeField";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("ScopeField", () => {
    each(["Mine", "Public", "Both"]).test("getDMPs is called correctly when the scope is %s", (scope: string) => {
        cleanup();
        const getDMPs = jest.fn<[Scope], unknown[]>();

        render(
            <ThemeProvider theme={materialTheme}>
                <ScopeField getDMPs={getDMPs} />
            </ThemeProvider>,
        );

        fireEvent.click(screen.getByRole("radio", { name: scope }));
        expect(getDMPs).toHaveBeenCalledTimes(1);
        expect(getDMPs).toHaveBeenCalledWith(scope.toUpperCase());
    });
});
