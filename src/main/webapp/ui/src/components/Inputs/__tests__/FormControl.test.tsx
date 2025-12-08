/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render } from "@testing-library/react";
import "@testing-library/jest-dom";
import FormLabel from "@mui/material/FormLabel";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";
import FormControl from "../FormControl";

jest.mock("@mui/material/FormLabel", () => jest.fn(() => <div></div>));

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("FormControl", () => {
    describe("Label correctly", () => {
        test("FormLabel is rendered when label is passed.", () => {
            render(
                <ThemeProvider theme={materialTheme}>
                    <FormControl label="foo">
                        <div></div>
                    </FormControl>
                </ThemeProvider>,
            );

            expect(FormLabel).toHaveBeenCalledWith(
                expect.objectContaining({
                    children: expect.objectContaining({
                        props: expect.objectContaining({
                            label: "foo",
                        }),
                    }),
                }),
                expect.anything(),
            );
        });
        test("FormLabel is not rendered when label is not passed.", () => {
            render(
                <ThemeProvider theme={materialTheme}>
                    <FormControl>
                        <div></div>
                    </FormControl>
                </ThemeProvider>,
            );

            expect(FormLabel).not.toHaveBeenCalled();
        });
    });
});
