/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import InitialScreen from "../InitialScreen";
import Header from "../Header";
import Sidebar from "../Sidebar";
import { BrowserRouter } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../theme";

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
      </ThemeProvider>
    );

    expect(Header).toHaveBeenCalled();
    expect(Sidebar).toHaveBeenCalled();
    expect(screen.getByText("My Bench")).toBeInTheDocument();
    expect(screen.getByText("Containers")).toBeInTheDocument();
    expect(screen.getByText("Samples")).toBeInTheDocument();
  });
});
