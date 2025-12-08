/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { ThemeProvider } from "@mui/material/styles";
import { BrowserRouter } from "react-router-dom";
import materialTheme from "../../../../theme";
import Header from "../Header";
import InitialScreen from "../InitialScreen";
import Sidebar from "../Sidebar";

jest.mock("../Header", () => jest.fn(() => <></>));
jest.mock("../Sidebar", () => jest.fn(() => <></>));

beforeEach(() => {
    jest.clearAllMocks();
});

afterEach(cleanup);

describe("InitialScreen", () => {
    test("Renders correctly", () => {
        render(
            <ThemeProvider theme={materialTheme}>
                <BrowserRouter>
                    <InitialScreen />
                </BrowserRouter>
            </ThemeProvider>,
        );

        expect(Header).toHaveBeenCalled();
        expect(Sidebar).toHaveBeenCalled();
        expect(screen.getByText("My Bench")).toBeInTheDocument();
        expect(screen.getByText("Containers")).toBeInTheDocument();
        expect(screen.getByText("Samples")).toBeInTheDocument();
    });
});
