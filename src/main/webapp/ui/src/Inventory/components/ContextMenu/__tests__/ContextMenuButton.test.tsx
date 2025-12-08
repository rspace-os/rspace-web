/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import fc from "fast-check";
import materialTheme from "../../../../theme";
import ContextMenuButton from "../ContextMenuButton";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("ContextMenuButton", () => {
    describe("Disabled state", () => {
        test("When disabled, should render aria-disabled.", () => {
            fc.assert(
                fc.property(fc.string(), (disabledHelp) => {
                    cleanup();
                    render(
                        <ThemeProvider theme={materialTheme}>
                            <ContextMenuButton disabledHelp={disabledHelp} label="Foo" />
                        </ThemeProvider>,
                    );
                    expect(screen.getByRole("button", { name: "Foo" })).toHaveAttribute(
                        "aria-disabled",
                        (disabledHelp !== "").toString(),
                    );
                }),
            );
        });
    });
});
