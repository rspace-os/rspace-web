/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import ContextMenuSplitButton from "../ContextMenuSplitButton";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("ContextMenuSplitButton", () => {
    test("Current view should have aria-current property", () => {
        render(
            <ThemeProvider theme={materialTheme}>
                <ContextMenuSplitButton options={[{ text: "foo" }]} icon={null} />
            </ThemeProvider>,
        );

        // none of the options are selected until one has been used
        // so first we tap one of the options
        fireEvent.click(screen.getByRole("button", { name: "More selection options" }));
        fireEvent.click(screen.getByRole("menuitem", { name: "foo" }));

        // then we reopen the menu to assert the now selected option
        fireEvent.click(screen.getByRole("button", { name: "More selection options" }));
        expect(
            screen.getByRole("menuitem", {
                name: "foo",
            }),
        ).toHaveAttribute("aria-current", "true");
    });
});
