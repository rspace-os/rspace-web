/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";
import NameDialog from "../NameDialog";

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("NameDialog", () => {
    test("Naming a new saved search the same name as an existing saved search should be an error.", () => {
        render(
            <ThemeProvider theme={materialTheme}>
                <NameDialog
                    open={true}
                    setOpen={() => {}}
                    name="foo"
                    setName={() => {}}
                    existingNames={["foo"]}
                    onChange={() => {}}
                />
            </ThemeProvider>,
        );

        expect(screen.getByText("This name is already taken. Please modify it.")).toBeVisible();
    });
});
