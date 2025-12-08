/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import CloseIcon from "@mui/icons-material/Close";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import materialTheme from "../../theme";
import IconButtonWithTooltip from "../IconButtonWithTooltip";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("IconButtonWithTooltip", () => {
    test("Renders title and aria-label attributes.", () => {
        render(
            <ThemeProvider theme={materialTheme}>
                <IconButtonWithTooltip title="foo" icon={<CloseIcon />} />
            </ThemeProvider>,
        );

        screen.getByLabelText("foo");
    });
    test("onClick functions correctly.", async () => {
        const user = userEvent.setup();
        const onClick = jest.fn();

        render(
            <ThemeProvider theme={materialTheme}>
                <IconButtonWithTooltip title="foo" icon={<CloseIcon />} onClick={onClick} />
            </ThemeProvider>,
        );

        await user.click(screen.getByRole("button"));

        expect(onClick).toHaveBeenCalled();
    });
});
