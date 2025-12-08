/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import materialTheme from "../../theme";
import RemoveButton from "../RemoveButton";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("RemoveButton", () => {
    test("Should invoke onClick when clicked.", async () => {
        const user = userEvent.setup();
        const onClick = jest.fn();
        render(
            <ThemeProvider theme={materialTheme}>
                <RemoveButton onClick={onClick} />
            </ThemeProvider>,
        );

        await user.click(screen.getByRole("button"));

        expect(onClick).toHaveBeenCalled();
    });

    test('Has default tooltip "Delete"', () => {
        render(
            <ThemeProvider theme={materialTheme}>
                <RemoveButton />
            </ThemeProvider>,
        );

        screen.getByLabelText("Delete");
    });
});
